package com.zone.agri.dto.response.order;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PreparePrimaryBranchDto {
    private Long id;
    private String name;
    private Double distanceKm;
}
