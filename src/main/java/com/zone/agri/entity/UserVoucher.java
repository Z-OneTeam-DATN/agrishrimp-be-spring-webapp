package com.zone.agri.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_vouchers", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "voucher_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserVoucher {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "usage_count")
    Integer usageCount;

    @Column(name = "is_saved")
    Boolean isSaved;

    @Column(name = "created_at")
    LocalDateTime createdAt;

    // --- KHÓA NGOẠI ---

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "voucher_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    Voucher voucher;

    // --- CONSTRUCTOR TÙY CHỈNH DÀNH CHO ORDER SERVICE ---
    // Giúp hàm new UserVoucher(user, voucher, 0, false) hoạt động không lỗi
    public UserVoucher(User user, Voucher voucher, Integer usageCount, Boolean isSaved) {
        this.user = user;
        this.voucher = voucher;
        this.usageCount = usageCount;
        this.isSaved = isSaved;
        this.createdAt = LocalDateTime.now();
    }
}