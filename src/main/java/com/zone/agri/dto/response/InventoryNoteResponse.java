package com.zone.agri.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class InventoryNoteResponse {
    private Long id;
    private String code;
    private String type; // EXPORT
    private String exportType; // INTERNAL / RETURN
    private String status; // PENDING / COMPLETED
    private String reason;
    private String note;
    private String referenceCode;
    private String deliverer; // Sử dụng để lưu tên người nhận
    private String shippingAddress;
    private String entryDate; // Ngày hẹn xuất (yyyy-MM-dd)

    private BigDecimal totalAmount;
    private BigDecimal paymentAmount;
    private BigDecimal debtAmount;

    // Phân loại nguồn / đích
    private Long branchId; // ID kho xuất
    private String branchName; // Tên kho xuất

    private Long partnerBranchId; // ID Kho nhận (nội bộ)
    private String partnerBranchName;

    private Long supplierId; // ID NCC
    private String supplierName;
    private String supplierCode;

    private String displayPartnerName; // Tên hiển thị chung

    private LocalDateTime createdAt;
    private String creatorName;
    private List<InventoryNoteDetailResponse> details;
}