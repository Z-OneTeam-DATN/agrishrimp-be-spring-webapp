package com.zone.agri.dto.response.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerInsightsResponse {
    private Long totalCustomers;
    private Long activeCustomers;
    private Long newCustomersThisMonth;
}
