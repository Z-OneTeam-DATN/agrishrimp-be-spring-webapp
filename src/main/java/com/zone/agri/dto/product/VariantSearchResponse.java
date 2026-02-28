package com.zone.agri.dto.product;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VariantSearchResponse {
    private Long id;
    private String sku;
    private String barcode;
    private String productName;
    private String unit;
    private Integer quantity; // Giữ lại quantity vì nó là tổng tồn kho lúc search để xem hàng
}