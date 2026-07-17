package com.zone.agri.dto.response.admin;

import com.zone.agri.dto.response.product.AttributeValueResponse;
import com.zone.agri.entity.enums.AttributeStatus;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class AttributeDTO {
    private Long id;

    @jakarta.validation.constraints.NotBlank(message = "Ten thuoc tinh khong duoc de trong")
    private String name;

    private String code;

    @NotNull(message = "Trang thai khong duoc de trong")
    private AttributeStatus status;

    @NotEmpty(message = "Thuoc tinh phai co it nhat 1 gia tri")
    private List<String> values;

    private List<AttributeValueResponse> valueDetails;
}
