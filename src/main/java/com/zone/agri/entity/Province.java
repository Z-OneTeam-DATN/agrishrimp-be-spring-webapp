package com.zone.agri.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "provinces")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Province {

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

    // --- QUAN HỆ 1-N (Tỉnh có nhiều Quận/Huyện) ---
    @OneToMany(mappedBy = "province", fetch = FetchType.LAZY)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    List<District> districts;

    // --- QUAN HỆ 1-N (Tỉnh có nhiều địa chỉ user - tùy chọn) ---
    @OneToMany(mappedBy = "province", fetch = FetchType.LAZY)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    List<UserAddress> userAddresses;
}