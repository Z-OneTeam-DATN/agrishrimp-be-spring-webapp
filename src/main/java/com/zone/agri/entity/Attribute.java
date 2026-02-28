package com.zone.agri.entity;

import com.zone.agri.entity.enums.AttributeStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Entity
@Table(name = "attributes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Attribute {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "name", length = 255)
    String name;

    @Column(name = "code", length = 50, unique = true)
    String code;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "ENUM('ACTIVE', 'INACTIVE')")
    AttributeStatus status;

    // Quan hệ 1-Nhiều với bảng giá trị (Tự động map danh sách giá trị lên)
    @OneToMany(mappedBy = "attribute", cascade = CascadeType.ALL, orphanRemoval = true)
    List<AttributeValue> attributeValues;
}