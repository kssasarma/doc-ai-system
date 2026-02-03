package com.docai.ingestor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync
@EnableScheduling
public class DocumentIngestorApplication {

    public static void main(String[] args) {
        SpringApplication.run(DocumentIngestorApplication.class, args);
    }
}
