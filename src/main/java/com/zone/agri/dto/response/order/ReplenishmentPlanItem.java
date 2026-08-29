package com.zone.agri.dto.response.order;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReplenishmentPlanItem {
    private String sku;
    private Integer missingQuantity;
    private String sourceType;
    private String sourceBranchName;
    private String destinationBranchName;
    private Long documentId;
    private String documentType;
    private String documentStatus;
    private String documentCode;
    private String documentPath;
    private String documentLabel;
    private Boolean approvalRequired;
    private String approvalMessage;
    private String message;
}
