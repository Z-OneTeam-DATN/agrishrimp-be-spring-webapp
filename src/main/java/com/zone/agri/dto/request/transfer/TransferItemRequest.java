package com.zone.agri.dto.request.transfer;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class TransferItemRequest {
    @NotBlank(message = "Mã SKU không được để trống")
    private String sku;

    @NotNull(message = "Số lượng không được để trống")
    @Min(value = 1, message = "Số lượng phải lớn hơn 0")
    private Integer quantity; // Số lượng muốn chuyển đi

    private String itemNote; // Ghi chú từng dòng

    /**
     * Đơn giá điều chuyển nội bộ (chỉ bắt buộc khi transferBusinessType = INTERNAL_SALE).
     * Đây là giá bán nội bộ, có thể khác giá vốn FIFO của kho xuất.
     */
    private BigDecimal unitTransferPrice;
}
