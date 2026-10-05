package com.vayvora.callservice.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class VoiceEngineConfig {

    @Bean
    public RestTemplate voiceEngineRestTemplate(RestTemplateBuilder builder,
                                                VoiceEngineProperties properties) {
        return builder
                .rootUri(properties.getBaseUrl())
                .setConnectTimeout(properties.getConnectTimeout())
                .setReadTimeout(properties.getReadTimeout())
                .build();
    }
}
