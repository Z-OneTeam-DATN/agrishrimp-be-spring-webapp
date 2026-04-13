package com.zone.agri.dto.request.transfer;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferQCRequest {
    @NotNull(message = "ID biến thể không được để trống")
    private Long variantId;

    @NotNull(message = "Số lượng thực nhận không được để trống")
    @Min(value = 0, message = "Số lượng không được âm")
    private Integer quantityReal;      // Actual

    @NotNull(message = "Số lượng đạt không được để trống")
    @Min(value = 0, message = "Số lượng không được âm")
    private Integer quantityAccepted;  // Accepted

    @NotNull(message = "Số lượng lỗi không được để trống")
    @Min(value = 0, message = "Số lượng không được âm")
    private Integer quantityRejected;  // Rejected

    private String note; // Lý do lỗi
}
