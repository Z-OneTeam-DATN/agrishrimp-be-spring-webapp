package com.zone.agri.exception;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@ControllerAdvice
@Slf4j
public class ApiExceptionHandler {

  private static final String ERROR_LOG_FORMAT = "Error: URI: {}, ErrorCode: {}, Message: {}";

  @ExceptionHandler(NotFoundException.class)
  public ResponseEntity<ErrorDetail> handleNotFoundException(NotFoundException ex,
      WebRequest request) {
    String message = ex.getMessage();
    ErrorDetail errorVm = new ErrorDetail(HttpStatus.NOT_FOUND.toString(), "Not Found", message);
    log.warn(ERROR_LOG_FORMAT, this.getServletPath(request), 404, message);
    log.debug(ex.toString());
    return new ResponseEntity<>(errorVm, HttpStatus.NOT_FOUND);
  }

  @ExceptionHandler({ BadRequestException.class, IllegalArgumentException.class })
  public ResponseEntity<ErrorDetail> handleBadRequestException(Exception ex,
      WebRequest request) {
    String message = ex.getMessage();
    ErrorDetail errorVm = new ErrorDetail(HttpStatus.BAD_REQUEST.toString(), "Bad Request",
        message);
    return ResponseEntity.badRequest().body(errorVm);
  }

  @ExceptionHandler(ConflictException.class)
  public ResponseEntity<Map<String, Object>> handleConflictException(ConflictException ex,
      WebRequest request) {
    String message = ex.getMessage();
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("statusCode", HttpStatus.CONFLICT.toString());
    payload.put("title", "Conflict");
    payload.put("detail", message);
    payload.put("message", message);
    if (ex.getCode() != null && !ex.getCode().isBlank()) {
      payload.put("code", ex.getCode());
    }
    if (ex.getPayload() != null && !ex.getPayload().isEmpty()) {
      payload.putAll(ex.getPayload());
    }
    return ResponseEntity.status(HttpStatus.CONFLICT).body(payload);
  }

  @ExceptionHandler(RateLimitException.class)
  public ResponseEntity<Map<String, Object>> handleRateLimitException(RateLimitException ex,
      WebRequest request) {
    Map<String, Object> payload = new HashMap<>();
    payload.put("message", ex.getMessage());
    payload.put("code", ex.getCode());
    payload.put("retryAfterSeconds", ex.getRetryAfterSeconds());
    log.warn(ERROR_LOG_FORMAT, this.getServletPath(request), 409, ex.getMessage());
    return ResponseEntity.status(HttpStatus.CONFLICT).body(payload);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ErrorDetail> handleHttpMessageNotReadable(
      HttpMessageNotReadableException ex, WebRequest request) {
    String message = "Dữ liệu gửi lên không hợp lệ: " + ex.getMostSpecificCause().getMessage();
    ErrorDetail errorVm = new ErrorDetail("400", "Bad Request", message);
    log.warn(ERROR_LOG_FORMAT, this.getServletPath(request), 400, message);
    return ResponseEntity.badRequest().body(errorVm);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  protected ResponseEntity<ErrorDetail> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex) {
    List<String> errors = ex.getBindingResult().getFieldErrors().stream()
        .map(error -> error.getDefaultMessage() != null
            ? error.getDefaultMessage()
            : error.getField() + " không hợp lệ")
        .distinct()
        .toList();

    String detail = errors.isEmpty()
        ? "Dữ liệu gửi lên không hợp lệ"
        : String.join(". ", errors);

    ErrorDetail errorVm = new ErrorDetail("400", "Bad Request", detail,
        errors);
    return ResponseEntity.badRequest().body(errorVm);
  }

  @ExceptionHandler({ SignInRequiredException.class })
  public ResponseEntity<ErrorDetail> handleSignInRequired(SignInRequiredException ex) {
    String message = ex.getMessage();
    ErrorDetail errorVm = new ErrorDetail(HttpStatus.UNAUTHORIZED.toString(), "Authentication required", message);
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorVm);
  }

  @ExceptionHandler({ Forbidden.class })
  public ResponseEntity<ErrorDetail> handleForbidden(Forbidden ex, WebRequest request) {
    String message = ex.getMessage();
    ErrorDetail errorVm = new ErrorDetail(HttpStatus.FORBIDDEN.toString(), "Forbidden", message);
    log.warn(ERROR_LOG_FORMAT, this.getServletPath(request), 403, message);
    log.debug(ex.toString());
    return new ResponseEntity<>(errorVm, HttpStatus.FORBIDDEN);
  }

  @ExceptionHandler({ AuthenticationException.class })
  public ResponseEntity<ErrorDetail> handleAuthenticationException(AuthenticationException ex,
      WebRequest request) {
    String message = ex.getMessage();
    ErrorDetail errorVm = new ErrorDetail(HttpStatus.UNAUTHORIZED.toString(),
        "Authentication failed", message);
    log.warn(ERROR_LOG_FORMAT, this.getServletPath(request), 401, message);
    log.debug(ex.toString());
    return new ResponseEntity<>(errorVm, HttpStatus.UNAUTHORIZED);
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<ErrorDetail> handleDataIntegrityViolation(
      DataIntegrityViolationException ex, WebRequest request) {
    String detail = "Dữ liệu vi phạm ràng buộc cơ sở dữ liệu.";
    Throwable cause = ex.getRootCause();
    if (cause != null && cause.getMessage() != null) {
      String rootMessage = cause.getMessage();
      if (rootMessage.contains("Duplicate entry")) {
        detail = "Dữ liệu bị trùng lặp. Vui lòng kiểm tra lại thông tin đã nhập.";
      } else if (rootMessage.contains("Data too long for column 'slug'")) {
        detail = "Mã vai trò vượt quá giới hạn 50 ký tự. Vui lòng rút gọn tên vai trò.";
      } else if (rootMessage.contains("Data too long for column 'display_name'")) {
        detail = "Tên vai trò tối đa 100 ký tự.";
      } else if (rootMessage.contains("Data too long for column 'description'")) {
        detail = "Mô tả vai trò tối đa 255 ký tự.";
      }
    }
    ErrorDetail errorVm = new ErrorDetail(HttpStatus.CONFLICT.toString(), "Xung đột dữ liệu", detail);
    log.warn(ERROR_LOG_FORMAT, this.getServletPath(request), 409, detail);
    return ResponseEntity.status(HttpStatus.CONFLICT).body(errorVm);
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public ResponseEntity<ErrorDetail> handleMaxSizeException(MaxUploadSizeExceededException exc,
      WebRequest request) {
    String message = exc.getMessage();
    ErrorDetail errorVm = new ErrorDetail(HttpStatus.NOT_FOUND.toString(),
        "Maximum upload size exceeded",
        "File is too large! Please upload a file smaller than 100MB.");
    log.warn(ERROR_LOG_FORMAT, this.getServletPath(request), 404, message);
    log.debug(exc.toString());
    return new ResponseEntity<>(errorVm, HttpStatus.NOT_FOUND);
  }

  @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
  public ResponseEntity<ErrorDetail> handleResponseStatusException(
      org.springframework.web.server.ResponseStatusException ex, WebRequest request) {
    String detail = ex.getReason() != null && !ex.getReason().isBlank()
        ? ex.getReason()
        : "Yêu cầu không thể được xử lý.";
    ErrorDetail errorVm = new ErrorDetail(ex.getStatusCode().toString(), ex.getReason(), detail);
    log.warn(ERROR_LOG_FORMAT, this.getServletPath(request), ex.getStatusCode().value(), ex.getReason());
    return new ResponseEntity<>(errorVm, ex.getStatusCode());
  }

  @ExceptionHandler(Exception.class)
  protected ResponseEntity<ErrorDetail> handleOtherException(Exception ex, WebRequest request) {
    String message = ex.getMessage();
    String detail = message != null && !message.isBlank()
        ? message
        : "Có lỗi hệ thống xảy ra, vui lòng thử lại sau.";
    ErrorDetail errorVm = new ErrorDetail(HttpStatus.INTERNAL_SERVER_ERROR.toString(),
        HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(), detail);
    log.warn(ERROR_LOG_FORMAT, this.getServletPath(request), 500, message);
    log.debug(ex.toString());
    return new ResponseEntity<>(errorVm, HttpStatus.INTERNAL_SERVER_ERROR);
  }

  private String getServletPath(WebRequest webRequest) {
    ServletWebRequest servletRequest = (ServletWebRequest) webRequest;
    return servletRequest.getRequest().getServletPath();
  }
}
