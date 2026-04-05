package com.zone.agri.dto.miniapp.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DiseaseResponse {

    private String code;
    private String nameVi;
    private String nameEn;
    private Double confidencePercent;
}
