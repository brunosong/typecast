package com.example.typecast.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "typecast")
public record TypecastProperties(
        String baseUrl,
        String apiKey,
        String model,
        String defaultVoiceId,
        String outputDir
) {
}
