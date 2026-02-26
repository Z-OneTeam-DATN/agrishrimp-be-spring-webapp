package com.zone.agri.dto.geo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShippingFeeResult {
    private BigDecimal totalFee;
    private String estimatedDays;
    private String carrier;
    /** true nếu lấy từ fallback/ước tính do API lỗi */
    private boolean isEstimate;
}
