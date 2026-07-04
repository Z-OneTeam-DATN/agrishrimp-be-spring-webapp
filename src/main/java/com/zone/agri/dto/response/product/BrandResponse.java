package com.zone.agri.dto.response.product;

import com.zone.agri.entity.enums.BrandStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BrandResponse {
    private Long id;
    private String name;
    private String logoUrl;
    private BrandStatus status;
}

