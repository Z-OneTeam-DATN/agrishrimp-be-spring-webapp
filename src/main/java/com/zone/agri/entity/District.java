package com.zone.agri.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "districts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class District {

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

    // --- KHÓA NGOẠI (Thuộc về Tỉnh nào) ---
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "province_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    Province province;

    // --- QUAN HỆ 1-N (Huyện có nhiều Xã/Phường) ---
    @OneToMany(mappedBy = "district", fetch = FetchType.LAZY)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    List<Ward> wards;

    // --- QUAN HỆ 1-N (Huyện có nhiều địa chỉ user - tùy chọn) ---
    @OneToMany(mappedBy = "district", fetch = FetchType.LAZY)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    List<UserAddress> userAddresses;
}