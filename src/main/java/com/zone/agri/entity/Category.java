package com.zone.agri.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.zone.agri.entity.enums.CategoryStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Entity
@Table(name = "category")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "name", length = 255,unique = true)
    String name;

    @Column(name = "slug", length = 180, unique = true)
    String slug;

    @Column(name = "image_url", columnDefinition = "TEXT")
    String imageUrl;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "ENUM('ACTIVE', 'INACTIVE')")
    CategoryStatus status;


    // parent_id trỏ về chính bảng category
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    Category parent;

    //lấy danh sách con (Sub-categories)
    @JsonIgnore  // phá vòng lặp: Category → children → parent → children → ...
    @OneToMany(mappedBy = "parent", fetch = FetchType.LAZY)
    @ToString.Exclude
    List<Category> children;

}
