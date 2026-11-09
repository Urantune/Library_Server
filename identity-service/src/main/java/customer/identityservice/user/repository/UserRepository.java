package customer.identityservice.user.repository;

import java.util.List;
import java.util.Optional;

import customer.identityservice.user.dto.UserDto.Input;
import customer.identityservice.user.entity.UserRow;

/** Cổng truy cập dữ liệu người dùng, tách khỏi công nghệ lưu trữ cụ thể. */
public interface UserRepository {

    List<UserRow> findAll(String query, int offset, int size);

    long count(String query);

    Optional<UserRow> findById(String id);

    boolean existsByEmail(String email, String exceptId);

    boolean existsByUsername(String username, String exceptId);

    UserRow create(Input input, String encodedPassword);

    UserRow update(String id, Input input);

    UserRow updateProfile(String id, String fullName, String phone);

    void updateStatus(String id, String status);

    int delete(String id);
}
