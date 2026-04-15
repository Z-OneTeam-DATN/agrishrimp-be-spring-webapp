package com.zone.agri.dto.request.inventory;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryQCRequest {
    
    @NotEmpty(message = "Danh sách kiểm đếm không được để trống")
    private List<ItemQCRequest> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ItemQCRequest {
        @NotBlank(message = "Mã sản phẩm không được để trống")
        private String productCode;

        @NotNull(message = "Số lượng thực nhận không được để trống")
        @Min(value = 0, message = "Số lượng không được âm")
        private Integer quantityReal; // Số lượng đạt QC (accepted)

        @Min(value = 0, message = "Tổng số lượng NCC giao không được âm")
        private Integer quantityDelivered; // Tổng NCC giao trong đợt này (optional, ưu tiên dùng nếu FE gửi)

        @Min(value = 0, message = "Số lượng hàng lỗi không được âm")
        private Integer quantityRejected; // Số lượng hàng lỗi / bị từ chối nhận

        private String lotNumber;
        private String expiryDate;
        private String note;
    }
}
