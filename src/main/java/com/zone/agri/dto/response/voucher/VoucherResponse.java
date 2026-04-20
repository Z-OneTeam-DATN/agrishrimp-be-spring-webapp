package com.zone.agri.dto.response.voucher;

import com.zone.agri.entity.enums.VoucherDiscountType;
import com.zone.agri.entity.enums.VoucherStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VoucherResponse {
    private Long id;
    private String code;
    private String title;
    private VoucherDiscountType discountType;
    private BigDecimal value;
    private BigDecimal maxDiscount;
    private Integer maxUsagePerUser;
    private BigDecimal minOrderValue;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private Integer quantity;
    private VoucherStatus status;
}
