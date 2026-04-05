package com.zone.agri.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

@Entity
@Table(name = "sub_order_items")
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SubOrderItem extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "quantity", nullable = false)
    Integer quantity;

    @Column(name = "allocated_quantity")
    Integer allocatedQuantity;

    @Column(name = "missing_quantity")
    Integer missingQuantity;

    @Column(name = "unit_price", precision = 38, scale = 2, nullable = false)
    BigDecimal unitPrice;

    // --- KHÓA NGOẠI ---

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sub_order_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    SubOrder subOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_variant_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    ProductVariant productVariant;
}
