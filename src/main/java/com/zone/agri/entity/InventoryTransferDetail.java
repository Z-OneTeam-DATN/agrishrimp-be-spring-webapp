package com.zone.agri.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Entity
@Table(name = "inventory_transfer_details")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class InventoryTransferDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "quantity_requested")
    Integer quantityRequested; // Số lượng yêu cầu ban đầu (Expected Qty)

    @Column(name = "quantity_real")
    Integer quantityReal; // Tổng số lượng thực tế nhận được (Actual Qty)

    @Column(name = "quantity_accepted")
    Integer quantityAccepted; // Số lượng đạt chuẩn (Accepted Qty)

    @Column(name = "quantity_rejected")
    Integer quantityRejected; // Số lượng lỗi (Rejected Qty)

    @Column(name = "note", columnDefinition = "TEXT")
    String note; // Ghi chú từng dòng sản phẩm

    // --- KHÓA NGOẠI ---
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transfer_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @JsonManagedReference
    InventoryTransfer inventoryTransfer; // Trỏ về phiếu tổng

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    ProductVariant productVariant; // Sửa tên biến thành productVariant để Service nhận diện được
}