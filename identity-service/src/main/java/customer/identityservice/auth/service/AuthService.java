package customer.identityservice.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import customer.identityservice.auth.dto.AuthDto;
import customer.identityservice.auth.entity.AuthUser;
import customer.identityservice.auth.entity.RefreshTokenRow;
import customer.identityservice.auth.repository.AuthRepository;
import customer.identityservice.user.dto.UserDto;
import customer.identityservice.user.repository.UserRepository;
import customer.identityservice.user.service.UserService;

/**
 * Luồng xác thực tạm thời: đăng nhập, đăng ký, refresh và logout.
 *
 * <p>Dùng đúng bảng users/roles/refresh_tokens đã có trong Flyway, mật khẩu
 * kiểm tra bằng BCrypt giống UserService.</p>
 */
@Service
public class AuthService {
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AuthRepository auth;
    private final UserRepository users;
    private final UserService userService;
    private final JwtService jwt;
    private final long refreshTtlDays;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthService(AuthRepository auth, UserRepository users, UserService userService,
            JwtService jwt,
            @Value("${security.jwt.refresh-ttl-days:30}") long refreshTtlDays) {
        this.auth = auth;
        this.users = users;
        this.userService = userService;
        this.jwt = jwt;
        this.refreshTtlDays = refreshTtlDays;
    }

    @Transactional
    public AuthDto.Session login(AuthDto.LoginInput input) {
        if (input == null || blank(input.email()) || blank(input.password())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email và mật khẩu là bắt buộc");
        }
        AuthUser user = auth.findByEmail(input.email().trim())
                .orElseThrow(AuthService::invalidCredentials);
        if (!passwordEncoder.matches(input.password(), user.passwordHash())) {
            throw invalidCredentials();
        }
        if (!"ACTIVE".equalsIgnoreCase(user.status())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Tài khoản đang bị khóa hoặc ngừng hoạt động");
        }
        auth.touchLastLogin(user.id(), Instant.now());
        return issueSession(user);
    }

    @Transactional
    public AuthDto.UserView register(AuthDto.RegisterInput input) {
        if (input == null || blank(input.email()) || blank(input.password()) || blank(input.fullName())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "fullName, email và password là bắt buộc");
        }
        String email = input.email().trim().toLowerCase(Locale.ROOT);
        if (!EMAIL.matcher(email).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email không hợp lệ");
        }
        if (input.password().length() < 8) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mật khẩu cần ít nhất 8 ký tự");
        }
        String username = uniqueUsername(email);
        UserDto created = userService.create(
                new UserDto.Input(username, email, input.fullName().trim(), null, "MEMBER", input.password()),
                "self-register", UUID.randomUUID().toString());
        return new AuthDto.UserView(created.id(), created.fullName(), created.email(),
                List.of(created.role()), created.status());
    }

    @Transactional
    public AuthDto.Tokens refresh(AuthDto.RefreshInput input) {
        if (input == null || blank(input.refreshToken())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "refreshToken là bắt buộc");
        }
        RefreshTokenRow stored = auth.findRefreshToken(sha256(input.refreshToken()))
                .orElseThrow(AuthService::invalidRefreshToken);
        if (stored.revokedAt() != null || !stored.expiresAt().isAfter(Instant.now())) {
            throw invalidRefreshToken();
        }
        AuthUser user = auth.findById(stored.userId()).orElseThrow(AuthService::invalidRefreshToken);
        if (!"ACTIVE".equalsIgnoreCase(user.status())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Tài khoản đang bị khóa hoặc ngừng hoạt động");
        }
        auth.revokeRefreshToken(stored.id());
        List<String> roles = rolesOf(user.id());
        String accessToken = jwt.issue(user.id(), roles);
        String refreshToken = newRefreshToken(user.id());
        return new AuthDto.Tokens(accessToken, refreshToken);
    }

    @Transactional
    public void logout(AuthDto.RefreshInput input) {
        if (input == null || blank(input.refreshToken())) return;
        auth.findRefreshToken(sha256(input.refreshToken()))
                .ifPresent(token -> auth.revokeRefreshToken(token.id()));
    }

    private AuthDto.Session issueSession(AuthUser user) {
        List<String> roles = rolesOf(user.id());
        String accessToken = jwt.issue(user.id(), roles);
        String refreshToken = newRefreshToken(user.id());
        return new AuthDto.Session(accessToken, refreshToken,
                new AuthDto.UserView(user.id(), user.fullName(), user.email(), roles, user.status()));
    }

    private String newRefreshToken(String userId) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant now = Instant.now();
        auth.saveRefreshToken(UUID.randomUUID().toString(), userId, sha256(token),
                now.plusSeconds(refreshTtlDays * 24 * 60 * 60), now);
        return token;
    }

    private List<String> rolesOf(String userId) {
        List<String> roles = auth.findRoles(userId);
        return roles.isEmpty() ? List.of("MEMBER") : roles;
    }

    private String uniqueUsername(String email) {
        String local = email.substring(0, email.indexOf('@'));
        String base = local.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "");
        if (base.length() < 3) base = "user" + base;
        if (base.length() > 24) base = base.substring(0, 24);
        String candidate = base;
        int suffix = 0;
        while (users.existsByUsername(candidate, null)) {
            suffix++;
            String tail = String.valueOf(suffix);
            int keep = Math.min(base.length(), 24 - tail.length());
            candidate = base.substring(0, keep) + tail;
        }
        return candidate;
    }

    private static ResponseStatusException invalidCredentials() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Email hoặc mật khẩu không đúng");
    }

    private static ResponseStatusException invalidRefreshToken() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                "Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại");
    }

    private static String sha256(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte item : hash) builder.append(String.format("%02x", item));
            return builder.toString();
        } catch (Exception exception) {
            throw new IllegalStateException("Không băm được refresh token", exception);
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
