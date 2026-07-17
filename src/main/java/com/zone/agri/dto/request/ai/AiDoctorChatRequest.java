package com.zone.agri.dto.request.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiDoctorChatRequest {

    private String message;
    private String sessionId;
    private DiagnosisContext diagnosisContext;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DiagnosisContext {
        private String diseaseCode;
        private String diseaseName;
    }
}
