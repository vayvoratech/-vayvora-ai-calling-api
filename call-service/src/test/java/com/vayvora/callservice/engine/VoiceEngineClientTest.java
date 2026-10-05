package com.vayvora.callservice.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vayvora.callservice.config.VoiceEngineProperties;
import com.vayvora.callservice.engine.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.DefaultUriBuilderFactory;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class VoiceEngineClientTest {
    private static final String BASE = "https://engine.test";

    private MockRestServiceServer server;
    private VoiceEngineClient client;

    @BeforeEach
    void setUp() {
        RestTemplate rest = new RestTemplate();
        rest.setUriTemplateHandler(new DefaultUriBuilderFactory(BASE));
        server = MockRestServiceServer.bindTo(rest).build();

        VoiceEngineProperties props = new VoiceEngineProperties();
        props.setBaseUrl(BASE);
        props.setWsBaseUrl("wss://engine.test");
        client = new VoiceEngineClient(rest, props, new ObjectMapper());
    }

    @Test
    void readsStatus() {
        server.expect(requestTo(BASE + "/api/status"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"status":"ready","overall_mode":"HYBRID","llm_mode":"LIVE",
                         "rag_mode":"IN_MEMORY","tool_mode":"MOCK","vad_mode":"MOCK",
                         "stt_mode":"LIVE","tts_mode":"LIVE",
                         "initialization_notes":["LLM: Live Gemini provider active"]}
                        """, MediaType.APPLICATION_JSON));

        EngineStatusResponse status = client.getStatus();

        assertThat(status.getStatus()).isEqualTo("ready");
        assertThat(status.getLlmMode()).isEqualTo("LIVE");
        assertThat(status.getInitializationNotes()).hasSize(1);
    }

    @Test
    void createsSessionWithDefaultsAndSnakeCaseBody() {
        server.expect(requestTo(BASE + "/api/sessions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {"direction":"inbound","domain":"vayvora","caller_name":"Alex"}
                        """, true))
                .andRespond(withSuccess("""
                        {"session_id":"session-4a2f8b1c","direction":"inbound","domain":"vayvora",
                         "opening_message":null,"diagnostics":{"stage":"greeting","turn_count":0}}
                        """, MediaType.APPLICATION_JSON));

        EngineSessionResponse session = client.createSession(
                EngineSessionRequest.builder().callerName("Alex").build());

        assertThat(session.getSessionId()).isEqualTo("session-4a2f8b1c");
        assertThat(session.getDiagnostics()).containsEntry("stage", "greeting");
    }

    @Test
    void sendsTurn() {
        server.expect(requestTo(BASE + "/api/sessions/session-1/turns"))
                .andExpect(content().json("{\"user_message\":\"What AI courses do you offer?\"}", true))
                .andRespond(withSuccess("""
                        {"session_id":"session-1","response_text":"We offer an AI & ML program.",
                         "latency_ms":318.4,
                         "diagnostics":{"intent":{"current_intent":"course_information"},
                                        "rag":{"grounded_citations":["edusaas:courses:course_catalog"]}}}
                        """, MediaType.APPLICATION_JSON));

        EngineTurnResponse turn = client.sendTurn("session-1", "What AI courses do you offer?");

        assertThat(turn.getResponseText()).startsWith("We offer");
        assertThat(turn.getLatencyMs()).isEqualTo(318.4);
        assertThat(turn.getDiagnostics()).containsKeys("intent", "rag");
    }

    @Test
    void mapsUnknownSessionTo404() {
        server.expect(requestTo(BASE + "/api/sessions/gone/turns"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"detail\":\"Session not found\"}"));

        assertThatThrownBy(() -> client.sendTurn("gone", "hi"))
                .isInstanceOf(VoiceEngineException.class)
                .hasMessage("Session not found")
                .matches(e -> ((VoiceEngineException) e).isSessionNotFound());
    }

    @Test
    void mapsEngineFailureTo502() {
        server.expect(requestTo(BASE + "/api/sessions/s/turns"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.sendTurn("s", "hi"))
                .isInstanceOf(VoiceEngineException.class)
                .matches(e -> ((VoiceEngineException) e).getStatus() == HttpStatus.BAD_GATEWAY);
    }

    @Test
    void listsMcpTools() {
        server.expect(requestTo(BASE + "/mcp"))
                .andExpect(jsonPath("$.jsonrpc").value("2.0"))
                .andExpect(jsonPath("$.method").value("tools/list"))
                .andRespond(withSuccess("""
                        {"jsonrpc":"2.0","id":1,"result":{"tools":[
                          {"name":"send_email","description":"Dispatch an email."},
                          {"name":"find_available_slots","description":"Lookup calendar availability."}]}}
                        """, MediaType.APPLICATION_JSON));

        List<McpTool> tools = client.listTools();

        assertThat(tools).extracting(McpTool::getName)
                .containsExactly("send_email", "find_available_slots");
    }

    @Test
    void callsMcpTool() {
        server.expect(requestTo(BASE + "/mcp"))
                .andExpect(jsonPath("$.method").value("tools/call"))
                .andExpect(jsonPath("$.params.name").value("find_available_slots"))
                .andExpect(jsonPath("$.params.arguments.preferred_date").value("tomorrow"))
                .andRespond(withSuccess("""
                        {"jsonrpc":"2.0","id":3,"result":{"isError":false,"count":3,
                         "slots":["10:00 AM","02:00 PM","04:30 PM"],
                         "message":"Available slots: 10:00 AM, 02:00 PM, 04:30 PM"}}
                        """, MediaType.APPLICATION_JSON));

        McpToolCallResult result = client.callTool("find_available_slots", Map.of("preferred_date", "tomorrow"));

        assertThat(result.isError()).isFalse();
        assertThat(result.getMessage()).startsWith("Available slots");
        assertThat(result.getResult()).containsEntry("count", 3);
    }

    @Test
    void reportsToolErrorFromContentText() {
        server.expect(requestTo(BASE + "/mcp"))
                .andRespond(withSuccess("""
                        {"jsonrpc":"2.0","id":4,"result":{
                         "content":[{"type":"text","text":"Error: Tool 'x' not found."}],
                         "isError":true,"error":"Tool 'x' not found."}}
                        """, MediaType.APPLICATION_JSON));

        McpToolCallResult result = client.callTool("x", null);

        assertThat(result.isError()).isTrue();
        assertThat(result.getMessage()).contains("not found");
    }

    @Test
    void surfacesJsonRpcErrors() {
        server.expect(requestTo(BASE + "/mcp"))
                .andRespond(withSuccess("""
                        {"jsonrpc":"2.0","id":1,"error":{"code":-32601,"message":"Method not found"}}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.listTools())
                .isInstanceOf(VoiceEngineException.class)
                .hasMessageContaining("Method not found");
    }

    @Test
    void buildsMediaStreamUrl() {
        String url = client.mediaStreamUrl("call_01", "edusaas", "inbound", "+15551234567", null);

        assertThat(url).isEqualTo(
                "wss://engine.test/media-stream?session_id=call_01&domain=edusaas"
                        + "&direction=inbound&caller_phone=%2B15551234567");
    }
}
