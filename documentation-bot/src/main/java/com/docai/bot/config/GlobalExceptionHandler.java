package com.docai.bot.config;

import java.time.Instant;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException ex, WebRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
            .map(FieldError::getDefaultMessage)
            .collect(Collectors.joining(", "));
        return ResponseEntity.badRequest()
            .body(ErrorResponse.of("VALIDATION_ERROR", message, request));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex, WebRequest request) {
        return ResponseEntity.badRequest()
            .body(ErrorResponse.of("BAD_REQUEST", ex.getMessage(), request));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(
            NoResourceFoundException ex, WebRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse.of("NOT_FOUND", "No such endpoint", request));
    }

    @ExceptionHandler(TenantNotResolvedException.class)
    public ResponseEntity<ErrorResponse> handleTenantNotResolved(
            TenantNotResolvedException ex, WebRequest request) {
        return ResponseEntity.badRequest()
            .body(ErrorResponse.of("TENANT_NOT_RESOLVED", ex.getMessage(), request));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(
            IllegalStateException ex, WebRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ErrorResponse.of("CONFLICT", ex.getMessage(), request));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(
            AccessDeniedException ex, WebRequest request) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(ErrorResponse.of("FORBIDDEN", "Access denied", request));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(
            Exception ex, WebRequest request) {
        log.error("Unhandled exception at {}", request.getDescription(false), ex);
        return ResponseEntity.internalServerError()
            .body(ErrorResponse.of("INTERNAL_ERROR", "An unexpected error occurred", request));
    }

    @Data
    @Builder
    public static class ErrorResponse {
        private String code;
        private String message;
        private String path;
        private long timestamp;
        private String traceId;

        public static ErrorResponse of(String code, String message, WebRequest request) {
            // Falls back to a fresh id only if RequestCorrelationFilter didn't run for this
            // request (shouldn't happen for any real HTTP request, but keeps this safe as a
            // standalone unit).
            String traceId = MDC.get(RequestCorrelationFilter.MDC_KEY);
            if (traceId == null) {
                traceId = UUID.randomUUID().toString().replace("-", "");
            }
            return ErrorResponse.builder()
                .code(code)
                .message(message)
                .path(request.getDescription(false).replace("uri=", ""))
                .timestamp(Instant.now().toEpochMilli())
                .traceId(traceId)
                .build();
        }
    }
}
