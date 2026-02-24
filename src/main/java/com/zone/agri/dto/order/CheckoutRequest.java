package com.zone.agri.dto.order;

import lombok.Data;
import java.util.List;

@Data
public class CheckoutRequest {
    private String shippingAddress;
    private String phone;
    private String fullName;
    private String note;
    private String voucherCode; // Có thể null
    private Long branchId;
    private List<CheckoutItemRequest> items; // Danh sách sản phẩm mua
}