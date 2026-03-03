package com.zone.agri.entity;

import com.zone.agri.entity.enums.OrderStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.util.List;

@Entity
@Table(name = "sub_orders")
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SubOrder extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    OrderStatus status;

    @Column(name = "subtotal", precision = 38, scale = 2)
    BigDecimal subtotal;

    @Column(name = "shipping_fee", precision = 38, scale = 2)
    BigDecimal shippingFee;

    /** Ước tính ngày giao hàng ("2025-01-10T15:00:00+07:00" từ GHN hoặc "2-3 ngày") */
    @Column(name = "estimated_days", length = 100)
    String estimatedDays;

    /** Đơn vị vận chuyển: "GHN", "GHTK", ... */
    @Column(name = "carrier", length = 20)
    String carrier;

    /** Mã đơn bên đơn vị vận chuyển */
    @Column(name = "carrier_order_id", length = 100)
    String carrierOrderId;

    // --- KHÓA NGOẠI ---

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    Branch branch;

    @OneToMany(mappedBy = "subOrder", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    List<SubOrderItem> items;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "handover_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    Handover handover;

    @Column(name = "tracking_code", length = 100)
    String trackingCode;
}
