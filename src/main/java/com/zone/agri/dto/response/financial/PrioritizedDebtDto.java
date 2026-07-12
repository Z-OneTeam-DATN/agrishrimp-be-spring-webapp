package com.zone.agri.dto.response.financial;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrioritizedDebtDto {
    private Long supplierId;
    private String supplierName;
    private BigDecimal outstandingAmount;
    private LocalDate dueDate;
    private double priorityScore;
    private int priorityRank;
}
