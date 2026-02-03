package com.docai.ingestor.application.service.impl;

import com.docai.ingestor.application.service.DocumentParser;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;

@Slf4j
@Component
public class ChmParser implements DocumentParser {

    @Override
    public boolean supports(String fileType) {
        return "chm".equalsIgnoreCase(fileType);
    }

    @Override
    public String parseDocument(File file) throws Exception {
        log.info("Parsing CHM file: {}", file.getName());
        
        try (FileInputStream inputStream = new FileInputStream(file)) {
            BodyContentHandler handler = new BodyContentHandler(-1); // No limit
            Metadata metadata = new Metadata();
            ParseContext context = new ParseContext();
            AutoDetectParser parser = new AutoDetectParser();
            
            parser.parse(inputStream, handler, metadata, context);
            
            String content = handler.toString();
            log.info("Successfully parsed CHM. Content length: {} characters", content.length());
            
            return content;
        }
    }
}
