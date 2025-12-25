package com.agrishrimp.agrishrimpbe.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;

import java.util.List;

@ControllerAdvice
@Slf4j
public class ApiExceptionHandler {
    private static final String ERROR_LOG_FORMAT = "Error: URI: {}, ErrorCode: {}, Message: {}";

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorDetail> handleNotFoundException(NotFoundException ex, WebRequest request) {
        String message = ex.getMessage();
        ErrorDetail errorVm = new ErrorDetail(String.valueOf(HttpStatus.NOT_FOUND.value()), "Not Found", message);
        log.warn(ERROR_LOG_FORMAT, this.getServletPath(request), 404, message);
        return new ResponseEntity<>(errorVm, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorDetail> handleBadRequestException(BadRequestException ex, WebRequest request) {
        String message = ex.getMessage();
        ErrorDetail errorVm = new ErrorDetail(String.valueOf(HttpStatus.BAD_REQUEST.value()), "Bad Request", message);
        log.warn(ERROR_LOG_FORMAT, this.getServletPath(request), 400, message);
        return ResponseEntity.badRequest().body(errorVm);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorDetail> handleConflictException(ConflictException ex, WebRequest request) {
        String message = ex.getMessage();
        ErrorDetail errorVm = new ErrorDetail(String.valueOf(HttpStatus.CONFLICT.value()), "Conflict", message);
        log.warn(ERROR_LOG_FORMAT, this.getServletPath(request), 409, message);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorVm);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    protected ResponseEntity<ErrorDetail> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, WebRequest request) {
        List<String> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + " " + error.getDefaultMessage()).toList();
        ErrorDetail errorVm = new ErrorDetail(String.valueOf(HttpStatus.BAD_REQUEST.value()), "Validation Failed", "Request information is not valid", errors);
        log.warn(ERROR_LOG_FORMAT, this.getServletPath(request), 400, errors);
        return ResponseEntity.badRequest().body(errorVm);
    }

    @ExceptionHandler(SignInRequiredException.class)
    public ResponseEntity<ErrorDetail> handleSignInRequired(SignInRequiredException ex, WebRequest request) {
        String message = ex.getMessage();
        ErrorDetail errorVm = new ErrorDetail(String.valueOf(HttpStatus.FORBIDDEN.value()), "Authentication required", message);
        log.warn(ERROR_LOG_FORMAT, this.getServletPath(request), 403, message);
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorVm);
    }

    @ExceptionHandler(Forbidden.class)
    public ResponseEntity<ErrorDetail> handleForbidden(Forbidden ex, WebRequest request) {
        String message = ex.getMessage();
        ErrorDetail errorVm = new ErrorDetail(String.valueOf(HttpStatus.FORBIDDEN.value()), "Forbidden", message);
        log.warn(ERROR_LOG_FORMAT, this.getServletPath(request), 403, message);
        return new ResponseEntity<>(errorVm, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorDetail> handleAuthenticationException(AuthenticationException ex, WebRequest request) {
        String message = ex.getMessage();
        ErrorDetail errorVm = new ErrorDetail(String.valueOf(HttpStatus.UNAUTHORIZED.value()), "Authentication failed", message);
        log.warn(ERROR_LOG_FORMAT, this.getServletPath(request), 401, message);
        return new ResponseEntity<>(errorVm, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(Exception.class)
    protected ResponseEntity<ErrorDetail> handleOtherException(Exception ex, WebRequest request) {
        String message = ex.getMessage();
        ErrorDetail errorVm = new ErrorDetail(String.valueOf(HttpStatus.INTERNAL_SERVER_ERROR.value()),
                HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(), message);
        log.error(ERROR_LOG_FORMAT, this.getServletPath(request), 500, message, ex);
        return new ResponseEntity<>(errorVm, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private String getServletPath(WebRequest webRequest) {
        ServletWebRequest servletRequest = (ServletWebRequest) webRequest;
        return servletRequest.getRequest().getServletPath();
    }
}
