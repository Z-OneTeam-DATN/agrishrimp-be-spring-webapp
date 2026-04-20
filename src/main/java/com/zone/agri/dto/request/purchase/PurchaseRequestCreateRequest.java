package com.zone.agri.dto.request.purchase;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseRequestCreateRequest {

    @NotBlank(message = "Ma nha cung cap khong duoc de trong")
    private String supplierCode;

    @NotBlank(message = "Vui long chon chi nhanh nhan hang")
    private String branchName;

    private Long branchId;

    private String expectedDeliveryDate;

    private String note;

    @NotEmpty(message = "Phieu yeu cau phai co it nhat 1 san pham")
    @Valid
    private List<ItemRequest> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ItemRequest {

        @NotBlank(message = "Ma san pham khong duoc de trong")
        private String productCode;

        @NotNull(message = "So luong yeu cau khong duoc de trong")
        @Positive(message = "So luong yeu cau phai lon hon 0")
        private Integer requestedQty;

        private BigDecimal unitPrice;

        private String note;
    }
}
