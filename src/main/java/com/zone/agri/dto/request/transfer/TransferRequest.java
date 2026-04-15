package com.zone.agri.dto.request.transfer;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class TransferRequest {
    @NotNull(message = "Chi nhánh xuất không được để trống")
    private Long fromBranchId;

    @NotNull(message = "Chi nhánh nhận không được để trống")
    private Long toBranchId;

    // Các trường mới từ form UI
    @NotBlank(message = "Loại chuyển hàng không được để trống")
    private String transferType;

    /**
     * Loại nghiệp vụ: STOCK_TRANSFER (mặc định) hoặc INTERNAL_SALE.
     * Khi INTERNAL_SALE, mỗi dòng hàng phải có unitTransferPrice.
     */
    private String transferBusinessType; // Nếu null, mặc định STOCK_TRANSFER

    private String description;
    private String transporter;
    private String vehicle;
    private String dispatchOrder;
    private String referenceCode;

    @NotBlank(message = "Mức độ ưu tiên không được để trống")
    private String priority;

    @NotNull(message = "Ngày chuyển không được để trống")
    private LocalDateTime transferDate;

    private LocalDateTime deadline;

    @NotEmpty(message = "Danh sách sản phẩm không được để trống")
    @Valid
    private List<TransferItemRequest> items;
}
