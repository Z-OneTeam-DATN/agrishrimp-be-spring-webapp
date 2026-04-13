package com.zone.agri.dto.request.inventory;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.List;

@Data
public class CheckNoteRequest {
    private Long id; // Bổ sung để hỗ trợ cập nhật
    private String code;
    private String note;
    private String type; // Thay checkType thành type
    private java.time.LocalDateTime checkDate;
    private String checkedBy;
    private String createdByName; // FE sends "createdByName"
    
    @NotNull(message = "Chi nhánh kiểm kho không được để trống")
    private Long branchId;
    
    private Long createdById;

    @NotEmpty(message = "Danh sách sản phẩm kiểm kho không được để trống")
    @Valid
    private List<CheckNoteDetailRequest> details;

    @Data
    public static class CheckNoteDetailRequest {
        @NotNull(message = "ID biến thể không được để trống")
        private Long productVariantId;
        
        @NotBlank(message = "Số lô không được để trống")
        private String batchNumber;
        
        @NotNull(message = "Số lượng thực tế không được để trống")
        @jakarta.validation.constraints.Min(value = 0, message = "Số lượng thực tế không được âm")
        private Integer quantityReal;

        private Integer quantityRejected; // Số lượng hàng lỗi (defective)

        private Integer quantity;

        private Integer systemQuantity; // Bổ sung để nhận từ FE

        private java.math.BigDecimal importPrice;

        private String note;
    }
}
