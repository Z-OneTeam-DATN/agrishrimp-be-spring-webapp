package com.zone.agri.dto.response.transfer;

import com.zone.agri.entity.enums.InventoryTransferStatus;
import com.zone.agri.entity.enums.TransferBusinessType;
import com.zone.agri.entity.enums.TransferSettlementStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransferResponse {
    private Long id;
    private String transferCode;
    private InventoryTransferStatus status;
    private LocalDateTime createdAt;

    // Các thông tin ngày tháng
    private LocalDateTime transferDate;
    private LocalDateTime deadline;

    // Tuyến đường và tài xế
    private String fromBranchName;
    private String toBranchName;
    private String transporter;

    // Mức độ ưu tiên (HIGH, NORMAL, LOW)
    private String priority;

    // Số liệu
    private Integer totalQuantity;
    private long itemCount; // Số lượng mặt hàng (số dòng)
    private BigDecimal totalValue; // Tổng giá trị hàng theo giá vốn FIFO

    // Nghiệp vụ bán nội bộ
    private TransferBusinessType transferBusinessType;
    private TransferSettlementStatus settlementStatus;
    private BigDecimal transferAmount; // Tổng thành tiền nội bộ (chỉ có khi INTERNAL_SALE)
}
