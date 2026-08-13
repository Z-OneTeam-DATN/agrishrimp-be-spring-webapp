package com.zone.agri.dto.response.returns;

import com.zone.agri.entity.enums.ReturnIssueType;
import com.zone.agri.entity.enums.ReturnRefundMethod;
import com.zone.agri.entity.enums.ReturnRequestStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class ReturnRequestResponse {
    private Long id;
    private String code;
    private ReturnRequestStatus status;
    private ReturnIssueType issueType;
    private ReturnRefundMethod refundMethod;
    private Boolean requiresPhysicalReturn;
    private Long orderId;
    private String orderCode;
    private Long branchId;
    private String branchName;
    private String customerName;
    private String customerPhone;
    private String customerEmail;
    private String bankAccountName;
    private String bankAccountNumber;
    private String bankName;
    private String bankBranch;
    private String reason;
    private String description;
    private String rejectReason;
    private String internalNote;
    private BigDecimal totalRefundAmount;
    private LocalDateTime createdAt;
    private LocalDateTime approvedAt;
    private LocalDateTime rejectedAt;
    private LocalDateTime receivedAt;
    private LocalDateTime refundedAt;
    private List<ReturnItemResponse> items;
    private List<ReturnEvidenceResponse> evidences;
}
