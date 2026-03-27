package com.zone.agri.dto.response.admin;

import com.zone.agri.dto.response.product.AttributeValueResponse;
import com.zone.agri.entity.enums.AttributeStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.List;

@Data
public class AttributeDTO {
    private Long id;

    @NotBlank(message = "Tên thuộc tính không được để trống")
    private String name;

    @NotBlank(message = "Mã thuộc tính không được để trống")
    private String code;

    @NotNull(message = "Trạng thái không được để trống")
    private AttributeStatus status;

    @NotEmpty(message = "Thuộc tính phải có ít nhất 1 giá trị")
    private List<String> values;

    private List<AttributeValueResponse> valueDetails;
}
