package com.docai.ingestor.application.service.impl;

import java.io.File;

import org.springframework.stereotype.Component;

import com.docai.ingestor.application.service.DocumentParser;
import com.docai.ingestor.application.service.StructuredTextExtractor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class HtmlParser implements DocumentParser {

    private final StructuredTextExtractor extractor;

    @Override
    public boolean supports(String fileType) {
        return "html".equalsIgnoreCase(fileType) || "htm".equalsIgnoreCase(fileType);
    }

    @Override
    public String parseDocument(File file) throws Exception {
        log.info("Parsing HTML file: {}", file.getName());
        String content = extractor.extract(file).trim();
        log.info("Parsed HTML: {} characters", content.length());
        return content;
    }
}
