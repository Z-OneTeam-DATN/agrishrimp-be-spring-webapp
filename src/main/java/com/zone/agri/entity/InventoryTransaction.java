package com.zone.agri.entity;

import com.zone.agri.entity.enums.TransactionType;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import java.time.LocalDateTime;

@Entity
@Table(name = "inventory_transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class InventoryTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "type")
    TransactionType type;

    @Column(name = "quantity_change")
    Integer quantityChange;

    @Column(name = "new_balance")
    Integer newBalance;

    @Column(name = "reference_code", length = 50)
    String referenceCode; // Mã đơn hàng hoặc mã phiếu kho liên quan

    @Column(name = "reason", length = 255)
    String reason;

    @Column(name = "created_at")
    LocalDateTime createdAt;

    // --- KHÓA NGOẠI ---

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inventory_note_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    InventoryNote inventoryNote;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inventory_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    Inventory inventory;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    User createdBy;
}