package com.zone.agri.dto.response.ai;

import com.zone.agri.dto.response.ai.SuggestedProductResponse;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiKnowledgeTreatmentStageResponse {

    private String stageTitle;
    private List<String> instructions;
    private List<Long> productIds;
    private List<SuggestedProductResponse> products;
    private List<String> extraProductNames;
}
