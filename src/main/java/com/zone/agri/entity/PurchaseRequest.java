package com.zone.agri.entity;

import com.zone.agri.entity.enums.PurchaseRequestStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Phiếu yêu cầu mua hàng từ nhà cung cấp.
 * Một phiếu có thể có nhiều đợt giao hàng (GoodsReceipts / InventoryNote).
 */
@Entity
@Table(name = "purchase_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PurchaseRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "code", length = 30, unique = true)
    String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30)
    PurchaseRequestStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    Supplier supplier;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    Branch branch;

    @Column(name = "expected_delivery_date")
    LocalDateTime expectedDeliveryDate;

    @Column(name = "note", columnDefinition = "TEXT")
    String note;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    User createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    User approvedBy;

    @Column(name = "approved_at")
    LocalDateTime approvedAt;

    @Column(name = "sent_to_supplier_at")
    LocalDateTime sentToSupplierAt;

    @Column(name = "completed_at")
    LocalDateTime completedAt;

    // Tổng giá trị yêu cầu (requestedQty * unitPrice cho từng dòng)
    @Column(name = "total_amount", precision = 38, scale = 2)
    BigDecimal totalAmount;

    @OneToMany(mappedBy = "purchaseRequest", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @ToString.Exclude
    @Builder.Default
    List<PurchaseRequestItem> items = new ArrayList<>();

    // Các phiếu nhập thực tế (InventoryNote type=IMPORT) được liên kết với yêu cầu này
    @OneToMany(mappedBy = "purchaseRequest", fetch = FetchType.LAZY)
    @ToString.Exclude
    @Builder.Default
    List<InventoryNote> goodsReceipts = new ArrayList<>();
}
