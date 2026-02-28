package com.zone.agri.dto.admin;

import com.zone.agri.entity.enums.AttributeStatus;
import lombok.Data;
import java.util.List;

@Data
public class AttributeDTO {
    private Long id;
    private String name;
    private String code;

    private AttributeStatus status;
    private List<String> values;
}
