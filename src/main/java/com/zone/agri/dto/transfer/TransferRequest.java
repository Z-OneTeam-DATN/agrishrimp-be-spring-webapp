package com.zone.agri.dto.transfer;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class TransferRequest {
    private Long fromBranchId;
    private Long toBranchId;

    // Các trường mới từ form UI
    private String transferType;
    private String description;
    private String transporter;
    private String vehicle;
    private String dispatchOrder;
    private String referenceCode;
    private String priority;

    private LocalDateTime transferDate;
    private LocalDateTime deadline;

    private List<TransferItemRequest> items;
}