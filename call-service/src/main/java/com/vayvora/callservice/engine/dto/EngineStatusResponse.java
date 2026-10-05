package com.vayvora.callservice.engine.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** GET /api/status — runtime mode of each engine subsystem. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class EngineStatusResponse {
    private String status;

    @JsonProperty("overall_mode")
    private String overallMode;

    @JsonProperty("llm_mode")
    private String llmMode;

    @JsonProperty("rag_mode")
    private String ragMode;

    @JsonProperty("tool_mode")
    private String toolMode;

    @JsonProperty("vad_mode")
    private String vadMode;

    @JsonProperty("stt_mode")
    private String sttMode;

    @JsonProperty("tts_mode")
    private String ttsMode;

    @JsonProperty("initialization_notes")
    private List<String> initializationNotes;
}
