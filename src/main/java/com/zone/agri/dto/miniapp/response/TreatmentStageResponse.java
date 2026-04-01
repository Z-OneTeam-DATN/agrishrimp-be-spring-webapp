package com.zone.agri.dto.miniapp.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class TreatmentStageResponse {

    private String stageTitle;
    private List<String> instructions;
    private List<SuggestedProductResponse> products;
}
