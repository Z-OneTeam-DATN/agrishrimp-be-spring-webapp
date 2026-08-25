package com.zone.agri.dto.response.order;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SuggestedTransferDto {
    private Long fromBranchId;
    private String fromBranchName;
    private Long toBranchId;
    private Long productVariantId;
    private Integer quantity;
    private String fromRegion;
    private String toRegion;
}
