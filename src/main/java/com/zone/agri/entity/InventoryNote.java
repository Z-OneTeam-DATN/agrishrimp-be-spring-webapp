package com.zone.agri.entity;

import com.zone.agri.entity.enums.InventoryCheckWorkflowStatus;
import com.zone.agri.entity.enums.InventoryNoteStatus;
import com.zone.agri.entity.enums.InventoryNoteType;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "inventory_notes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class InventoryNote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "code", length = 20)
    String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "type")
    InventoryNoteType type;

    @Column(name = "reason", columnDefinition = "TEXT")
    String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 50)
    InventoryNoteStatus status;

    @Column(name = "total_amount", precision = 38, scale = 2)
    BigDecimal totalAmount;

    @Column(name = "created_at")
    LocalDateTime createdAt;

    @Column(name = "note", columnDefinition = "TEXT")
    String note;

    @Column(name = "tags")
    String tags;

    @Column(name = "deliverer")
    String deliverer;

    @Column(name = "entry_date")
    LocalDateTime entryDate;

    @Column(name = "payment_amount", precision = 38, scale = 2)
    BigDecimal paymentAmount;

    @Column(name = "debt_amount", precision = 38, scale = 2)
    BigDecimal debtAmount;

    @Column(name = "shipping_address", columnDefinition = "TEXT")
    String shippingAddress;

    @Column(name = "check_type", length = 50)
    String checkType;

    @Column(name = "check_date")
    LocalDateTime checkDate;

    @Column(name = "checked_by", length = 255)
    String checkedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "check_workflow_status", length = 50)
    InventoryCheckWorkflowStatus checkWorkflowStatus;

    @Column(name = "check_submitted_at")
    LocalDateTime checkSubmittedAt;

    @Column(name = "check_approved_at")
    LocalDateTime checkApprovedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "check_approved_by")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    User checkApprovedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    Branch branch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    Supplier supplier;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "partner_branch_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    Branch partnerBranch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    User createdBy;

    @OneToMany(mappedBy = "inventoryNote", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    List<InventoryNoteDetail> details;

    // Liên kết với Phiếu yêu cầu mua (nếu phiếu nhập này thuộc về một yêu cầu mua)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_request_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    PurchaseRequest purchaseRequest;
}
