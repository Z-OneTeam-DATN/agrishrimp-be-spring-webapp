package com.zone.agri.entity;

import com.zone.agri.entity.enums.BrandStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import java.util.List;

@Entity
@Table(name = "brands")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Brand {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "name", length = 100)
    String name;

    @Column(name = "logo_url", length = 255)
    String logoUrl;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "ENUM('ACTIVE', 'INACTIVE')")
    BrandStatus status;

    // --- QUAN HỆ NGƯỢC (ONE-TO-MANY) ---

    @OneToMany(mappedBy = "brand", fetch = FetchType.LAZY)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    List<Product> products;

    // 1. Danh sách phiếu chuyển ĐI từ chi nhánh này
    @OneToMany(mappedBy = "fromBranch", fetch = FetchType.LAZY)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<InventoryTransfer> sentTransfers;

    // 2. Danh sách phiếu chuyển ĐẾN chi nhánh này
    @OneToMany(mappedBy = "toBranch", fetch = FetchType.LAZY)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<InventoryTransfer> receivedTransfers;
}