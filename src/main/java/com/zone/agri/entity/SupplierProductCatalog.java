package com.zone.agri.entity;

import com.zone.agri.entity.enums.SupplierProductCatalogStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "supplier_product_catalogs", uniqueConstraints = @UniqueConstraint(name = "uq_supplier_product_catalog", columnNames = {
        "supplier_id", "product_id" }))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SupplierProductCatalog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    Supplier supplier;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    Product product;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    SupplierProductCatalogStatus status;

    @Column(name = "note", columnDefinition = "TEXT")
    String note;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    LocalDateTime updatedAt;
}
