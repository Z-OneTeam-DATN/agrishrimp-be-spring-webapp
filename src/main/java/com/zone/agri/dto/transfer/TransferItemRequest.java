package com.zone.agri.dto.transfer;

import lombok.Data;

@Data
public class TransferItemRequest {
    private Long variantId; // Mã hàng hóa quy đổi
    private Integer quantity; // Số lượng muốn chuyển đi
    private String itemNote; // Ghi chú từng dòng
}