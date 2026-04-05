package com.zone.agri.entity;

import com.zone.agri.entity.enums.OrderStatus;
import com.zone.agri.entity.enums.PaymentMethod;
import com.zone.agri.entity.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "orders") // "order" là từ khóa SQL, nên để số nhiều hoặc thêm ngoặc kép nếu cần
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "code", length = 20, unique = true)
    String code;

    @Column(name = "shipping_code", length = 50)
    String shippingCode;

    @Column(name = "total_amount", precision = 38, scale = 2)
    BigDecimal totalAmount;

    @Column(name = "discount_amount", precision = 38, scale = 2)
    BigDecimal discountAmount;

    @Column(name = "final_amount", precision = 38, scale = 2)
    BigDecimal finalAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", columnDefinition = "ENUM('CASH', 'TRANSFER', 'COD', 'PAYOS')")
    PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", columnDefinition = "ENUM('UNPAID', 'PAID')")
    PaymentStatus paymentStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", columnDefinition = "ENUM('PENDING', 'AWAITING_PAYMENT', 'AWAITING_REPLENISHMENT', 'CONFIRMED', 'PROCESSING', 'READY_FOR_PICKUP', 'SHIPPING', 'COMPLETED', 'CANCELLED', 'RETURNED')")
    OrderStatus status;

    @Column(name = "created_at")
    LocalDateTime createdAt;

    @Column(name = "shipping_address", columnDefinition = "TEXT")
    String shippingAddress;

    // --- Trường cho luồng tách đơn thông minh ---

    @Column(name = "total_shipping_fee", precision = 38, scale = 2)
    BigDecimal totalShippingFee;

    @Column(name = "user_lat")
    Double userLat;

    @Column(name = "user_lng")
    Double userLng;

    @Column(name = "delivery_address", columnDefinition = "TEXT")
    String deliveryAddress;

    @Column(name = "note", columnDefinition = "TEXT")
    String note;

    @Column(name = "receiver_name", length = 100)
    String receiverName;

    @Column(name = "receiver_phone", length = 15)
    String receiverPhone;

    // --- payOS ---

    @Column(name = "payos_payment_link_id")
    String payosPaymentLinkId;

    @Column(name = "payos_checkout_url", length = 512)
    String payosCheckoutUrl;

    // --- KHÓA NGOẠI ---

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    Branch branch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    User user; // Entity User (đã có)

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "voucher_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    Voucher voucher;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pond_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    Pond pond; // Nếu chưa có class Pond, bạn có thể comment dòng này hoặc tạo class Pond placeholder.


    @OneToMany(mappedBy = "order", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    List<OrderItem> orderItems;

    @OneToMany(mappedBy = "order", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    List<SubOrder> subOrders;

    @OneToMany(mappedBy = "order", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    List<Review> reviews;
    }
