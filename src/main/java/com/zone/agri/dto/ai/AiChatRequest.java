package com.zone.agri.dto.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AiChatRequest {

    @JsonProperty("message")
    private String message;

    @JsonProperty("conversation_id")
    private String conversationId;

    @JsonProperty("diagnosis_context")
    private AiChatDiagnosisContext diagnosisContext;

    @Data
    @Builder
    public static class AiChatDiagnosisContext {
        @JsonProperty("disease_code")
        private String diseaseCode;

        @JsonProperty("disease_name")
        private String diseaseName;
    }
}
