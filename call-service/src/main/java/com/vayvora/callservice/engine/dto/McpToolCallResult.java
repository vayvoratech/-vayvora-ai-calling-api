package com.vayvora.callservice.engine.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Outcome of an MCP tools/call. {@code result} keeps the tool-specific
 * fields (slots, message_id, recipient, …) exactly as the engine sent them.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class McpToolCallResult {
    private String tool;
    private boolean error;
    private String message;
    private Map<String, Object> result;
}
