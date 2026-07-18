package com.zone.agri.entity;

import com.zone.agri.entity.enums.DriverStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "drivers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Driver extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "code", length = 20, unique = true, nullable = false)
    String code;

    @Column(name = "full_name", length = 255, nullable = false)
    String fullName;

    @Column(name = "phone", length = 20)
    String phone;

    @Column(name = "email", length = 100)
    String email;

    @Column(name = "id_card", length = 50)
    String idCard;

    @Column(name = "license_number", length = 50)
    String licenseNumber;

    @Column(name = "license_class", length = 20)
    String licenseClass;

    @Column(name = "avatar_url", columnDefinition = "TEXT")
    String avatarUrl;

    @Column(name = "license_image_url", columnDefinition = "TEXT")
    String licenseImageUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    @Builder.Default
    DriverStatus status = DriverStatus.ACTIVE;

    @Column(name = "vehicle_number", length = 50)
    String vehicleNumber;

    @Column(name = "vehicle_type", length = 100)
    String vehicleType;
}
