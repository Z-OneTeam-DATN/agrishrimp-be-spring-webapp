package com.zone.agri.dto.request.order;

import com.zone.agri.entity.enums.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RetryPendingPaymentRequest {
    @NotNull(message = "paymentMethod la bat buoc")
    private PaymentMethod paymentMethod;
}
