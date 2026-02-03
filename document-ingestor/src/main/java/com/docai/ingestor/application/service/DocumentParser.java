package com.docai.ingestor.application.service;

import java.io.File;

public interface DocumentParser {
    
    /**
     * Check if this parser supports the given file type
     */
    boolean supports(String fileType);
    
    /**
     * Extract text content from the document
     */
    String parseDocument(File file) throws Exception;
}
