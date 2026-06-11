package com.docai.bot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

@Data
@Component
@ConfigurationProperties(prefix = "digest")
public class DigestProperties {
    private String fromAddress = "noreply@docs-inator.example.com";
    private String fromName = "Docs-inator";
    private String appUrl = "http://localhost:5173";
}
