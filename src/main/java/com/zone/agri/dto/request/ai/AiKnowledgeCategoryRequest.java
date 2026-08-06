package com.zone.agri.dto.request.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiKnowledgeCategoryRequest {

    private String name;
    private String slug;
    private String description;
    private Boolean enabled;
    private Integer sortOrder;
}
