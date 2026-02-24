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

    @Column(name = "sku", length = 50, unique = true, nullable = false)
    String sku;

    @Column(name = "unit", length = 50 )
     String unit;

    @Column(name = "barcode", length = 50)
    String barcode;

    @Column(name = "price", precision = 19, scale = 2)
    BigDecimal price;

    @Column(name = "import_price", precision = 19, scale = 2)
    BigDecimal importPrice;

    @Column(name = "wholesale_price", precision = 19, scale = 2)
    BigDecimal wholesalePrice;

    @Column(name = "quantity")
    Long quantity;

    @Column(name = "shipping_weight", precision = 19, scale = 3)
    BigDecimal shippingWeight;

    /** URL ảnh biến thể (lưu secure_url từ Cloudinary) */
    @Column(name = "image_url", length = 500)
    String imageUrl;

    /** Public ID Cloudinary – dùng để xóa / cập nhật ảnh sau này */
    @Column(name = "image_public_id", length = 255)
    String imagePublicId;

    @Column(name = "custom_specs", columnDefinition = "TEXT")
    String customSpecs;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    VariantStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    Product product;

    /** Thuộc tính động (màu sắc, v.v.) */
    @OneToMany(mappedBy = "sku", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    List<SKUAttributeValue> attributeValues;

    /** Bảng quy đổi đơn vị của biến thể này */
    @OneToMany(mappedBy = "variant", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    List<UnitConversion> unitConversions;
}
