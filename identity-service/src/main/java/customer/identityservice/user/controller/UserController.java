package customer.identityservice.user.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import customer.identityservice.user.dto.UserDto;
import customer.identityservice.user.dto.UserDto.Envelope;
import customer.identityservice.user.dto.UserDto.Input;
import customer.identityservice.user.dto.UserDto.StatusInput;
import customer.identityservice.user.service.UserService;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserService service;
    public UserController(UserService service) { this.service = service; }

    @GetMapping
    public Envelope<UserDto.UserList> list(@RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {
        return Envelope.success("Danh sách người dùng", service.search(q, page, size), traceId(request));
    }

    @GetMapping("/{id}")
    public Envelope<UserDto> get(@PathVariable String id, HttpServletRequest request) {
        return Envelope.success("Chi tiết người dùng", service.get(id), traceId(request));
    }

    @GetMapping("/me")
    public Envelope<UserDto> me(@RequestHeader("X-User-Id") String userId, HttpServletRequest request) {
        return Envelope.success("Hồ sơ cá nhân", service.get(userId), traceId(request));
    }

    @PutMapping("/me")
    public Envelope<UserDto> updateMe(@RequestHeader("X-User-Id") String userId,
            @RequestBody ProfileInput input, HttpServletRequest request) {
        return Envelope.success("Đã cập nhật hồ sơ", service.updateProfile(userId, input.fullName(), input.phone()), traceId(request));
    }

    public record ProfileInput(String fullName, String phone) {}

    @PostMapping
    public ResponseEntity<Envelope<UserDto>> create(@RequestBody Input input,
            @RequestHeader("X-User-Id") String actor, HttpServletRequest request) {
        UserDto result = service.create(input, actor, traceId(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(Envelope.success("Đã tạo người dùng", result, traceId(request)));
    }

    @PutMapping("/{id}")
    public Envelope<UserDto> update(@PathVariable String id, @RequestBody Input input,
            @RequestHeader("X-User-Id") String actor, HttpServletRequest request) {
        return Envelope.success("Đã cập nhật người dùng", service.update(id, input, actor, traceId(request)), traceId(request));
    }

    @PatchMapping("/{id}/status")
    public Envelope<Void> status(@PathVariable String id, @RequestBody StatusInput input,
            @RequestHeader("X-User-Id") String actor, HttpServletRequest request) {
        service.setStatus(id, input.status(), actor, traceId(request));
        return Envelope.success("Đã cập nhật trạng thái", null, traceId(request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id,
            @RequestHeader("X-User-Id") String actor, HttpServletRequest request) {
        service.delete(id, actor, traceId(request));
        return ResponseEntity.noContent().build();
    }

    private String traceId(HttpServletRequest request) {
        String trace = request.getHeader("X-Trace-Id");
        return trace == null || trace.isBlank() ? UUID.randomUUID().toString() : trace;
    }
}
