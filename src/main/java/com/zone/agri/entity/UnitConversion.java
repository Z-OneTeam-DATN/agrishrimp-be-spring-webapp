package com.zone.agri.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import java.math.BigDecimal;

@Entity
@Table(
    name = "unit_conversions",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_unit_conversion_variant_from_to",
        columnNames = {"variant_id", "from_unit", "to_unit"}
    )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UnitConversion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    /** Đơn vị gốc (VD: "Hộp") */
    @Column(name = "from_unit", nullable = false, length = 50)
    String fromUnit;

    /** Đơn vị đích (VD: "Viên") */
    @Column(name = "to_unit", nullable = false, length = 50)
    String toUnit;

    /** Tỉ lệ: 1 fromUnit = rate * toUnit */
    @Column(name = "rate", nullable = false, precision = 19, scale = 6)
    BigDecimal rate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    ProductVariant variant;
}
