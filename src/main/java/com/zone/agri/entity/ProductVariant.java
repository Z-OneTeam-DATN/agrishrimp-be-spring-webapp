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

    @Column(name = "barcode", length = 50)
    String barcode;

    @Column(name = "price", precision = 38, scale = 2)
    BigDecimal price;

    @Column(name = "import_price", precision = 38, scale = 2)
    BigDecimal importPrice;

    @Column(name = "wholesale_price", precision = 38, scale = 2)
    BigDecimal wholesalePrice;

    @Column(name = "quantity")
    Integer quantity;

    @Column(name = "weight_value", precision = 38, scale = 2)
    BigDecimal weightValue;

    @Column(name = "net_weight_unit", length = 20)
    String netWeightUnit;

    @Column(name = "shipping_weight", precision = 38, scale = 2)
    BigDecimal shippingWeight;

    @Column(name = "image_url", length = 255)
    String imageUrl;

    @Column(name = "custom_specs", columnDefinition = "TEXT")
    String customSpecs;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "ENUM('ACTIVE', 'INACTIVE')")
    VariantStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    Product product;

    // --- MỚI: DANH SÁCH THUỘC TÍNH ĐỘNG CỦA BIẾN THỂ NÀY ---
    @OneToMany(mappedBy = "variant", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    List<VariantAttribute> attributes;
}