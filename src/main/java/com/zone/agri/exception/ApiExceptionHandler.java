package com.zone.agri.exception;

import java.util.List;
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

  @ExceptionHandler(BadRequestException.class)
  public ResponseEntity<ErrorDetail> handleBadRequestException(BadRequestException ex,
      WebRequest request) {
    String message = ex.getMessage();
    ErrorDetail errorVm = new ErrorDetail(HttpStatus.BAD_REQUEST.toString(), "Bad Request",
        message);
    return ResponseEntity.badRequest().body(errorVm);
  }

  @ExceptionHandler(ConflictException.class)
  public ResponseEntity<ErrorDetail> handleConflictException(ConflictException ex,
      WebRequest request) {
    String message = ex.getMessage();
    ErrorDetail errorVm = new ErrorDetail(HttpStatus.CONFLICT.toString(), "Conflict", message);
    return ResponseEntity.badRequest().body(errorVm);
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
        .map(error -> error.getField() + " " + error.getDefaultMessage()).toList();

    ErrorDetail errorVm = new ErrorDetail("400", "Bad Request", "Request information is not valid",
        errors);
    return ResponseEntity.badRequest().body(errorVm);
  }

  @ExceptionHandler({SignInRequiredException.class})
  public ResponseEntity<ErrorDetail> handleSignInRequired(SignInRequiredException ex) {
    String message = ex.getMessage();
    ErrorDetail errorVm = new ErrorDetail(HttpStatus.UNAUTHORIZED.toString(), "Authentication required", message);
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
    if (cause != null && cause.getMessage() != null && cause.getMessage().contains("Duplicate entry")) {
      detail = "Giá trị bị trùng lặp: " + cause.getMessage();
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
  public ResponseEntity<ErrorDetail> handleResponseStatusException(org.springframework.web.server.ResponseStatusException ex, WebRequest request) {
    ErrorDetail errorVm = new ErrorDetail(ex.getStatusCode().toString(), ex.getReason(), null);
    log.warn(ERROR_LOG_FORMAT, this.getServletPath(request), ex.getStatusCode().value(), ex.getReason());
    return new ResponseEntity<>(errorVm, ex.getStatusCode());
  }

  @ExceptionHandler(Exception.class)
  protected ResponseEntity<ErrorDetail> handleOtherException(Exception ex, WebRequest request) {
    String message = ex.getMessage();
    ErrorDetail errorVm = new ErrorDetail(HttpStatus.INTERNAL_SERVER_ERROR.toString(),
        HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(), null);
    log.warn(ERROR_LOG_FORMAT, this.getServletPath(request), 500, message);
    log.debug(ex.toString());
    return new ResponseEntity<>(errorVm, HttpStatus.INTERNAL_SERVER_ERROR);
  }

  private String getServletPath(WebRequest webRequest) {
    ServletWebRequest servletRequest = (ServletWebRequest) webRequest;
    return servletRequest.getRequest().getServletPath();
  }
}
