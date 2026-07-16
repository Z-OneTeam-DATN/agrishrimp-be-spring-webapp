package com.zone.agri.dto.response.inventory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryNoteResponse {
    private Long id;
    private String code;
    private String noteType;
    private String exportType;
    private String status;
    private String reason;
    private String note;
    private String deliverer;
    private BigDecimal totalAmount;
    private BigDecimal paymentAmount;
    private BigDecimal debtAmount;
    private String type;
    private String scopeType;
    private LocalDateTime checkDate;
    private String checkedBy;
    private String checkWorkflowStatus;
    private LocalDateTime checkStartedAt;
    private LocalDateTime checkSubmittedAt;
    private LocalDateTime checkApprovedAt;
    private String checkApprovedByName;
    private String checkRecountReason;
    private String checkCancelReason;
    private LocalDateTime checkCancelledAt;
    private String entryDate;
    private LocalDateTime createdAt;

    private Long branchId;
    private String branchName;

    private Long partnerBranchId;
    private String partnerBranchName;

    private Long supplierId;
    private String supplierName;
    private String supplierCode;

    private String displayPartnerName;
    private String creatorName;
    private String createdByName;

    private String shippingAddress;

    private List<InventoryNoteDetailResponse> details;
}
