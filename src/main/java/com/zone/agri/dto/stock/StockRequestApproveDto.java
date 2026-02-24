package com.zone.agri.dto.stock;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class StockRequestApproveDto {

    @NotEmpty(message = "Danh sách duyệt không được trống")
    @Valid
    List<StockRequestApproveItemDto> items;
}
