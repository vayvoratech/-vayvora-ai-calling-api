package com.vayvora.callservice.engine.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/** Arguments for one MCP tools/call, e.g. {"preferred_date": "tomorrow"}. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class McpToolCallRequest {
    private Map<String, Object> arguments = new HashMap<>();
}
