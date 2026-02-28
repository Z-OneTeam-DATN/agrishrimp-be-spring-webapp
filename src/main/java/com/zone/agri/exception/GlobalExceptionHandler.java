package com.zone.agri.exception;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // 1. Xử lý lỗi không tìm thấy (404)
    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFoundException(NotFoundException ex) {
        return buildResponse(ex.getMessage(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        return buildResponse("Dữ liệu đã tồn tại hoặc vi phạm ràng buộc hệ thống. Vui lòng kiểm tra lại (có thể do trùng tên)!", HttpStatus.CONFLICT);
    }

    // 2. Xử lý lỗi xung đột (409) - Rất quan trọng để báo lỗi "Trùng tên danh mục"
    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<Map<String, String>> handleConflictException(ConflictException ex) {
        return buildResponse(ex.getMessage(), HttpStatus.CONFLICT);
    }

    // 3. Xử lý lỗi Validation (400) - Khi dùng @Valid ở Controller
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        String errorMessage = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return buildResponse("Dữ liệu không hợp lệ: " + errorMessage, HttpStatus.BAD_REQUEST);
    }

    // 4. Xử lý lỗi JSON sai định dạng hoặc thừa trường (400)
    // Đặc biệt hữu ích khi bạn đã xóa cột 'description' nhưng Frontend vẫn gửi lên
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleReadableException(HttpMessageNotReadableException ex) {
        return buildResponse("Yêu cầu không hợp lệ: JSON sai định dạng hoặc chứa trường không tồn tại.", HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgumentException(IllegalArgumentException ex) {
        Map<String, String> errorResponse = new HashMap<>();
        // Đẩy message lỗi về Frontend
        errorResponse.put("message", ex.getMessage());
        // QUAN TRỌNG: Trả về 400 BAD_REQUEST, KHÔNG trả về 500
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    // 6. CATCH-ALL: Bắt mọi lỗi 500 chưa được định nghĩa
    // Giúp Frontend hiển thị được nội dung lỗi thay vì thông báo "Có lỗi xảy ra" mặc định
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGeneralException(Exception ex) {
        // Log chi tiết lỗi ra console của Backend để bạn dễ debug
        ex.printStackTrace();
        return buildResponse("Lỗi hệ thống: " + ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // Hàm helper giúp tạo phản hồi JSON đồng nhất với key "message"
    private ResponseEntity<Map<String, String>> buildResponse(String message, HttpStatus status) {
        Map<String, String> errorResponse = new HashMap<>();
        errorResponse.put("message", message);
        return ResponseEntity.status(status).body(errorResponse);
    }
}