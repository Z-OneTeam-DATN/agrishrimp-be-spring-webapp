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

    // 2. Xử lý lỗi xung đột / trùng lặp (409) - Đã gộp chung để FE dễ bắt
    @ExceptionHandler({ConflictException.class, DataIntegrityViolationException.class})
    public ResponseEntity<Map<String, String>> handleConflictException(Exception ex) {
        String message = (ex instanceof ConflictException)
                ? ex.getMessage()
                : "Tên danh mục này đã tồn tại trong hệ thống!";
        return buildResponse(message, HttpStatus.CONFLICT);
    }

    // 3. Xử lý lỗi Validation (400)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        String errorMessage = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return buildResponse("Dữ liệu không hợp lệ: " + errorMessage, HttpStatus.BAD_REQUEST);
    }

    // 4. Xử lý lỗi JSON sai định dạng (400)
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleReadableException(HttpMessageNotReadableException ex) {
        return buildResponse("Yêu cầu không hợp lệ: Dữ liệu gửi lên sai định dạng.", HttpStatus.BAD_REQUEST);
    }

    // 5. Xử lý lỗi yêu cầu không hợp lệ chung (400)
    @ExceptionHandler({BadRequestException.class, IllegalArgumentException.class})
    public ResponseEntity<Map<String, String>> handleBadRequestException(Exception ex) {
        return buildResponse(ex.getMessage(), HttpStatus.BAD_REQUEST);
    }

    // 6. CATCH-ALL: Lỗi hệ thống (500)
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGeneralException(Exception ex) {
        ex.printStackTrace(); // Giữ lại log để debug
        return buildResponse("Lỗi hệ thống: " + ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private ResponseEntity<Map<String, String>> buildResponse(String message, HttpStatus status) {
        Map<String, String> errorResponse = new HashMap<>();
        errorResponse.put("message", message);
        return ResponseEntity.status(status).body(errorResponse);
    }
}