package customer.identityservice.common;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/**
 * Trả lỗi dạng {status, message, timestamp} để frontend hiển thị đúng nội dung
 * thay vì chỉ thấy "Bad Request"/"Not Found".
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handle(ResponseStatusException exception) {
        HttpStatus status = HttpStatus.resolve(exception.getStatusCode().value());
        String reason = exception.getReason();
        if (reason == null || reason.isBlank()) {
            reason = status == null ? "Lỗi" : status.getReasonPhrase();
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "error");
        body.put("message", reason);
        body.put("timestamp", Instant.now());
        return ResponseEntity.status(exception.getStatusCode()).body(body);
    }
}
