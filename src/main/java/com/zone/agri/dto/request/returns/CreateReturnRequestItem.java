package com.zone.agri.dto.request.returns;

import com.zone.agri.entity.enums.ReturnItemSourceType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateReturnRequestItem {

    @NotNull
    ReturnItemSourceType sourceType;

    @NotNull
    Long sourceItemId;

    @NotNull
    @Min(1)
    Integer quantity;
}
