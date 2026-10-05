package com.vayvora.callservice.engine.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** POST /api/sessions/{session_id}/turns */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EngineTurnRequest {
    @NotBlank(message = "user_message is required")
    @JsonProperty("user_message")
    private String userMessage;
}
