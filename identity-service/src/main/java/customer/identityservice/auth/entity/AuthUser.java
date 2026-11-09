package customer.identityservice.auth.entity;

/** Thông tin tối thiểu cần cho việc xác thực người dùng. */
public record AuthUser(String id, String email, String fullName,
                       String status, String passwordHash) {}
