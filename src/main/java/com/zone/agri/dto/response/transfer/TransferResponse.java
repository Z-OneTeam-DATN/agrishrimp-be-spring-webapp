package com.zone.agri.dto.response.transfer;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.zone.agri.entity.enums.InventoryTransferStatus;
import com.zone.agri.entity.enums.TransferBusinessType;
import com.zone.agri.entity.enums.TransferSettlementStatus;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransferResponse {
    private Long id;
    private String transferCode;
    private String transferType;
    private InventoryTransferStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime transferDate;
    private LocalDateTime deadline;
    private String fromBranchName;
    private String toBranchName;
    private String transporter;
    private String referenceCode;
    private String description;
    private String priority;
    private Integer totalQuantity;
    private long itemCount;
    private BigDecimal totalValue;
    private TransferBusinessType transferBusinessType;
    private TransferSettlementStatus settlementStatus;
    private BigDecimal transferAmount;
}
