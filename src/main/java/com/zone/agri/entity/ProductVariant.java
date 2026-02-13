package com.zone.agri.entity;

import com.zone.agri.entity.enums.VariantStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import java.math.BigDecimal;
import java.util.List;

@Entity
@Table(name = "product_variants")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ProductVariant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "sku", length = 50, unique = true)
    String sku;

    @Column(name = "price", precision = 38, scale = 2)
    BigDecimal price;

    @Column(name = "import_price", precision = 38, scale = 2)
    BigDecimal importPrice;

    @Column(name = "quantity")
    Integer quantity;

    @Column(name = "barcode", length = 50)
    String barcode;

    @Column(name = "image_url", length = 255)
    String imageUrl;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "ENUM('ACTIVE', 'INACTIVE')")
    VariantStatus status;

    @Column(name = "weight_value", precision = 38, scale = 2)
    BigDecimal weightValue;

    // --- KHÓA NGOẠI ---

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_form_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    ProductForm productForm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "packaging_type_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    PackagingType packagingType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unit_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    Unit unit;

    @OneToMany(mappedBy = "productVariant", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    List<ProductImage> productImages;


}