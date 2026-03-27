package com.zone.agri.dto.request.order;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.List;

@Data
public class CheckoutRequest {
    @NotBlank(message = "Địa chỉ nhận hàng không được để trống")
    private String shippingAddress;

    @NotBlank(message = "Số điện thoại không được để trống")
    private String phone;

    @NotBlank(message = "Họ tên người nhận không được để trống")
    private String fullName;

    private String note;
    private String voucherCode; // Có thể null

    @NotNull(message = "Chi nhánh không được để trống")
    private Long branchId;

    @NotEmpty(message = "Đơn hàng phải có ít nhất 1 sản phẩm")
    private List<CheckoutItemRequest> items; // Danh sách sản phẩm mua
}
