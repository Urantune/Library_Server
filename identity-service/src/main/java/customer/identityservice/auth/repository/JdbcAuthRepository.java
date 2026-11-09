package customer.identityservice.auth.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import customer.identityservice.auth.entity.AuthUser;
import customer.identityservice.auth.entity.RefreshTokenRow;

/** Triển khai AuthRepository trên SQLite bằng JdbcTemplate. */
@Repository
public class JdbcAuthRepository implements AuthRepository {
    private final JdbcTemplate jdbc;

    public JdbcAuthRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<AuthUser> findByEmail(String email) {
        return jdbc.query(
                "SELECT id, email, full_name, status, password_hash FROM users WHERE lower(email)=lower(?)",
                (rs, rowNum) -> new AuthUser(rs.getString("id"), rs.getString("email"),
                        rs.getString("full_name"), rs.getString("status"), rs.getString("password_hash")),
                email).stream().findFirst();
    }

    @Override
    public Optional<AuthUser> findById(String id) {
        return jdbc.query(
                "SELECT id, email, full_name, status, password_hash FROM users WHERE id=?",
                (rs, rowNum) -> new AuthUser(rs.getString("id"), rs.getString("email"),
                        rs.getString("full_name"), rs.getString("status"), rs.getString("password_hash")),
                id).stream().findFirst();
    }

    @Override
    public List<String> findRoles(String userId) {
        return jdbc.queryForList(
                "SELECT r.code FROM user_roles ur JOIN roles r ON r.id=ur.role_id WHERE ur.user_id=? ORDER BY r.code",
                String.class, userId);
    }

    @Override
    public void touchLastLogin(String userId, Instant at) {
        jdbc.update("UPDATE users SET last_login_at=? WHERE id=?", at.toString(), userId);
    }

    @Override
    public void saveRefreshToken(String id, String userId, String tokenHash,
                                 Instant expiresAt, Instant createdAt) {
        jdbc.update("INSERT INTO refresh_tokens(id,user_id,token_hash,expires_at,created_at) VALUES(?,?,?,?,?)",
                id, userId, tokenHash, expiresAt.toString(), createdAt.toString());
    }

    @Override
    public Optional<RefreshTokenRow> findRefreshToken(String tokenHash) {
        return jdbc.query(
                "SELECT id, user_id, token_hash, expires_at, revoked_at FROM refresh_tokens WHERE token_hash=?",
                (rs, rowNum) -> new RefreshTokenRow(rs.getString("id"), rs.getString("user_id"),
                        rs.getString("token_hash"), Instant.parse(rs.getString("expires_at")),
                        rs.getString("revoked_at") == null ? null : Instant.parse(rs.getString("revoked_at"))),
                tokenHash).stream().findFirst();
    }

    @Override
    public void revokeRefreshToken(String id) {
        jdbc.update("UPDATE refresh_tokens SET revoked_at=? WHERE id=? AND revoked_at IS NULL",
                Instant.now().toString(), id);
    }
}
