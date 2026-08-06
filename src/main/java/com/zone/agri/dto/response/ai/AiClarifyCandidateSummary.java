package com.zone.agri.dto.response.ai;

import lombok.Builder;
import lombok.Data;

/**
 * Tóm tắt 1 bệnh candidate gửi cho Gemini trong luồng hỏi-đáp làm rõ bệnh.
 * Nội bộ — không serialize ra response của controller.
 */
@Data
@Builder
public class AiClarifyCandidateSummary {

    private String diseaseCode;
    private String nameVi;
    private String nameEn;
    private String symptomKeywordsRaw;
    private String signsSummary;
}
