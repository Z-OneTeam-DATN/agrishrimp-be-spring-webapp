package com.zone.agri.dto.stock;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class StockRequestCreateDto {

    String note;

    @NotEmpty(message = "Danh sách sản phẩm không được trống")
    @Valid
    List<StockRequestItemRequestDto> items;
}
