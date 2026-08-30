package com.zone.agri.dto.response.returns;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ReturnOrderDraftResponse {
    private Long orderId;
    private String orderCode;
    private String orderStatus;
    private String customerName;
    private String customerPhone;
    private Boolean singleBranchOnly;
    private Boolean canCreateRequest;
    private Long existingRequestId;
    private String existingRequestCode;
    private String message;
    private List<ReturnDraftItemResponse> items;
}
