package com.zone.agri.dto.product;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AttributeValueResponse {
    private Long attributeId;
    private String attributeName;
    private String attributeCode;
    private Long valueId;
    private String value;
}
