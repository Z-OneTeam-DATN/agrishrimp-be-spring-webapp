package com.zone.agri.dto.inventory;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExportNoteRequest {
    private String code;
    private String exportType; // "INTERNAL" hoặc "RETURN"
    private String referenceCode;
    private LocalDate expectedDate;
    private String note;

    private Long branchId; // Kho xuất
    private Long targetBranchId; // Kho nhận (nếu INTERNAL)
    private Long supplierId; // NCC nhận (nếu RETURN)

    private String specificReceiver;
    private String shippingAddress;
    private Long createdById;

    private List<ExportItemRequest> details;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExportItemRequest {
        private Long productVariantId;
        private Integer requestedQuantity;
        private BigDecimal price; // Giá bán hoặc giá trả lại
        private String note; // Lý do trả
    }
}