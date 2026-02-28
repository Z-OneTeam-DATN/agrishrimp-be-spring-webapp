package com.zone.agri.dto.supplier;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class SupplierImportDto {
    private Long id;
    private String code;
    private String status;
    private BigDecimal totalAmount;
    private LocalDateTime createdAt;
}