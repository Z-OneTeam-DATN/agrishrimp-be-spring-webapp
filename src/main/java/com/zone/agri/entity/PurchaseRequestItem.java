package com.zone.agri.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;

/**
 * Dòng hàng hóa trong Phiếu yêu cầu mua.
 * Theo dõi số lượng lũy kế đã giao / đã đạt QC / còn thiếu.
 */
@Entity
@Table(name = "purchase_request_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PurchaseRequestItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_request_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    PurchaseRequest purchaseRequest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_variant_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    ProductVariant productVariant;

    @Builder.Default
    @Column(name = "requested_qty")
    Integer requestedQty = 0;      // Số lượng yêu cầu mua ban đầu

    @Builder.Default
    @Column(name = "delivered_qty")
    Integer deliveredQty = 0;      // Tổng NCC đã giao (quantityReal lũy kế qua các phiếu nhập)

    @Builder.Default
    @Column(name = "accepted_qty")
    Integer acceptedQty = 0;       // Tổng đạt QC, đã cộng vào tồn kho bán được

    @Builder.Default
    @Column(name = "defective_qty")
    Integer defectiveQty = 0;      // Tổng hàng lỗi, cộng vào tồn kho lỗi

    @Builder.Default
    @Column(name = "remaining_qty")
    Integer remainingQty = 0;      // requestedQty - acceptedQty (còn thiếu)

    @Column(name = "unit_price", precision = 38, scale = 2)
    BigDecimal unitPrice;

    @Column(name = "note", columnDefinition = "TEXT")
    String note;
}
