package com.zone.agri.dto.stock;

import com.zone.agri.entity.enums.StockRequestStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class StockRequestResponse {

    Long id;
    String requestCode;

    Long fromBranchId;
    String fromBranchName;

    Long toBranchId;
    String toBranchName;

    StockRequestStatus status;
    String note;
    String rejectReason;

    Long approvedBy;
    LocalDateTime approvedAt;

    LocalDateTime createdAt;
    Long createdByUserId;

    List<StockRequestItemResponse> items;
}
