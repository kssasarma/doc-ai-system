package com.docai.ingestor.application.service.impl;

import java.io.File;
import java.io.FileInputStream;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Component;

import com.docai.ingestor.application.service.DocumentParser;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class HtmlParser implements DocumentParser {

    @Override
    public boolean supports(String fileType) {
        return "html".equalsIgnoreCase(fileType) || "htm".equalsIgnoreCase(fileType);
    }

    @Override
    public String parseDocument(File file) throws Exception {
        log.info("Parsing HTML file: {}", file.getName());
        try (FileInputStream inputStream = new FileInputStream(file)) {
            BodyContentHandler handler = new BodyContentHandler(-1);
            AutoDetectParser parser = new AutoDetectParser();
            parser.parse(inputStream, handler, new Metadata(), new ParseContext());
            String content = handler.toString().trim();
            log.info("Parsed HTML: {} characters", content.length());
            return content;
        }
    }
}
