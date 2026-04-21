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
    private String noteType; // Đổi từ type -> noteType (IMPORT/EXPORT/CHECK)
    private String exportType;
    private String status;
    private String reason;
    private String note;
    private String deliverer;
    private BigDecimal totalAmount;
    private BigDecimal paymentAmount;
    private BigDecimal debtAmount;
    private String type; // Đây là check type (PERIODIC/UNEXPECTED/YEAR_END)
    private LocalDateTime checkDate;
    private String checkedBy;
    private String checkWorkflowStatus;
    private LocalDateTime checkSubmittedAt;
    private LocalDateTime checkApprovedAt;
    private String checkApprovedByName;
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
    private String createdByName; // Bổ sung theo yêu cầu API mới

    private String shippingAddress; // Bổ sung để khớp form

    private List<InventoryNoteDetailResponse> details;
}
