package com.zone.agri.entity;

import com.zone.agri.entity.enums.ProductFormStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Entity
@Table(name = "product_forms")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ProductForm {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "name", length = 100)
    String name;

    @Column(name = "code", length = 50)
    String code;

    // Type trong DB là TEXT
    @Column(name = "description", columnDefinition = "TEXT")
    String description;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "ENUM('ACTIVE', 'INACTIVE')")
    ProductFormStatus status;

    // --- KHÓA NGOẠI (ONE-TO-MANY) ---

//    @OneToMany(mappedBy = "productForm", fetch = FetchType.LAZY)
//    @ToString.Exclude
//    @EqualsAndHashCode.Exclude
//    List<ProductVariant> productVariants;
}