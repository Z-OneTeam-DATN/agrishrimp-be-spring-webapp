package com.zone.agri.dto.response.customer;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerDebtResponse {
    private Long id;
    private String customerName;
    private String phone;
    private String staffAssignedName;
    private BigDecimal totalDebt;
}
