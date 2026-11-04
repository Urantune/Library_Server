package customer.identityservice.user;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepository {
    private final JdbcTemplate jdbc;
    private final RowMapper<UserRow> mapper = this::mapRow;

    public UserRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<UserRow> findAll(String query, int offset, int size) {
        String match = "%" + query.toLowerCase() + "%";
        return jdbc.query("SELECT u.id, u.username, u.email, u.full_name, u.phone, u.status, u.created_at, u.updated_at, COALESCE((SELECT r.code FROM user_roles ur JOIN roles r ON r.id=ur.role_id WHERE ur.user_id=u.id LIMIT 1),'MEMBER') role FROM users u WHERE lower(COALESCE(u.username,'')) LIKE ? OR lower(u.email) LIKE ? OR lower(u.full_name) LIKE ? ORDER BY u.created_at DESC LIMIT ? OFFSET ?",
                mapper, match, match, match, size, offset);
    }

    public long count(String query) {
        String match = "%" + query.toLowerCase() + "%";
        return jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE lower(COALESCE(username,'')) LIKE ? OR lower(email) LIKE ? OR lower(full_name) LIKE ?", Long.class, match, match, match);
    }

    public Optional<UserRow> findById(String id) {
        return jdbc.query("SELECT u.id, u.username, u.email, u.full_name, u.phone, u.status, u.created_at, u.updated_at, COALESCE((SELECT r.code FROM user_roles ur JOIN roles r ON r.id=ur.role_id WHERE ur.user_id=u.id LIMIT 1),'MEMBER') role FROM users u WHERE u.id=?", mapper, id).stream().findFirst();
    }

    public boolean existsByEmail(String email, String exceptId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM users WHERE lower(email)=lower(?) AND id<>?)", Boolean.class, email, exceptId == null ? "" : exceptId));
    }

    public boolean existsByUsername(String username, String exceptId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM users WHERE lower(username)=lower(?) AND id<>?)", Boolean.class, username, exceptId == null ? "" : exceptId));
    }

    public UserRow create(UserDto.Input input, String encodedPassword) {
        String id = UUID.randomUUID().toString();
        String now = Instant.now().toString();
        jdbc.update("INSERT INTO users(id,username,email,password_hash,full_name,phone,status,created_at,updated_at) VALUES(?,?,?,?,?,?, 'ACTIVE',?,?)", id, input.username(), input.email(), encodedPassword, input.fullName(), input.phone(), now, now);
        assignRole(id, input.role());
        return findById(id).orElseThrow();
    }

    public UserRow update(String id, UserDto.Input input) {
        jdbc.update("UPDATE users SET username=?, email=?, full_name=?, phone=?, updated_at=? WHERE id=?", input.username(), input.email(), input.fullName(), input.phone(), Instant.now().toString(), id);
        jdbc.update("DELETE FROM user_roles WHERE user_id=?", id);
        assignRole(id, input.role());
        return findById(id).orElseThrow();
    }

    public UserRow updateProfile(String id, String fullName, String phone) {
        jdbc.update("UPDATE users SET full_name=?, phone=?, updated_at=? WHERE id=?", fullName, phone, Instant.now().toString(), id);
        return findById(id).orElseThrow();
    }

    public void updateStatus(String id, String status) {
        jdbc.update("UPDATE users SET status=?, updated_at=? WHERE id=?", status, Instant.now().toString(), id);
        if ("LOCKED".equals(status)) jdbc.update("UPDATE refresh_tokens SET revoked_at=? WHERE user_id=? AND revoked_at IS NULL", Instant.now().toString(), id);
    }

    public int delete(String id) { return jdbc.update("UPDATE users SET status='INACTIVE', updated_at=? WHERE id=?", Instant.now().toString(), id); }

    public void audit(String actorId, String action, String targetId, String outcome, String traceId) {
        jdbc.update("INSERT INTO admin_audit_logs(id,actor_user_id,action,target_user_id,outcome,trace_id,created_at) VALUES(?,?,?,?,?,?,?)", UUID.randomUUID().toString(), actorId, action, targetId, outcome, traceId, Instant.now().toString());
    }

    private void assignRole(String userId, String role) {
        Integer roleId = jdbc.queryForObject("SELECT id FROM roles WHERE code=?", Integer.class, role);
        jdbc.update("INSERT INTO user_roles(user_id,role_id,assigned_at) VALUES(?,?,?)", userId, roleId, Instant.now().toString());
    }

    private UserRow mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new UserRow(rs.getString("id"), rs.getString("username"), rs.getString("email"), rs.getString("full_name"), rs.getString("phone"), rs.getString("role"), rs.getString("status"), Instant.parse(rs.getString("created_at")), Instant.parse(rs.getString("updated_at")));
    }
}
