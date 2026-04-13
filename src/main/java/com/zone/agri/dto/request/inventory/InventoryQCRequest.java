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
        private Integer quantityReal;

        private Integer quantityRejected; // Số lượng hàng lỗi (Nếu có)

        private String lotNumber;
        private String expiryDate;
        private String note;
    }
}
