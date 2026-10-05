package com.vayvora.callservice.engine.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class EngineSessionResponse {
    @JsonProperty("session_id")
    private String sessionId;

    private String direction;
    private String domain;

    @JsonProperty("opening_message")
    private String openingMessage;

    /** Passed through as-is; the engine's diagnostics layout varies by version. */
    private Map<String, Object> diagnostics;
}
