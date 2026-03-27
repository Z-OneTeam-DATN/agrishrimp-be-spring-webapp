package com.zone.agri.dto.response.supplier;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupplierDebtResponse {
    private Long id;
    private String supplierCode;
    private String supplierName;
    private String phone;
    private BigDecimal totalDebt;
}
