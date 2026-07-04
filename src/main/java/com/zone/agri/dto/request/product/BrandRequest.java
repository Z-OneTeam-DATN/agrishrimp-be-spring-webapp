package com.zone.agri.dto.request.product;

import com.zone.agri.entity.enums.BrandStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BrandRequest {

    @NotBlank(message = "Tên thương hiệu không được để trống")
    @Size(max = 100, message = "Tên thương hiệu không được vượt quá 100 ký tự")
    private String name;

    @Size(max = 255, message = "Logo URL không được vượt quá 255 ký tự")
    private String logoUrl;

    private BrandStatus status;
}
