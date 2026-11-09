package customer.identityservice.auth.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import customer.identityservice.auth.entity.AuthUser;
import customer.identityservice.auth.entity.RefreshTokenRow;

/** Cổng truy cập dữ liệu phục vụ đăng nhập và refresh token. */
public interface AuthRepository {

    Optional<AuthUser> findByEmail(String email);

    Optional<AuthUser> findById(String id);

    List<String> findRoles(String userId);

    void touchLastLogin(String userId, Instant at);

    void saveRefreshToken(String id, String userId, String tokenHash,
                          Instant expiresAt, Instant createdAt);

    Optional<RefreshTokenRow> findRefreshToken(String tokenHash);

    void revokeRefreshToken(String id);
}
