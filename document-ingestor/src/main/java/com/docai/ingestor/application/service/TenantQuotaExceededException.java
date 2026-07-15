package com.docai.ingestor.application.service;

/** Thrown by {@link DocumentQuotaService} when a tenant is at or over its plan's document limit —
 * mapped to 409 CONFLICT by GlobalExceptionHandler. */
public class TenantQuotaExceededException extends RuntimeException {
    public TenantQuotaExceededException(String message) {
        super(message);
    }
}
