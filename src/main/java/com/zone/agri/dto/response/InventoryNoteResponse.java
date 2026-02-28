package com.zone.agri.dto.response;

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
    private String type;
    private String exportType;
    private String status;
    private String reason;
    private String note;
    private String deliverer;
    private BigDecimal totalAmount;
    private BigDecimal paymentAmount;
    private BigDecimal debtAmount;
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

    private String shippingAddress; // Bổ sung để khớp form

    private List<InventoryNoteDetailResponse> details;
}