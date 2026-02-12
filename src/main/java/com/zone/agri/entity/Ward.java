package com.zone.agri.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "wards")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Ward {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "code", length = 20, unique = true)
    String code;

    @Column(name = "name", length = 255)
    String name;

    @Column(name = "full_name", length = 255)
    String fullName;

    @Column(name = "created_at")
    LocalDateTime createdAt;

    // --- KHÓA NGOẠI (Thuộc về Huyện nào) ---
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "district_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    District district;

    // --- QUAN HỆ 1-N (Xã có nhiều địa chỉ user - tùy chọn) ---
    @OneToMany(mappedBy = "ward", fetch = FetchType.LAZY)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    List<UserAddress> userAddresses;
}