package com.zone.agri.dto.response.ai;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PublicAiDiseaseTreatmentStageResponse {
    private String stageTitle;
    private List<String> instructions;
    private List<SuggestedProductResponse> products;
    private List<String> extraProductNames;
}
