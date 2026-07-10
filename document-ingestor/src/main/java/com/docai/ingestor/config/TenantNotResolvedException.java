package com.docai.ingestor.config;

/** Thrown when a tenant-scoped operation runs with no tenant resolved for the current request. */
public class TenantNotResolvedException extends RuntimeException {
    public TenantNotResolvedException() {
        super("No tenant could be resolved for this request");
    }
}
