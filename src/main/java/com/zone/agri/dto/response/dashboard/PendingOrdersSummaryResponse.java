package com.zone.agri.dto.response.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PendingOrdersSummaryResponse {
    private long pendingApproval;
    private long pendingPayment;
    private long pendingPacking;
    private long pendingPickup;
    private long shipping;
    private long cancelPending;
}
