package com.zone.agri.dto.request.returns;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ReturnRequestReceiveItemRequest {

    @NotNull
    private Long returnRequestItemId;

    @NotNull
    @Min(0)
    private Integer restockQuantity;

    @NotNull
    @Min(0)
    private Integer defectiveQuantity;

    private String itemNote;
}
