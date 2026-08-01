package com.zone.agri.dto.request.order;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderCancelRequest {
    private String orderId;

    @NotBlank(message = "reasonCode la bat buoc")
    private String reasonCode;

    private String otherReasonText;
}
