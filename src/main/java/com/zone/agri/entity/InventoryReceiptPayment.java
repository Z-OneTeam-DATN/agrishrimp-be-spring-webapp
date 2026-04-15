package com.zone.agri.entity;

import com.zone.agri.entity.enums.SupplierPaymentMethod;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "inventory_receipt_payments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class InventoryReceiptPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inventory_note_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    InventoryNote inventoryNote;

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    User createdBy;

    @Column(name = "payment_date")
    LocalDateTime paymentDate;

    @Column(name = "amount", precision = 38, scale = 2, nullable = false)
    BigDecimal amount;

    @Column(name = "remaining_debt_after", precision = 38, scale = 2, nullable = false)
    BigDecimal remainingDebtAfter;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", length = 50)
    SupplierPaymentMethod paymentMethod;

    @Column(name = "reference_code", length = 255)
    String referenceCode;

    @Column(name = "note", columnDefinition = "TEXT")
    String note;

    @Column(name = "created_at", nullable = false)
    LocalDateTime createdAt;
}
