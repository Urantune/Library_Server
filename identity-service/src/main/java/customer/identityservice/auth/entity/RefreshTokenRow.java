package customer.identityservice.auth.entity;

import java.time.Instant;

/** Một bản ghi refresh token đã lưu trong DB. */
public record RefreshTokenRow(String id, String userId, String tokenHash,
                              Instant expiresAt, Instant revokedAt) {}
