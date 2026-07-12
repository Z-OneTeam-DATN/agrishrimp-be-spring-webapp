package com.zone.agri.dto.response.product;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class FrequentlyBoughtTogetherResponse {
    private ProductResponse product;
    private Integer supportCount;
    private Integer customerCount;
    private BigDecimal support;
    private BigDecimal confidence;
    private BigDecimal lift;
    private LocalDateTime calculatedAt;
}
