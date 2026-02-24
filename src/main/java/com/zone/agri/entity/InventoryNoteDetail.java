package com.zone.agri.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "inventory_note_details")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)

public class InventoryNoteDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "quantity")
    Integer quantity;

    @Column(name = "price", precision = 38, scale = 2)
    BigDecimal price;

    @Column(name = "quantity_requested")
    Integer quantityRequested;

    @Column(name = "quantity_real")
    Integer quantityReal;

    @Column(name = "batch_number", length = 50)
    String batchNumber; // Khớp với "lotNumber" trong UI

    @Column(name = "expiry_date")
    LocalDateTime expiryDate; // Khớp với "expiryDate" trong UI

    @Column(name = "new_selling_price", precision = 38, scale = 2)
    BigDecimal newSellingPrice; // Khớp với "Giá bán mới" trong UI



    // --- KHÓA NGOẠI ---
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inventory_transfer_id", insertable = false, updatable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private InventoryTransfer inventoryTransfer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inventory_note_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    InventoryNote inventoryNote;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_variant_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    ProductVariant productVariant;
}