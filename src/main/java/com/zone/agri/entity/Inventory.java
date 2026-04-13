package com.zone.agri.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "inventories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Inventory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Builder.Default
    @Column(name = "quantity")
    Integer quantity = 0; // Tồn kho có thể bán (Accepted)

    @Builder.Default
    @Column(name = "defective_quantity")
    Integer defectiveQuantity = 0; // Tồn kho hàng lỗi (Rejected) - Chờ xuất trả

    // --- QUẢN LÝ THEO LÔ VÀ GIÁ ---
    @Column(name = "batch_number", length = 50)
    String batchNumber;

    @Column(name = "import_price", precision = 38, scale = 2)
    BigDecimal importPrice;

    @Column(name = "expiry_date")
    LocalDateTime expiryDate;

    @Column(name = "shelf_location", length = 50)
    String shelfLocation;

    @Column(name = "last_receipt_date")
    LocalDateTime lastReceiptDate;

    @Column(name = "min_stock")
    Integer minStock;

    @Column(name = "last_checked_at")
    LocalDateTime lastCheckedAt;

    // --- KHÓA NGOẠI ---
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    Branch branch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_variant_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    ProductVariant productVariant;
}