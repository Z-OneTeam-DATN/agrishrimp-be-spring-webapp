package com.zone.agri.dto.miniapp.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class AiPredictionItem {

    @JsonProperty("disease_code")
    private String diseaseCode;

    @JsonProperty("english_name")
    private String englishName;

    @JsonProperty("vietnamese_name")
    private String vietnameseName;

    @JsonProperty("confidence_percent")
    private Double confidencePercent;
}
