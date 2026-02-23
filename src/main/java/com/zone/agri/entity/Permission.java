package com.zone.agri.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.zone.agri.entity.enums.PermissionGroup;
import com.zone.agri.entity.enums.PermissionType;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "permissions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Permission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(nullable = false, length = 50)
    String name;

    @Column(nullable = false, unique = true, length = 100)
    String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "group_name", length = 50)
    PermissionGroup groupName;


    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    PermissionType type;

    @Column(name = "parent_id")
    Long parentId;
    // QUAN HỆ ONE OR MANY ROLE
    @ManyToMany(mappedBy = "permissions")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
            Set<Role> roles;

}