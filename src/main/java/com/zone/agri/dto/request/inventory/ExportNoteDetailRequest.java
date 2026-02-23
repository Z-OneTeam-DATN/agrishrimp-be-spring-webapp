package com.zone.agri.dto.request.inventory;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class ExportNoteDetailRequest {
    private Long productVariantId; // ID phiên bản sản phẩm
    private Integer requestedQuantity; // Số lượng yêu cầu
    private BigDecimal price; // Đơn giá
    private String note; // Ghi chú dòng
}