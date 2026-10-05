package com.vayvora.callservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CallResponse {
    private String id;
    private Long organizationId;
    private Long agentId;
    private String contactPhoneNumber;
    private Long campaignId;
    private String status;
    private String transcript;
    private String summary;
    private String sentiment;
    private Integer leadScore;
    private Integer duration;
    private String recordingUrl;
    /** Conversational engine session handling this call. */
    private String engineSessionId;
    /** WebSocket URL for streaming the call's PCM16 audio to the voice engine. */
    private String mediaStreamUrl;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private LocalDateTime createdAt;
}
