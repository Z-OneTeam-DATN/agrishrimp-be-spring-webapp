package com.zone.agri.dto.request.order;

import com.zone.agri.entity.enums.PaymentMethod;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConfirmOrderRequest {
    @NotBlank(message = "prepareToken là bắt buộc")
    private String prepareToken;

    private PaymentMethod paymentMethod;
    private String note;
}
