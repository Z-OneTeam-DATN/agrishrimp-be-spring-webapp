package com.zone.agri.dto.request.purchase;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseRequestCreateRequest {

    @NotBlank(message = "Mã nhà cung cấp không được để trống")
    private String supplierCode;

    @NotBlank(message = "Vui lòng chọn chi nhánh nhận hàng")
    private String branchName;

    private String expectedDeliveryDate; // ISO date string (yyyy-MM-dd)

    private String note;

    @NotEmpty(message = "Phiếu yêu cầu phải có ít nhất 1 sản phẩm")
    @Valid
    private List<ItemRequest> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ItemRequest {

        @NotBlank(message = "Mã sản phẩm không được để trống")
        private String productCode; // SKU của variant

        @NotNull(message = "Số lượng yêu cầu không được để trống")
        @Positive(message = "Số lượng yêu cầu phải lớn hơn 0")
        private Integer requestedQty;

        private BigDecimal unitPrice;

        private String note;
    }
}
