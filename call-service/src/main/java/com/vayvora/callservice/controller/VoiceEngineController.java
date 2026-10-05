package com.vayvora.callservice.controller;

import com.vayvora.callservice.engine.VoiceEngineClient;
import com.vayvora.callservice.engine.VoiceEngineException;
import com.vayvora.callservice.engine.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Platform-facing proxy for the Unified AI Voice Agent & Conversational
 * Engine, so clients go through the gateway's auth instead of calling the
 * engine directly.
 */
@RestController
@RequestMapping("/api/v1/engine")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class VoiceEngineController {
    private final VoiceEngineClient engine;

    @GetMapping("/status")
    public EngineStatusResponse getStatus() {
        return engine.getStatus();
    }

    @PostMapping("/sessions")
    public ResponseEntity<EngineSessionResponse> createSession(
            @Valid @RequestBody(required = false) EngineSessionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(engine.createSession(request));
    }

    @PostMapping("/sessions/{sessionId}/turns")
    public EngineTurnResponse sendTurn(@PathVariable String sessionId,
                                       @Valid @RequestBody EngineTurnRequest request) {
        return engine.sendTurn(sessionId, request.getUserMessage());
    }

    @PostMapping("/sessions/{sessionId}/reset")
    public Map<String, Object> resetSession(@PathVariable String sessionId) {
        return engine.resetSession(sessionId);
    }

    @GetMapping("/tools")
    public List<McpTool> listTools() {
        return engine.listTools();
    }

    @PostMapping("/tools/{toolName}")
    public McpToolCallResult callTool(@PathVariable String toolName,
                                      @RequestBody(required = false) McpToolCallRequest request) {
        return engine.callTool(toolName, request == null ? Map.of() : request.getArguments());
    }

    /** WebSocket URL for the real-time voice pipeline with the given call context. */
    @GetMapping("/media-stream-url")
    public Map<String, String> mediaStreamUrl(@RequestParam(required = false) String sessionId,
                                              @RequestParam(required = false) String domain,
                                              @RequestParam(required = false) String direction,
                                              @RequestParam(required = false) String callerPhone,
                                              @RequestParam(required = false) String callerName) {
        return Map.of("url", engine.mediaStreamUrl(sessionId, domain, direction, callerPhone, callerName));
    }

    @ExceptionHandler(VoiceEngineException.class)
    public ResponseEntity<Map<String, String>> handleEngineError(VoiceEngineException e) {
        return ResponseEntity.status(e.getStatus()).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Invalid request");
        return ResponseEntity.badRequest().body(Map.of("error", message));
    }
}
