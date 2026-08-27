package com.zone.agri.dto.request.inventory;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class ExportNoteRequest {
    private String code;

    @NotBlank(message = "Loại xuất không được để trống")
    private String exportType; // "INTERNAL" (Luân chuyển nội bộ) hoặc "RETURN" (Trả NCC) hoặc "SELL" (Bán hàng)

    private String referenceCode;
    private String note;
    private LocalDate expectedDate;

    @NotNull(message = "Kho xuất không được để trống")
    private Long branchId; // Kho XUẤT đi (Bắt buộc)

    private Long targetBranchId; // Dành cho xuất nội bộ (Kho nhận)
    private Long supplierId; // Dành cho xuất trả nhà cung cấp
    private Long customerId; // Dành cho xuất bán (nếu có)

    private String specificReceiver;

    private String shippingAddress;
    private String creatorName; // FE sends "creatorName"
    private Long createdById;

    @NotEmpty(message = "Lệnh xuất phải có ít nhất 1 sản phẩm")
    @Valid
    private List<ExportNoteDetailRequest> details;

    @Data
    public static class ExportNoteDetailRequest {
        @NotNull(message = "ID biến thể không được để trống")
        private Long productVariantId;

        @NotNull(message = "Số lượng xuất không được để trống")
        @Positive(message = "Số lượng xuất phải lớn hơn 0")
        private Integer requestedQuantity; // Số lượng xuất

        private String batchNumber;
        private String expiryDate;
        private Integer defectiveQuantity; // Số lượng lỗi hiện có
        private Integer plannedQuantity;   // Số lượng yêu cầu lúc nhập

        private BigDecimal price; // Giá xuất
        private String note; // Lý do xuất
    }
}
