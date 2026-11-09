package customer.identityservice.auth.dto;

import java.util.List;

/** DTO dùng riêng cho luồng đăng nhập/đăng ký/refresh token. */
public final class AuthDto {
    private AuthDto() {}

    public record LoginInput(String email, String password) {}

    public record RegisterInput(String fullName, String email, String password) {}

    public record RefreshInput(String refreshToken) {}

    public record UserView(String id, String fullName, String email,
                           List<String> roles, String status) {}

    public record Session(String accessToken, String refreshToken, UserView user) {}

    public record Tokens(String accessToken, String refreshToken) {}
}
