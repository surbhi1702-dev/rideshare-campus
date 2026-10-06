package com.rideshare.common.exception;

import jakarta.persistence.LockTimeoutException;
import jakarta.persistence.PessimisticLockException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

/**
 * Translates every exception into the {@link ApiError} contract.
 * Internal details (SQL, stack traces) are logged, never returned.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApiException(ApiException ex, HttpServletRequest request) {
        log.debug("Business rule rejected request {}: {} - {}", request.getRequestURI(), ex.getErrorCode(), ex.getMessage());
        return build(ApiError.of(ex.getErrorCode(), ex.getMessage(), request.getRequestURI()));
    }

    /** Covers @Valid @RequestBody (MethodArgumentNotValidException) and query-param objects (BindException). */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiError> handleBodyValidation(BindException ex, HttpServletRequest request) {
        List<ApiError.FieldViolation> violations = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new ApiError.FieldViolation(error.getField(),
                        // Type-conversion failures carry internal class names; replace them.
                        error.isBindingFailure() ? "has an invalid value or format" : error.getDefaultMessage()))
                .toList();
        return build(ApiError.of(ErrorCode.VALIDATION_FAILED, "Request validation failed",
                request.getRequestURI(), violations));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleParamValidation(ConstraintViolationException ex, HttpServletRequest request) {
        List<ApiError.FieldViolation> violations = ex.getConstraintViolations().stream()
                .map(v -> new ApiError.FieldViolation(lastPathSegment(v.getPropertyPath().toString()), v.getMessage()))
                .toList();
        return build(ApiError.of(ErrorCode.VALIDATION_FAILED, "Request validation failed",
                request.getRequestURI(), violations));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class})
    public ResponseEntity<ApiError> handleMalformed(Exception ex, HttpServletRequest request) {
        String message = ex instanceof MissingServletRequestParameterException missing
                ? "Missing required parameter '%s'".formatted(missing.getParameterName())
                : "Malformed request: check parameter types and JSON body";
        return build(ApiError.of(ErrorCode.INVALID_REQUEST, message, request.getRequestURI()));
    }

    @ExceptionHandler({AccessDeniedException.class, AuthorizationDeniedException.class})
    public ResponseEntity<ApiError> handleAccessDenied(Exception ex, HttpServletRequest request) {
        return build(ApiError.of(ErrorCode.ACCESS_DENIED, "You are not allowed to perform this action",
                request.getRequestURI()));
    }

    /** Lock wait exceeded while another request held the ride row. */
    @ExceptionHandler({PessimisticLockingFailureException.class, CannotAcquireLockException.class,
            PessimisticLockException.class, LockTimeoutException.class})
    public ResponseEntity<ApiError> handleLockFailure(Exception ex, HttpServletRequest request) {
        log.info("Lock contention on {}: {}", request.getRequestURI(), ex.getMessage());
        return build(ApiError.of(ErrorCode.CONCURRENT_UPDATE,
                "The ride is being updated by someone else right now, please retry", request.getRequestURI()));
    }

    /** Safety net: unique/check constraints are the last line of defence. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        log.warn("Constraint violation on {}: {}", request.getRequestURI(), ex.getMostSpecificCause().getMessage());
        return build(ApiError.of(ErrorCode.CONCURRENT_UPDATE,
                "The request conflicts with existing data, please refresh and retry", request.getRequestURI()));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethod(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        ApiError body = new ApiError(java.time.Instant.now(), 405, "Method Not Allowed", "INVALID_REQUEST",
                ex.getMessage(), request.getRequestURI(), List.of());
        return ResponseEntity.status(405).body(body);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNoResource(NoResourceFoundException ex, HttpServletRequest request) {
        return build(ApiError.of(ErrorCode.RESOURCE_NOT_FOUND, "No endpoint at this path", request.getRequestURI()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error on {}", request.getRequestURI(), ex);
        return build(ApiError.of(ErrorCode.INTERNAL_ERROR, "Something went wrong on our side", request.getRequestURI()));
    }

    private static ResponseEntity<ApiError> build(ApiError body) {
        return ResponseEntity.status(body.status()).body(body);
    }

    private static String lastPathSegment(String path) {
        int dot = path.lastIndexOf('.');
        return dot >= 0 ? path.substring(dot + 1) : path;
    }
}
