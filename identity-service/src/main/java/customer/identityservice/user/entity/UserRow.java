package customer.identityservice.user.entity;

import java.time.Instant;

public record UserRow(String id, String username, String email, String fullName,
                      String phone, String role, String status,
                      Instant createdAt, Instant updatedAt) {}
