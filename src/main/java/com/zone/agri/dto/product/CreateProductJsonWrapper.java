package com.zone.agri.dto.product;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateProductJsonWrapper {

    @NotNull(message = "Thiếu field 'data'")
    @Valid
    private CreateProductRequest data;
}
