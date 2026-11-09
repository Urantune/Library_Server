package customer.identityservice.audit;

/** Cổng ghi nhật ký thao tác quản trị. */
public interface AuditRepository {

    void record(String actorId, String action, String targetId, String outcome, String traceId);
}
