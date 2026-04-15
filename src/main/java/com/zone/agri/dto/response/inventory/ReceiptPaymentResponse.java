package com.zone.agri.dto.response.inventory;

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
public class ReceiptPaymentResponse {
    private Long id;
    private Long receiptId;
    private String receiptCode;
    private Long supplierId;
    private String supplierName;
    private Long branchId;
    private String branchName;
    private BigDecimal amount;
    private BigDecimal remainingDebtAfter;
    private String paymentMethod;
    private String referenceCode;
    private String note;
    private LocalDateTime paymentDate;
    private LocalDateTime createdAt;
    private String createdByName;
}
