package com.zone.agri.dto.miniapp.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class AiPredictResponse {

    private boolean success;

    @JsonProperty("top_predictions")
    private List<AiPredictionItem> topPredictions;

    @JsonProperty("final_prediction")
    private AiPredictionItem finalPrediction;
}
