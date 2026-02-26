package com.zone.agri.dto.request.inventory;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class ExportNoteRequest {
    private String code;
    private String exportType; // "INTERNAL" (Luân chuyển nội bộ) hoặc "RETURN" (Trả NCC) hoặc "SELL" (Bán hàng)
    private String referenceCode;
    private String note;
    private LocalDate expectedDate;

    private Long branchId; // Kho XUẤT đi (Bắt buộc)
    private Long targetBranchId; // Dành cho xuất nội bộ (Kho nhận)
    private Long supplierId; // Dành cho xuất trả nhà cung cấp
    private Long customerId; // Dành cho xuất bán (nếu có)

    private String specificReceiver;
    private String shippingAddress;
    private Long createdById;

    private List<ExportNoteDetailRequest> details;

    @Data
    public static class ExportNoteDetailRequest {
        private Long productVariantId;
        private Integer requestedQuantity; // Số lượng xuất
        private BigDecimal price; // Giá xuất
        private String note; // Lý do xuất
    }
}