package com.zone.agri.dto.response.branch;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NearestBranchResponse {
    private Long id;
    private String name;
    private String addressText;
    private String phone;
    private double distanceKm;
    private double durationMinutes;
}
