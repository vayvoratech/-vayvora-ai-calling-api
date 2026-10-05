package com.vayvora.callservice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Connection settings for the Unified AI Voice Agent & Conversational Engine
 * (status, text sessions with RAG, real-time media stream and MCP tools).
 */
@Data
@Component
@ConfigurationProperties(prefix = "vayvora.engine")
public class VoiceEngineProperties {
    /** REST base URL, e.g. https://vayvora-ai-calling-ai-pzf9.onrender.com */
    private String baseUrl = "https://vayvora-ai-calling-ai-pzf9.onrender.com";

    /** WebSocket base URL for the /media-stream voice pipeline. */
    private String wsBaseUrl = "wss://vayvora-ai-calling-ai-pzf9.onrender.com";

    /** Domain used when a request does not name one: "edusaas" or "vayvora". */
    private String defaultDomain = "vayvora";

    private Duration connectTimeout = Duration.ofSeconds(10);

    /** Generous by default: the hosted engine cold-starts after idling. */
    private Duration readTimeout = Duration.ofSeconds(90);
}
