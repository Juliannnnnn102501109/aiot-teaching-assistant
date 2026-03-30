package com.aicoursemaster.ai;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
@RequiredArgsConstructor
public class AiClientConfig {

    private final AiRagProperties aiRagProperties;

    @Bean
    public RestTemplate ragRestTemplate(RestTemplateBuilder builder) {
        int connectTimeoutSeconds = Math.max(1, aiRagProperties.getConnectTimeoutSeconds());
        int readTimeoutSeconds = Math.max(1, aiRagProperties.getReadTimeoutSeconds());
        return builder
                .setConnectTimeout(Duration.ofSeconds(connectTimeoutSeconds))
                .setReadTimeout(Duration.ofSeconds(readTimeoutSeconds))
                .build();
    }
}

