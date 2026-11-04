package customer.identityservice.user;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UserService {
    private static final Set<String> ROLES = Set.of("MEMBER", "LIBRARIAN", "ADMIN");
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private final UserRepository users;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public UserService(UserRepository users) { this.users = users; }

    public UserDto.UserList search(String query, int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page/size không hợp lệ");
        String term = query == null ? "" : query.trim();
        long total = users.count(term);
        return new UserDto.UserList(users.findAll(term, page * size, size).stream().map(UserDto::from).toList(), page, size, total, (int) Math.ceil((double) total / size));
    }

    public UserDto get(String id) { return UserDto.from(requireUser(id)); }

    @Transactional
    public UserDto updateProfile(String id, String fullName, String phone) {
        requireUser(id);
        if (fullName == null || fullName.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Họ tên là bắt buộc");
        return UserDto.from(users.updateProfile(id, fullName.trim(), phone));
    }

    @Transactional
    public UserDto create(UserDto.Input input, String actor, String traceId) {
        validate(input, true, null);
        UserRow created = users.create(normalize(input), passwordEncoder.encode(input.password()));
        users.audit(actor, "USER_CREATED", created.id(), "SUCCESS", traceId);
        return UserDto.from(created);
    }

    @Transactional
    public UserDto update(String id, UserDto.Input input, String actor, String traceId) {
        requireUser(id);
        validate(input, false, id);
        UserRow updated = users.update(id, normalize(input));
        users.audit(actor, "USER_UPDATED", id, "SUCCESS", traceId);
        return UserDto.from(updated);
    }

    @Transactional
    public void setStatus(String id, String status, String actor, String traceId) {
        requireUser(id);
        if (!Set.of("ACTIVE", "LOCKED", "INACTIVE").contains(status)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Trạng thái không hợp lệ");
        users.updateStatus(id, status);
        users.audit(actor, "USER_STATUS_" + status, id, "SUCCESS", traceId);
    }

    @Transactional
    public void delete(String id, String actor, String traceId) {
        requireUser(id);
        users.audit(actor, "USER_DELETED", id, "SUCCESS", traceId);
        users.delete(id);
    }

    private UserRow requireUser(String id) {
        return users.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy người dùng"));
    }

    private void validate(UserDto.Input input, boolean creating, String id) {
        if (input == null || blank(input.username()) || blank(input.email()) || blank(input.fullName())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "username, email và fullName là bắt buộc");
        if (!EMAIL.matcher(input.email().trim()).matches()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email không hợp lệ");
        if (input.username().trim().length() < 3) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "username cần ít nhất 3 ký tự");
        String role = normalizeRole(input.role());
        if (!ROLES.contains(role)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vai trò không hợp lệ");
        if (users.existsByEmail(input.email().trim(), id) || users.existsByUsername(input.username().trim(), id)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Email hoặc username đã tồn tại");
        if (creating && (blank(input.password()) || input.password().length() < 8)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mật khẩu cần ít nhất 8 ký tự");
    }

    private UserDto.Input normalize(UserDto.Input input) {
        return new UserDto.Input(input.username().trim(), input.email().trim().toLowerCase(Locale.ROOT), input.fullName().trim(), input.phone(), normalizeRole(input.role()), input.password());
    }

    private String normalizeRole(String role) {
        if ("USER".equalsIgnoreCase(role)) return "MEMBER";
        if ("STAFF".equalsIgnoreCase(role)) return "LIBRARIAN";
        return role == null ? "MEMBER" : role.toUpperCase(Locale.ROOT);
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }
}
