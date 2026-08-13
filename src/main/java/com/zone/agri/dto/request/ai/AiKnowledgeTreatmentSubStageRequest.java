package com.zone.agri.dto.request.ai;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiKnowledgeTreatmentSubStageRequest {

    private String subStageTitle;
    private List<String> instructions;
    private List<Long> productIds;
    private List<String> extraProductNames;
}
