package com.docai.ingestor.application.service;

/** Thrown by {@link IngestionService#uploadAndIngest} when the exact same file content has
 * already been successfully processed in the same bucket (tenant-wide corpus or one notebook). */
public class DuplicateDocumentException extends RuntimeException {
    public DuplicateDocumentException(String message) {
        super(message);
    }
}
