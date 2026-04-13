package com.zone.agri.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.zone.agri.entity.enums.InventoryTransferStatus;
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
    BigDecimal totalValue; // Tổng giá trị hàng hóa

    @Column(name = "total_quantity")
    Integer totalQuantity;

    @Column(name = "created_at")
    LocalDateTime createdAt;

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