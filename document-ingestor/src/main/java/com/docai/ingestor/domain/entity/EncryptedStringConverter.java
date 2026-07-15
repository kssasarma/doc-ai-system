package com.docai.ingestor.domain.entity;

import org.springframework.stereotype.Component;

import com.docai.ingestor.application.service.SecretsCryptoService;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.RequiredArgsConstructor;

/**
 * Transparently encrypts/decrypts a column via {@link SecretsCryptoService} (AES-256-GCM).
 * Registered as a Spring bean (not {@code autoApply}) so Spring Boot's Hibernate bean-container
 * integration can inject {@link SecretsCryptoService} into it — plain {@code new}-ed JPA
 * converters can't reach Spring beans.
 */
@Component
@Converter
@RequiredArgsConstructor
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    private final SecretsCryptoService cryptoService;

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return cryptoService.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return cryptoService.decrypt(dbData);
    }
}
