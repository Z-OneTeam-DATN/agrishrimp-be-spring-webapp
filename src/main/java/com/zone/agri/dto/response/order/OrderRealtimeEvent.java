package com.zone.agri.dto.response.order;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Set;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderRealtimeEvent {
    private String eventType;
    private Long orderId;
    private String orderCode;
    private Set<Long> branchIds;
    private String orderStatus;
    private String paymentStatus;
    private LocalDateTime occurredAt;
}
