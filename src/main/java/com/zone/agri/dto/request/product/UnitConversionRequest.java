package com.zone.agri.dto.request.product;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class UnitConversionRequest {

    @NotBlank(message = "Đơn vị gốc (fromUnit) không được để trống")
    private String fromUnit;

    @NotBlank(message = "Đơn vị đích (toUnit) không được để trống")
    private String toUnit;

    @NotNull(message = "Tỉ lệ quy đổi không được để trống")
    @Positive(message = "Tỉ lệ quy đổi phải lớn hơn 0")
    private BigDecimal rate;
}
