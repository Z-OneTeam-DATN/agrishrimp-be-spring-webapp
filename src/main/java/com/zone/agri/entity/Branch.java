package com.zone.agri.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.zone.agri.entity.enums.BranchStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

import java.util.List;

@Entity
@Table(name = "branches")
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Branch extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "branch_code", length = 20)
    String branchCode;

    @Column(name = "branch_type", length = 20) // <--- THÊM MỚI
    String branchType;

    @Column(name = "name")
    String name;

    @Column(name = "phone", length = 15, unique = true) // Có đánh dấu UNI trong hình
    String phone;

    @Column(name = "email", length = 100)
    String email;

    @Column(name = "address_detail", columnDefinition = "TEXT")
    String addressDetail;

    @Column(name = "province_id")
    Integer provinceId;

    @Column(name = "district_id")
    Integer districtId;

    @Column(name = "ward_id")
    Integer wardId;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    BranchStatus status;


//  KHÓA NGOẠI
    @OneToMany(mappedBy = "branch", fetch = FetchType.LAZY)
    @JsonIgnore
    private List<User> users;

}