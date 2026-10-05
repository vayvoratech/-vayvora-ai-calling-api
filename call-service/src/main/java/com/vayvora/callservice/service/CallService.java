package com.vayvora.callservice.service;

import com.vayvora.callservice.dto.InitiateCallRequest;
import com.vayvora.callservice.dto.CallResponse;
import com.vayvora.callservice.dto.UpdateCallStatusRequest;
import com.vayvora.callservice.engine.VoiceEngineClient;
import com.vayvora.callservice.engine.dto.EngineSessionRequest;
import com.vayvora.callservice.engine.dto.EngineSessionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class CallService {
    private final VoiceEngineClient voiceEngine;

    public CallResponse initiateCall(InitiateCallRequest request) throws Exception {
        // 1. Generate unique call ID
        // 2. Validate agent exists and is published
        // 3. Create call entity with status INITIATED
        // 4. Save to database
        // 5. Cache call status in Redis (1-hour TTL)
        // 6. Publish CALL_INITIATED event to Kafka with call details
        // 7. Broadcast via WebSocket to connected listeners

        // 8. Open the AI conversation session that will handle the call
        Map<String, Object> vars = request.getVariables() == null ? Map.of() : request.getVariables();
        String domain = stringVar(vars, "domain");
        EngineSessionResponse session = voiceEngine.createSession(EngineSessionRequest.builder()
                .direction("outbound")
                .domain(domain)
                .callerPhone(request.getContactPhoneNumber())
                .contactName(stringVar(vars, "contact_name"))
                .agentName(stringVar(vars, "agent_name"))
                .callerEmail(stringVar(vars, "contact_email"))
                .callerCompany(stringVar(vars, "contact_company"))
                .campaignObjective(stringVar(vars, "campaign_objective"))
                .knownPurpose(stringVar(vars, "known_purpose"))
                .build());

        // 9. Return call response with ID and the engine session to stream audio into
        return CallResponse.builder()
                .organizationId(request.getOrganizationId())
                .agentId(request.getAgentId())
                .campaignId(request.getCampaignId())
                .contactPhoneNumber(request.getContactPhoneNumber())
                .status("INITIATED")
                .engineSessionId(session.getSessionId())
                .mediaStreamUrl(voiceEngine.mediaStreamUrl(session.getSessionId(),
                        session.getDomain(), "outbound", request.getContactPhoneNumber(),
                        stringVar(vars, "contact_name")))
                .build();
    }

    private static String stringVar(Map<String, Object> vars, String key) {
        Object v = vars.get(key);
        return v == null || v.toString().isBlank() ? null : v.toString();
    }

    public CallResponse getCall(String callId, Long organizationId) throws Exception {
        // 1. Check Redis cache first
        // 2. Find call by ID
        // 3. Verify organization ownership
        // 4. Fetch full call details
        // 5. Cache result
        // 6. Return call response
        return CallResponse.builder().build();
    }

    public CallResponse updateCallStatus(String callId, Long organizationId, UpdateCallStatusRequest request) throws Exception {
        // 1. Find call by ID
        // 2. Validate new status
        // 3. Update status, transcript, sentiment, leadScore
        // 4. If status is COMPLETED, set endedAt timestamp
        // 5. Save to database
        // 6. Update Redis cache
        // 7. Publish CALL_STATUS_UPDATED event to Kafka
        // 8. Broadcast via WebSocket
        // 9. Return updated call
        return CallResponse.builder().build();
    }

    public Object getOrganizationCalls(Long organizationId, int limit, int offset, 
                                       String status, Long agentId) throws Exception {
        // 1. Build query with filters
        // 2. Apply pagination
        // 3. Query database
        // 4. Return paginated list of calls
        return null;
    }

    public CallResponse uploadRecording(String callId, Long organizationId, Object request) throws Exception {
        // 1. Find call by ID
        // 2. Update recordingUrl
        // 3. Parse recording for audio analysis
        // 4. Extract transcript (via speech-to-text)
        // 5. Perform sentiment analysis on transcript
        // 6. Calculate lead score
        // 7. Update call with all analytics
        // 8. Save to database
        // 9. Invalidate cache
        // 10. Publish RECORDING_UPLOADED event
        // 11. Return updated call
        return CallResponse.builder().build();
    }

    public void deleteCall(String callId, Long organizationId) throws Exception {
        // 1. Find call by ID
        // 2. Check authorization
        // 3. Soft delete call
        // 4. Invalidate Redis cache
        // 5. Publish CALL_DELETED event
    }
}
