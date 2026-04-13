package com.zone.agri.dto.miniapp.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class AiPredictResponse {

    private boolean success;

    /** BLURRY | NON_SHRIMP | HEALTHY | DISEASE */
    private String status;

    @JsonProperty("top_predictions")
    private List<AiPredictionItem> topPredictions;

    @JsonProperty("final_prediction")
    private AiPredictionItem finalPrediction;

    @JsonProperty("annotated_image")
    private String annotatedImage;
}
