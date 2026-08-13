package com.zone.agri.dto.request.returns;

import com.zone.agri.entity.enums.ReturnRefundMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ReturnRequestRefundRequest {

    @NotNull
    @DecimalMin(value = "0.00")
    BigDecimal refundAmount;

    ReturnRefundMethod refundMethod;

    String internalNote;
}
