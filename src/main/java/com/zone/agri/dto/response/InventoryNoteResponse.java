package com.zone.agri.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class InventoryNoteResponse {
    private Long id;
    private String code;
    private String type;
    private String status;
    private String reason;
    private BigDecimal totalAmount;
    private String branchName;
    private String partnerBranchName;
    private String supplierName;
    private LocalDateTime createdAt;
    private String creatorName;
    private List<InventoryNoteDetailResponse> details;
}
