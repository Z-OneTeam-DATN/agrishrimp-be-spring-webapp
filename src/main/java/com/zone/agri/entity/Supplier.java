package com.zone.agri.entity;

import com.zone.agri.entity.enums.SupplierStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "suppliers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Supplier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "code", length = 20, unique = true)
    String code;

    @Column(name = "name", length = 255)
    String name;

    @Column(name = "phone", length = 10)
    String phone;

    @Column(name = "email", length = 100)
    String email;


    @Column(name = "address", columnDefinition = "TEXT")
    String address;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "ENUM('ACTIVE', 'INACTIVE')")
    SupplierStatus status;

    @Column(name = "created_at")
    LocalDateTime createdAt;


    @OneToMany(mappedBy = "supplier", fetch = FetchType.LAZY)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    List<InventoryNote> inventoryNotes;

}