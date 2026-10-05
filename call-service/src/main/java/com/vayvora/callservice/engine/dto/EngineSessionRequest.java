package com.vayvora.callservice.engine.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** POST /api/sessions — opens a multi-turn conversation with domain routing. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EngineSessionRequest {
    @Pattern(regexp = "inbound|outbound", message = "direction must be 'inbound' or 'outbound'")
    private String direction;

    @Pattern(regexp = "edusaas|vayvora", message = "domain must be 'edusaas' or 'vayvora'")
    private String domain;

    @JsonProperty("caller_name")
    private String callerName;

    /** Lead being called (outbound). */
    @JsonProperty("contact_name")
    private String contactName;

    /** Agent persona name (outbound). */
    @JsonProperty("agent_name")
    private String agentName;

    @JsonProperty("caller_phone")
    private String callerPhone;

    @JsonProperty("caller_email")
    private String callerEmail;

    @JsonProperty("caller_company")
    private String callerCompany;

    @JsonProperty("campaign_objective")
    private String campaignObjective;

    @JsonProperty("known_purpose")
    private String knownPurpose;
}
