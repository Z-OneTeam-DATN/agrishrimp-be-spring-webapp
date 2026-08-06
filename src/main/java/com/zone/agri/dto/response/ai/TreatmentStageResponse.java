package com.zone.agri.dto.response.ai;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class TreatmentStageResponse {

    private String stageTitle;
    private List<String> instructions;
    private List<SuggestedProductResponse> products;
    private List<String> extraProductNames;
}
