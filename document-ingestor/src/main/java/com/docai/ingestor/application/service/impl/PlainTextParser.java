package com.docai.ingestor.application.service.impl;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.springframework.stereotype.Component;

import com.docai.ingestor.application.service.DocumentParser;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class PlainTextParser implements DocumentParser {

    @Override
    public boolean supports(String fileType) {
        return "txt".equalsIgnoreCase(fileType) || "md".equalsIgnoreCase(fileType);
    }

    @Override
    public String parseDocument(File file) throws Exception {
        log.info("Parsing plain-text file: {}", file.getName());
        String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        log.info("Read plain-text file: {} characters", content.length());
        return content;
    }
}
