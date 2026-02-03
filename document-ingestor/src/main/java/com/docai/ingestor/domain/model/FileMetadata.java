package com.docai.ingestor.domain.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FileMetadata {
    private String product;
    private String version;
    private String documentName;
    private String filePath;
    private String fileType;
    private String fileHash;
    
    public static FileMetadata fromFileName(String fileName, String filePath, String fileHash) {
        String[] parts = fileName.split("-");
        if (parts.length < 3) {
            throw new IllegalArgumentException(
                "Invalid file name format. Expected: product-version-documentName.ext");
        }
        
        String product = parts[0];
        String version = parts[1];
        String documentName = fileName.substring(
            product.length() + version.length() + 2);
        String fileType = getFileExtension(fileName);
        
        return FileMetadata.builder()
            .product(product)
            .version(version)
            .documentName(documentName)
            .filePath(filePath)
            .fileType(fileType)
            .fileHash(fileHash)
            .build();
    }
    
    private static String getFileExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        return lastDot > 0 ? fileName.substring(lastDot + 1).toLowerCase() : "";
    }
}
