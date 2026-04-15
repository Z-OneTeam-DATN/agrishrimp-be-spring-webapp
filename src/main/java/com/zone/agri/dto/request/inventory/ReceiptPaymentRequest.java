package com.zone.agri.dto.request.inventory;

import com.zone.agri.entity.enums.SupplierPaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReceiptPaymentRequest {

    @NotNull(message = "Số tiền thanh toán không được để trống")
    @DecimalMin(value = "0.01", message = "Số tiền thanh toán phải lớn hơn 0")
    private BigDecimal amount;

    private String paymentDate;

    @NotNull(message = "Vui lòng chọn phương thức thanh toán")
    private SupplierPaymentMethod paymentMethod;

    private String referenceCode;
    private String note;
}
