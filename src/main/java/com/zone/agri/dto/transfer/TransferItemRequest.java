package com.zone.agri.dto.transfer;

import lombok.Data;

@Data
public class TransferItemRequest {
    private String sku;
    private Integer quantity; // Số lượng muốn chuyển đi
    private String itemNote; // Ghi chú từng dòng
}