package com.zone.agri.dto.response.ai;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TopPredictionResponse {

    private String diseaseCode;
    private String nameVi;
    private String nameEn;
    private Double confidencePercent;
}
