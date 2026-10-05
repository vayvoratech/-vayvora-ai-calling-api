package com.vayvora.callservice.engine;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vayvora.callservice.config.VoiceEngineProperties;
import com.vayvora.callservice.engine.dto.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Client for the Unified AI Voice Agent & Conversational Engine.
 *
 * <ul>
 *   <li>GET  /api/status — subsystem health</li>
 *   <li>POST /api/sessions, /api/sessions/{id}/turns, /api/sessions/{id}/reset — text conversation with RAG</li>
 *   <li>POST /mcp — JSON-RPC 2.0 tools/list and tools/call</li>
 *   <li>WSS  /media-stream — real-time voice; see {@link #mediaStreamUrl}</li>
 * </ul>
 */
@Slf4j
@Component
public class VoiceEngineClient {
    private static final ParameterizedTypeReference<Map<String, Object>> MAP =
            new ParameterizedTypeReference<>() {};
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final RestTemplate restTemplate;
    private final VoiceEngineProperties properties;
    private final ObjectMapper objectMapper;
    private final AtomicLong rpcId = new AtomicLong();

    public VoiceEngineClient(@Qualifier("voiceEngineRestTemplate") RestTemplate restTemplate,
                             VoiceEngineProperties properties,
                             ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    // -- System health ----------------------------------------------------------

    public EngineStatusResponse getStatus() {
        return call("status", () -> restTemplate.getForObject("/api/status", EngineStatusResponse.class));
    }

    // -- Text conversation & RAG ------------------------------------------------

    public EngineSessionResponse createSession(EngineSessionRequest request) {
        EngineSessionRequest body = request == null ? new EngineSessionRequest() : request;
        if (body.getDomain() == null) body.setDomain(properties.getDefaultDomain());
        if (body.getDirection() == null) body.setDirection("inbound");
        return call("create session", () ->
                restTemplate.postForObject("/api/sessions", body, EngineSessionResponse.class));
    }

    public EngineTurnResponse sendTurn(String sessionId, String userMessage) {
        return call("turn", () -> restTemplate.postForObject(
                "/api/sessions/{id}/turns",
                new EngineTurnRequest(userMessage),
                EngineTurnResponse.class,
                sessionId));
    }

    public Map<String, Object> resetSession(String sessionId) {
        return call("reset session", () -> restTemplate.exchange(
                "/api/sessions/{id}/reset",
                HttpMethod.POST,
                null,
                MAP,
                sessionId).getBody());
    }

    // -- MCP tool execution -------------------------------------------------------

    public List<McpTool> listTools() {
        JsonNode result = rpc("tools/list", null);
        List<McpTool> tools = new ArrayList<>();
        for (JsonNode t : result.path("tools")) {
            tools.add(objectMapper.convertValue(t, McpTool.class));
        }
        return tools;
    }

    public McpToolCallResult callTool(String name, Map<String, Object> arguments) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", name);
        params.put("arguments", arguments == null ? Map.of() : arguments);

        JsonNode result = rpc("tools/call", params);
        Map<String, Object> raw = objectMapper.convertValue(result, MAP_TYPE);
        return McpToolCallResult.builder()
                .tool(name)
                .error(result.path("isError").asBoolean(false))
                .message(toolMessage(result))
                .result(raw)
                .build();
    }

    // -- Real-time voice --------------------------------------------------------------

    /**
     * Connection URL for the full-duplex voice pipeline. Clients stream raw
     * PCM16 mono audio (16 kHz or 48 kHz) and receive synthesized PCM audio
     * plus JSON telemetry (transcript, llm_chunk, tts_first_audio, interrupt, …).
     */
    public String mediaStreamUrl(String sessionId, String domain, String direction,
                                 String callerPhone, String callerName) {
        Map<String, String> params = new LinkedHashMap<>();
        if (hasText(sessionId)) params.put("session_id", sessionId);
        params.put("domain", hasText(domain) ? domain : properties.getDefaultDomain());
        params.put("direction", hasText(direction) ? direction : "inbound");
        if (hasText(callerPhone)) params.put("caller_phone", callerPhone);
        if (hasText(callerName)) params.put("caller_name", callerName);

        // Values go in as template variables so they are strictly encoded;
        // a literal "+" in a phone number would otherwise decode as a space.
        UriComponentsBuilder b = UriComponentsBuilder
                .fromUriString(properties.getWsBaseUrl())
                .path("/media-stream");
        params.keySet().forEach(k -> b.queryParam(k, "{" + k + "}"));
        return b.encode().buildAndExpand(params).toUriString();
    }

    // -- Transport -----------------------------------------------------------------------

    private JsonNode rpc(String method, Map<String, Object> params) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("jsonrpc", "2.0");
        request.put("id", rpcId.incrementAndGet());
        request.put("method", method);
        if (params != null) request.put("params", params);

        JsonNode response = call("MCP " + method,
                () -> restTemplate.postForObject("/mcp", request, JsonNode.class));
        if (response == null) {
            throw new VoiceEngineException(HttpStatus.BAD_GATEWAY, "Empty MCP response for " + method);
        }
        JsonNode error = response.path("error");
        if (error.isObject()) {
            throw new VoiceEngineException(HttpStatus.BAD_GATEWAY,
                    "MCP " + method + " failed: " + error.path("message").asText("unknown error"));
        }
        return response.path("result");
    }

    /** Prefer the tool's own message, then MCP text content, then the error field. */
    private static String toolMessage(JsonNode result) {
        if (result.hasNonNull("message")) return result.get("message").asText();
        StringBuilder text = new StringBuilder();
        for (JsonNode c : result.path("content")) {
            if (c.hasNonNull("text")) {
                if (!text.isEmpty()) text.append('\n');
                text.append(c.get("text").asText());
            }
        }
        if (!text.isEmpty()) return text.toString();
        if (result.hasNonNull("error")) return result.get("error").asText();
        return result.path("status").asText(null);
    }

    private <T> T call(String operation, Supplier<T> request) {
        try {
            return request.get();
        } catch (HttpStatusCodeException e) {
            HttpStatus status = HttpStatus.resolve(e.getStatusCode().value());
            String detail = detail(e.getResponseBodyAsString());
            log.warn("Voice engine {} failed: {} {}", operation, e.getStatusCode(), detail);
            if (status == HttpStatus.NOT_FOUND) {
                throw new VoiceEngineException(HttpStatus.NOT_FOUND,
                        detail != null ? detail : "Session not found or timed out", e);
            }
            if (status != null && status.is4xxClientError()) {
                throw new VoiceEngineException(status,
                        detail != null ? detail : "Rejected by voice engine", e);
            }
            throw new VoiceEngineException(HttpStatus.BAD_GATEWAY,
                    "Voice engine " + operation + " failed" + (detail != null ? ": " + detail : ""), e);
        } catch (ResourceAccessException e) {
            log.warn("Voice engine unreachable during {}: {}", operation, e.getMessage());
            throw new VoiceEngineException(HttpStatus.GATEWAY_TIMEOUT,
                    "Voice engine is unreachable or timed out", e);
        } catch (RestClientException e) {
            throw new VoiceEngineException(HttpStatus.BAD_GATEWAY,
                    "Unexpected response from voice engine during " + operation, e);
        }
    }

    /** FastAPI errors carry {@code detail} as a string or a list of {msg} objects. */
    private String detail(String body) {
        if (!hasText(body)) return null;
        try {
            JsonNode d = objectMapper.readTree(body).path("detail");
            if (d.isTextual()) return d.asText();
            if (d.isArray() && !d.isEmpty()) {
                List<String> msgs = new ArrayList<>();
                d.forEach(e -> msgs.add(e.path("msg").asText(e.toString())));
                return String.join("; ", msgs);
            }
        } catch (Exception ignored) {
            // Not JSON; fall through.
        }
        return null;
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}
