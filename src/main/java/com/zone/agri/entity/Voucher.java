package com.zone.agri.entity;

import com.zone.agri.entity.enums.VoucherDiscountType;
import com.zone.agri.entity.enums.VoucherStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "vouchers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Voucher {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "code", length = 20, unique = true)
    String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", columnDefinition = "ENUM('PERCENT', 'FIXED')")
    VoucherDiscountType discountType;

    @Column(name = "value", precision = 38, scale = 2)
    BigDecimal value;

    // Thêm trường maxDiscount để hứng dữ liệu "Giảm tối đa (VNĐ)"
    @Column(name = "max_discount", precision = 38, scale = 2)
    BigDecimal maxDiscount;

    @Column(name = "max_usage_per_user")
    Integer maxUsagePerUser;

    @Column(name = "min_order_value", precision = 38, scale = 2)
    BigDecimal minOrderValue;

    @Column(name = "start_date")
    LocalDateTime startDate;

    @Column(name = "end_date")
    LocalDateTime endDate;

    @Column(name = "quantity")
    Integer quantity;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "ENUM('ACTIVE', 'INACTIVE', 'EXPIRED')")
    VoucherStatus status;

    @OneToMany(mappedBy = "voucher", fetch = FetchType.LAZY)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    List<Order> orders;

    @OneToMany(mappedBy = "voucher", fetch = FetchType.LAZY)
    @ToString.Exclude
    private List<UserVoucher> userVouchers;
}