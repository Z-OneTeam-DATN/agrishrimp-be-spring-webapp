package com.zone.agri.dto.request.product;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RecommendationClickRequest {

    @NotNull(message = "recommendedProductId không được để trống")
    private Long recommendedProductId;

    private String source;
}
