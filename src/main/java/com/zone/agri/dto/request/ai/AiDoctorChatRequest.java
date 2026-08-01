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
    /** Anh nong dan gui kem (base64, khong co tien to "data:image/...;base64,") — null neu khong co. */
    private String imageBase64;
    /** Mime type cua imageBase64 (vd "image/jpeg"), bo qua neu imageBase64 null. */
    private String imageMimeType;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DiagnosisContext {
        private String diseaseCode;
        private String diseaseName;
    }
}
