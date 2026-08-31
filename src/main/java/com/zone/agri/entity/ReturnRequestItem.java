package com.zone.agri.entity;

import com.zone.agri.entity.enums.ReturnItemSourceType;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

@Entity
@Table(name = "return_request_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@FieldDefaults(level = AccessLevel.PRIVATE)
@EqualsAndHashCode(callSuper = true)
public class ReturnRequestItem extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", length = 40, nullable = false)
    ReturnItemSourceType sourceType;

    @Column(name = "source_item_id", nullable = false)
    Long sourceItemId;

    @Column(name = "product_variant_id")
    Long productVariantId;

    @Column(name = "sub_order_id")
    Long subOrderId;

    @Column(name = "product_name", length = 255, nullable = false)
    String productName;

    @Column(name = "variant_name", length = 255)
    String variantName;

    @Column(name = "sku", length = 80)
    String sku;

    @Column(name = "image_url", columnDefinition = "TEXT")
    String imageUrl;

    @Column(name = "quantity", nullable = false)
    Integer quantity;

    @Column(name = "ordered_quantity", nullable = false)
    Integer orderedQuantity;

    @Column(name = "unit_price", precision = 38, scale = 2, nullable = false)
    BigDecimal unitPrice;

    @Column(name = "refund_amount", precision = 38, scale = 2, nullable = false)
    BigDecimal refundAmount;

    @Column(name = "restock_quantity", nullable = false)
    Integer restockQuantity;

    @Column(name = "defective_quantity", nullable = false)
    Integer defectiveQuantity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "return_request_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    ReturnRequest returnRequest;
}
