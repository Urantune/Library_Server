package customer.identityservice.audit;

import java.time.Instant;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Triển khai AuditRepository trên bảng admin_audit_logs. */
@Repository
public class JdbcAuditRepository implements AuditRepository {
    private final JdbcTemplate jdbc;

    public JdbcAuditRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void record(String actorId, String action, String targetId, String outcome, String traceId) {
        jdbc.update("INSERT INTO admin_audit_logs(id,actor_user_id,action,target_user_id,outcome,trace_id,created_at) VALUES(?,?,?,?,?,?,?)",
                UUID.randomUUID().toString(), actorId, action, targetId, outcome, traceId, Instant.now().toString());
    }
}
