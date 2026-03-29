package com.zone.agri.dto.response.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryDistributionResponse {
    private Long categoryId;
    private String categoryName;
    private BigDecimal totalRevenue;
    private Long totalQuantity;
    private Double percentage; // Tỷ trọng %
}
