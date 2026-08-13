package com.zone.agri.dto.response.order;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class SubOrderDraftDto {
    private Long branchId;
    private String branchName;
    private String branchAddress;
    /** districtId của chi nhánh — dùng làm from_district_id cho GHN */
    private Integer fromDistrictId;
    private double durationMinutes;
    private double distanceKm;
    private List<OrderItemDto> items;
    private BigDecimal subtotal;
    private BigDecimal shippingFee;
    private int shippingWeightGram;
    private String estimatedDays;
    private String carrier;
    /** true nếu phí ship là ước tính (API lỗi) */
    private boolean shippingEstimate;
    private String shippingEstimateReason;
}
