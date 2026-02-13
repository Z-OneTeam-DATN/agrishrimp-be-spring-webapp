package com.zone.agri.entity;

import com.zone.agri.entity.enums.AttributeStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

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

    @Column(name = "description", columnDefinition = "TEXT")
    String description;

    @Column(name = "value_list", columnDefinition = "TEXT")
    String valueList;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "ENUM('ACTIVE', 'INACTIVE')")
    AttributeStatus status;

}