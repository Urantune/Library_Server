package customer.identityservice.user;

import java.time.Instant;
import java.util.List;

public record UserDto(String id, String username, String email, String fullName,
                      String phone, String role, String status,
                      Instant createdAt, Instant updatedAt) {
    public static UserDto from(UserRow row) {
        return new UserDto(row.id(), row.username(), row.email(), row.fullName(),
                row.phone(), row.role(), row.status(), row.createdAt(), row.updatedAt());
    }

    public record Input(String username, String email, String fullName,
                        String phone, String role, String password) {}
    public record StatusInput(String status) {}
    public record Envelope<T>(String status, String message, T data, Instant timestamp, String traceId) {
        public static <T> Envelope<T> success(String message, T data, String traceId) {
            return new Envelope<>("success", message, data, Instant.now(), traceId);
        }
    }
    public record UserList(List<UserDto> items, int page, int size, long totalElements, int totalPages) {}
}
