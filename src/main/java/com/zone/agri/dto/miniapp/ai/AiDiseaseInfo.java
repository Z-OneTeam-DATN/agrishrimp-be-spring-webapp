package com.zone.agri.dto.miniapp.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class AiDiseaseInfo {

    private String code;

    @JsonProperty("name_vi")
    private String nameVi;

    @JsonProperty("name_en")
    private String nameEn;

    @JsonProperty("confidence_level")
    private String confidenceLevel;
}
