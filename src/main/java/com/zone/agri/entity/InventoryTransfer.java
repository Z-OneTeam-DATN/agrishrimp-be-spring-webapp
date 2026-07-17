package com.zone.agri.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.zone.agri.entity.enums.InventoryTransferStatus;
import com.zone.agri.entity.enums.TransferBusinessType;
import com.zone.agri.entity.enums.TransferSettlementStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "inventory_transfers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
public class InventoryTransfer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "transfer_code", length = 20)
    String transferCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 50)
    InventoryTransferStatus status;

    // --- CÁC TRƯỜNG BỔ SUNG TỪ GIAO DIỆN UI ---

    @Column(name = "description", columnDefinition = "TEXT")
    String description; // Lý do điều chuyển / Diễn giải

    @Column(name = "transfer_type", length = 50)
    String transferType; // Loại điều chuyển: BETWEEN_WAREHOUSES, INTERNAL

    @Column(name = "vehicle", length = 100)
    String vehicle; // Phương tiện vận chuyển (VD: Xe tải 29C...)

    @Column(name = "transporter", length = 100)
    String transporter; // Tài xế / Người giao

    @Column(name = "dispatch_order", length = 100)
    String dispatchOrder; // Lệnh điều động số

    @Column(name = "reference_code", length = 100)
    String referenceCode; // Tham chiếu chứng từ (VD: YCDC001)

    @Column(name = "priority", length = 20)
    String priority; // Độ ưu tiên: HIGH, NORMAL, LOW

    @Column(name = "transfer_date")
    LocalDateTime transferDate; // Ngày xuất thực tế / Dự kiến

    @Column(name = "deadline")
    LocalDateTime deadline; // Dự kiến nhận (Deadline)

    @Column(name = "total_value", precision = 38, scale = 2)
    BigDecimal totalValue; // Tổng giá trị hàng hóa theo giá vốn FIFO (quản trị kho)

    @Column(name = "total_quantity")
    Integer totalQuantity;

    @Column(name = "created_at")
    LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    User createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_branch_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    Branch createdByBranch;

    // --- NGHIỆP VỤ BÁN NỘI BỘ (INTERNAL_SALE) ---

    /**
     * Loại nghiệp vụ điều chuyển:
     * STOCK_TRANSFER = điều chuyển kho thuần (không phát sinh doanh thu / công nợ)
     * INTERNAL_SALE  = bán nội bộ giữa 2 chi nhánh hạch toán riêng
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "transfer_business_type", length = 30)
    TransferBusinessType transferBusinessType; // Mặc định: STOCK_TRANSFER

    /**
     * Tổng thành tiền điều chuyển nội bộ = Σ(unitTransferPrice × quantity) từng dòng hàng.
     * Chỉ có giá trị khi transferBusinessType = INTERNAL_SALE.
     */
    @Column(name = "transfer_amount", precision = 38, scale = 2)
    BigDecimal transferAmount;

    /**
     * Trạng thái thanh toán công nợ nội bộ (UNPAID / PARTIAL / PAID).
     * NULL khi STOCK_TRANSFER.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "settlement_status", length = 20)
    TransferSettlementStatus settlementStatus;

    /**
     * Phải thu nội bộ của kho xuất = transferAmount (bên xuất ghi doanh thu nội bộ).
     */
    @Column(name = "source_receivable_amount", precision = 38, scale = 2)
    BigDecimal sourceReceivableAmount;

    /**
     * Phải trả nội bộ của kho nhận = transferAmount (bên nhận ghi nợ mua nội bộ).
     */
    @Column(name = "dest_payable_amount", precision = 38, scale = 2)
    BigDecimal destPayableAmount;

    @Column(name = "paid_amount", precision = 38, scale = 2)
    BigDecimal paidAmount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_confirmed_by_user_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    User sourceConfirmedBy;

    @Column(name = "source_confirmed_at")
    LocalDateTime sourceConfirmedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by_user_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    User approvedBy;

    @Column(name = "approved_at")
    LocalDateTime approvedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shipped_by_user_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    User shippedBy;

    @Column(name = "shipped_at")
    LocalDateTime shippedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inspection_started_by_user_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    User inspectionStartedBy;

    @Column(name = "inspection_started_at")
    LocalDateTime inspectionStartedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "received_by_user_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    User receivedBy;

    @Column(name = "received_at")
    LocalDateTime receivedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "settled_by_user_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    User settledBy;

    @Column(name = "settled_at")
    LocalDateTime settledAt;

    // --- KHÓA NGOẠI ---

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_branch_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    Branch fromBranch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_branch_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    Branch toBranch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    User sender;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receiver_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    User receiver;

    @Builder.Default
    @OneToMany(mappedBy = "inventoryTransfer", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<InventoryTransferDetail> details = new ArrayList<>();

    public String getTransferCode() {
        return transferCode;
    }
}
