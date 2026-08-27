package com.zone.agri.exception;

import jakarta.validation.ConstraintViolationException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.AuthenticationException;
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

  @Value("${spring.servlet.multipart.max-file-size}")
  private String maxUploadFileSize;

  @ExceptionHandler(NotFoundException.class)
  public ResponseEntity<ErrorDetail> handleNotFoundException(
      NotFoundException ex,
      WebRequest request) {
    String message = ex.getMessage();
    ErrorDetail errorVm = new ErrorDetail(HttpStatus.NOT_FOUND.toString(), "Not Found", message);
    log.warn(ERROR_LOG_FORMAT, this.getServletPath(request), 404, message);
    log.debug(ex.toString());
    return new ResponseEntity<>(errorVm, HttpStatus.NOT_FOUND);
  }

  @ExceptionHandler({BadRequestException.class, IllegalArgumentException.class})
  public ResponseEntity<Map<String, Object>> handleBadRequestException(
      Exception ex,
      WebRequest request) {
    String message = ex.getMessage();
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("statusCode", HttpStatus.BAD_REQUEST.toString());
    payload.put("title", "Bad Request");
    payload.put("detail", message);
    payload.put("message", message);
    if (ex instanceof BadRequestException badRequestException) {
      if (badRequestException.getCode() != null && !badRequestException.getCode().isBlank()) {
        payload.put("code", badRequestException.getCode());
      }
      if (badRequestException.getPayload() != null && !badRequestException.getPayload().isEmpty()) {
        payload.putAll(badRequestException.getPayload());
      }
    }
    log.warn(ERROR_LOG_FORMAT, this.getServletPath(request), 400, message);
    return ResponseEntity.badRequest().body(payload);
  }

  @ExceptionHandler(ConflictException.class)
  public ResponseEntity<Map<String, Object>> handleConflictException(
      ConflictException ex,
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
  public ResponseEntity<Map<String, Object>> handleRateLimitException(
      RateLimitException ex,
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
      HttpMessageNotReadableException ex,
      WebRequest request) {
    String message =
        "D\u1eef li\u1ec7u g\u1eedi l\u00ean kh\u00f4ng h\u1ee3p l\u1ec7: "
            + ex.getMostSpecificCause().getMessage();
    ErrorDetail errorVm = new ErrorDetail("400", "Bad Request", message);
    log.warn(ERROR_LOG_FORMAT, this.getServletPath(request), 400, message);
    return ResponseEntity.badRequest().body(errorVm);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  protected ResponseEntity<ErrorDetail> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex) {
    List<String> errors =
        ex.getBindingResult().getFieldErrors().stream()
            .map(
                error ->
                    error.getDefaultMessage() != null
                        ? error.getDefaultMessage()
                        : error.getField() + " kh\u00f4ng h\u1ee3p l\u1ec7")
            .distinct()
            .toList();

    String detail =
        errors.isEmpty()
            ? "D\u1eef li\u1ec7u g\u1eedi l\u00ean kh\u00f4ng h\u1ee3p l\u1ec7"
            : String.join(". ", errors);

    ErrorDetail errorVm = new ErrorDetail("400", "Bad Request", detail, errors);
    return ResponseEntity.badRequest().body(errorVm);
  }

  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ErrorDetail> handleConstraintViolationException(
      ConstraintViolationException ex,
      WebRequest request) {
    String detail =
        ex.getConstraintViolations().stream()
            .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
            .collect(java.util.stream.Collectors.joining(", "));
    ErrorDetail errorVm =
        new ErrorDetail(
            HttpStatus.BAD_REQUEST.toString(),
            "Bad Request",
            "D\u1eef li\u1ec7u kh\u00f4ng h\u1ee3p l\u1ec7: " + detail);
    log.warn(ERROR_LOG_FORMAT, this.getServletPath(request), 400, detail);
    return ResponseEntity.badRequest().body(errorVm);
  }

  @ExceptionHandler({SignInRequiredException.class})
  public ResponseEntity<ErrorDetail> handleSignInRequired(SignInRequiredException ex) {
    String message = ex.getMessage();
    ErrorDetail errorVm =
        new ErrorDetail(HttpStatus.UNAUTHORIZED.toString(), "Authentication required", message);
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorVm);
  }

  @ExceptionHandler({Forbidden.class})
  public ResponseEntity<ErrorDetail> handleForbidden(Forbidden ex, WebRequest request) {
    String message = ex.getMessage();
    ErrorDetail errorVm = new ErrorDetail(HttpStatus.FORBIDDEN.toString(), "Forbidden", message);
    log.warn(ERROR_LOG_FORMAT, this.getServletPath(request), 403, message);
    log.debug(ex.toString());
    return new ResponseEntity<>(errorVm, HttpStatus.FORBIDDEN);
  }

  @ExceptionHandler({AuthenticationException.class})
  public ResponseEntity<ErrorDetail> handleAuthenticationException(
      AuthenticationException ex,
      WebRequest request) {
    String message = ex.getMessage();
    ErrorDetail errorVm =
        new ErrorDetail(HttpStatus.UNAUTHORIZED.toString(), "Authentication failed", message);
    log.warn(ERROR_LOG_FORMAT, this.getServletPath(request), 401, message);
    log.debug(ex.toString());
    return new ResponseEntity<>(errorVm, HttpStatus.UNAUTHORIZED);
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<ErrorDetail> handleDataIntegrityViolation(
      DataIntegrityViolationException ex,
      WebRequest request) {
    String detail = "D\u1eef li\u1ec7u vi ph\u1ea1m r\u00e0ng bu\u1ed9c c\u01a1 s\u1edf d\u1eef li\u1ec7u.";
    Throwable cause = ex.getRootCause();
    if (cause != null && cause.getMessage() != null) {
      String rootMessage = cause.getMessage();
      if (rootMessage.contains("Duplicate entry")) {
        detail =
            "D\u1eef li\u1ec7u b\u1ecb tr\u00f9ng l\u1eb7p. Vui l\u00f2ng ki\u1ec3m tra l\u1ea1i th\u00f4ng tin \u0111\u00e3 nh\u1eadp.";
      } else if (rootMessage.contains("Data too long for column 'slug'")) {
        detail =
            "M\u00e3 vai tr\u00f2 v\u01b0\u1ee3t qu\u00e1 gi\u1edbi h\u1ea1n 50 k\u00fd t\u1ef1. Vui l\u00f2ng r\u00fat g\u1ecdn t\u00ean vai tr\u00f2.";
      } else if (rootMessage.contains("Data too long for column 'display_name'")) {
        detail = "T\u00ean vai tr\u00f2 t\u1ed1i \u0111a 100 k\u00fd t\u1ef1.";
      } else if (rootMessage.contains("Data too long for column 'description'")) {
        detail = "M\u00f4 t\u1ea3 vai tr\u00f2 t\u1ed1i \u0111a 255 k\u00fd t\u1ef1.";
      }
    }
    ErrorDetail errorVm =
        new ErrorDetail(HttpStatus.CONFLICT.toString(), "Xung \u0111\u1ed9t d\u1eef li\u1ec7u", detail);
    log.warn(ERROR_LOG_FORMAT, this.getServletPath(request), 409, detail);
    return ResponseEntity.status(HttpStatus.CONFLICT).body(errorVm);
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public ResponseEntity<ErrorDetail> handleMaxSizeException(
      MaxUploadSizeExceededException exc,
      WebRequest request) {
    String message = exc.getMessage();
    ErrorDetail errorVm =
        new ErrorDetail(
            HttpStatus.PAYLOAD_TOO_LARGE.toString(),
            "Payload Too Large",
            "File t\u1ea3i l\u00ean v\u01b0\u1ee3t qu\u00e1 dung l\u01b0\u1ee3ng cho ph\u00e9p (t\u1ed1i \u0111a "
                + maxUploadFileSize
                + "). Vui l\u00f2ng ch\u1ecdn file nh\u1ecf h\u01a1n.");
    log.warn(ERROR_LOG_FORMAT, this.getServletPath(request), 413, message);
    log.debug(exc.toString());
    return new ResponseEntity<>(errorVm, HttpStatus.PAYLOAD_TOO_LARGE);
  }

  @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
  public ResponseEntity<ErrorDetail> handleResponseStatusException(
      org.springframework.web.server.ResponseStatusException ex,
      WebRequest request) {
    String detail =
        ex.getReason() != null && !ex.getReason().isBlank()
            ? ex.getReason()
            : "Y\u00eau c\u1ea7u kh\u00f4ng th\u1ec3 \u0111\u01b0\u1ee3c x\u1eed l\u00fd.";
    ErrorDetail errorVm = new ErrorDetail(ex.getStatusCode().toString(), ex.getReason(), detail);
    log.warn(ERROR_LOG_FORMAT, this.getServletPath(request), ex.getStatusCode().value(), ex.getReason());
    return new ResponseEntity<>(errorVm, ex.getStatusCode());
  }

  @ExceptionHandler(Exception.class)
  protected ResponseEntity<ErrorDetail> handleOtherException(Exception ex, WebRequest request) {
    String message = ex.getMessage();
    String detail =
        message != null && !message.isBlank()
            ? message
            : "C\u00f3 l\u1ed7i h\u1ec7 th\u1ed1ng x\u1ea3y ra, vui l\u00f2ng th\u1eed l\u1ea1i sau.";
    ErrorDetail errorVm =
        new ErrorDetail(
            HttpStatus.INTERNAL_SERVER_ERROR.toString(),
            HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
            detail);
    log.warn(ERROR_LOG_FORMAT, this.getServletPath(request), 500, message);
    log.debug(ex.toString());
    return new ResponseEntity<>(errorVm, HttpStatus.INTERNAL_SERVER_ERROR);
  }

  private String getServletPath(WebRequest webRequest) {
    ServletWebRequest servletRequest = (ServletWebRequest) webRequest;
    return servletRequest.getRequest().getServletPath();
  }
}
