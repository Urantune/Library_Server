package customer.identityservice.auth.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import customer.identityservice.auth.dto.AuthDto;
import customer.identityservice.auth.service.AuthService;
import customer.identityservice.user.dto.UserDto.Envelope;
import jakarta.servlet.http.HttpServletRequest;

/**
 * API đăng nhập tạm thời để frontend chạy end-to-end.
 *
 * <p>Khi Trọng hoàn thiện module auth chính thức, có thể thay thế package
 * {@code auth} này mà không ảnh hưởng phần user.</p>
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService service;

    public AuthController(AuthService service) {
        this.service = service;
    }

    @PostMapping("/login")
    public Envelope<AuthDto.Session> login(@RequestBody AuthDto.LoginInput input,
            HttpServletRequest request) {
        return Envelope.success("Đăng nhập thành công", service.login(input), traceId(request));
    }

    @PostMapping("/register")
    public ResponseEntity<Envelope<AuthDto.UserView>> register(@RequestBody AuthDto.RegisterInput input,
            HttpServletRequest request) {
        AuthDto.UserView user = service.register(input);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Envelope.success("Đăng ký thành công", user, traceId(request)));
    }

    @PostMapping("/refresh")
    public Envelope<AuthDto.Tokens> refresh(@RequestBody AuthDto.RefreshInput input,
            HttpServletRequest request) {
        return Envelope.success("Đã làm mới phiên đăng nhập", service.refresh(input), traceId(request));
    }

    @PostMapping("/logout")
    public Envelope<Void> logout(@RequestBody(required = false) AuthDto.RefreshInput input,
            HttpServletRequest request) {
        service.logout(input);
        return Envelope.success("Đã đăng xuất", null, traceId(request));
    }

    private String traceId(HttpServletRequest request) {
        String trace = request.getHeader("X-Trace-Id");
        return trace == null || trace.isBlank() ? UUID.randomUUID().toString() : trace;
    }
}
