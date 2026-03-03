package com.zone.agri.dto.order;

import com.zone.agri.entity.Handover;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class HandoverResponse {
    private Long id;
    private String code;
    private String carrier;
    private Integer totalOrders;
    private Double totalWeight;
    private BigDecimal totalCod;
    private String status;
    private String creatorName;
    private LocalDateTime createdAt;

    public static HandoverResponse fromEntity(Handover handover) {
        return HandoverResponse.builder()
                .id(handover.getId())
                .code(handover.getCode())
                .carrier(handover.getCarrier())
                .totalOrders(handover.getTotalOrders())
                .totalWeight(handover.getTotalWeight())
                .totalCod(handover.getTotalCod())
                .status(handover.getStatus())
                .creatorName(handover.getCreatedBy() != null ? handover.getCreatedBy().getFullName() : "Ẩn danh")
                .createdAt(handover.getCreatedAt())
                .build();
    }
}