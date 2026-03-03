package com.zone.agri.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "handovers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Handover {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "code", length = 50, unique = true)
    String code; // Mã phiếu, VD: BG-20260303-001

    @Column(name = "carrier", length = 100)
    String carrier; // Đối tác vận chuyển (GHTK, J&T...)

    @Column(name = "total_orders")
    Integer totalOrders; // Tổng số kiện hàng

    @Column(name = "total_weight")
    Double totalWeight; // Tổng trọng lượng (kg)

    @Column(name = "total_cod", precision = 38, scale = 2)
    BigDecimal totalCod; // Tổng tiền thu hộ

    @Column(name = "status", length = 50)
    String status; // Trạng thái: WAITING (Chờ bưu tá lấy), COMPLETED (Đã giao bưu tá)

    @Column(name = "created_at")
    LocalDateTime createdAt;

    // --- KHÓA NGOẠI ---

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id")
    Branch branch; // Phiếu này của chi nhánh nào

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    User createdBy; // Ai là người bấm tạo phiếu này

    @OneToMany(mappedBy = "handover", fetch = FetchType.LAZY)
    List<SubOrder> subOrders; // Danh sách các đơn hàng trong phiếu này
}