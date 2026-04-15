package com.zone.agri.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import java.math.BigDecimal;
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

    // --- GIÁ ĐIỀU CHUYỂN NỘI BỘ (chỉ có giá trị khi INTERNAL_SALE) ---

    /**
     * Đơn giá điều chuyển nội bộ do người dùng nhập.
     * Có thể khác giá vốn FIFO - đây là giá bán nội bộ giữa 2 chi nhánh.
     */
    @Column(name = "unit_transfer_price", precision = 38, scale = 2)
    BigDecimal unitTransferPrice;

    /**
     * Thành tiền điều chuyển nội bộ = unitTransferPrice × quantityRequested.
     * Được tính tự động khi tạo phiếu loại INTERNAL_SALE.
     */
    @Column(name = "total_transfer_price", precision = 38, scale = 2)
    BigDecimal totalTransferPrice;

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