package com.zone.agri.dto.miniapp.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class AiTreatmentStage {

    @JsonProperty("stage_title")
    private String stageTitle;

    private List<String> instructions;

    @JsonProperty("related_product_ids")
    private List<Long> relatedProductIds;
}
