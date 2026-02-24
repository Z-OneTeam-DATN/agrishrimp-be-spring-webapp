package com.zone.agri.dto.request.inventory;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class ExportNoteRequest {
    private String code;
    private String exportType; // SELL, INTERNAL, WASTE, RETURN
    private String referenceCode; // Mã tham chiếu
    private String note; // Lý do xuất
    private LocalDate expectedDate; // Ngày hẹn xuất

    private Long branchId; // Kho xuất
    private Long customerId; // Dành cho xuất bán
    private Long supplierId; // Dành cho xuất trả nhà cung cấp
    private String specificReceiver; // Người nhận cụ thể
    private String shippingAddress; // Địa chỉ giao hàng
    private Long createdById;
    private Long targetBranchId;
    private List<ExportNoteDetailRequest> details; // Danh sách sản phẩm
}