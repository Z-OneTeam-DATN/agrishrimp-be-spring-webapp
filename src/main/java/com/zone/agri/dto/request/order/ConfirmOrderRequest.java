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
    @NotBlank(message = "prepareToken la bat buoc")
    private String prepareToken;

    @NotBlank(message = "idempotencyKey la bat buoc")
    private String idempotencyKey;

    private PaymentMethod paymentMethod;
    private String note;
}
