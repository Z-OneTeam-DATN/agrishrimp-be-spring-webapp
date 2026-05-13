package com.zone.agri.dto.response.financial;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CashbookEntryResponse {
    private String id;
    private LocalDate date;
    private Long branchId;
    private String direction;
    private String source;
    private String code;
    private String title;
    private String description;
    private String branchName;
    private String partnerName;
    private String creatorName;
    private String paymentMethod;
    private BigDecimal amount;
    private BigDecimal debtAmount;
    private BigDecimal paymentAmount;
}
