package com.zone.agri.dto.request.inventory;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class CheckNoteRequest {
    private Long id;
    private String code;
    private String note;
    private String type;
    private String scopeType;
    private java.time.LocalDateTime checkDate;
    private String checkedBy;
    private String createdByName;

    @NotNull(message = "Chi nhanh kiem kho khong duoc de trong")
    private Long branchId;

    private Long createdById;

    @Valid
    private List<CheckNoteDetailRequest> details;

    @Data
    public static class CheckNoteDetailRequest {
        @NotNull(message = "ID bien the khong duoc de trong")
        private Long productVariantId;

        private String batchNumber;
        private String expiryDate;
        private String originalExpiryDate;
        private Integer quantityReal;
        private Integer quantityRejected;
        private Integer quantity;
        private Integer systemQuantity;
        private java.math.BigDecimal importPrice;
        private String note;
    }
}
