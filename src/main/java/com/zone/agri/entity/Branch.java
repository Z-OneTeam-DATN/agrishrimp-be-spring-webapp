package com.zone.agri.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.zone.agri.entity.enums.BranchStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
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

    @Column(name = "full_address", columnDefinition = "TEXT")
    String fullAddress;

    @Column(name = "map_display_name", columnDefinition = "TEXT")
    String mapDisplayName;

    @Column(name = "province_id")
    Integer provinceId;

    @Column(name = "province_name", length = 100)
    String provinceName;

    @Column(name = "district_id")
    Integer districtId;

    @Column(name = "district_name", length = 100)
    String districtName;

    @Column(name = "ward_id")
    Integer wardId;

    @Column(name = "ward_name", length = 100)
    String wardName;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    BranchStatus status;

    // --- Geo fields (dùng cho tìm chi nhánh gần nhất & GHN shipping) ---

    /** Vĩ độ — lấy từ Geocoding API sau khi tạo/sửa branch */
    @Column(name = "lat")
    Double lat;

    /** Kinh độ */
    @Column(name = "lng")
    Double lng;

    /** Mã phường/xã theo hệ thống GHN (string, khác wardId Integer) */
    @Column(name = "ward_code", length = 20)
    String wardCode;

//  KHÓA NGOẠI
    @OneToMany(mappedBy = "branch", fetch = FetchType.LAZY)
    @JsonIgnore
    private List<User> users;

    @OneToMany(mappedBy = "fromBranch", fetch = FetchType.LAZY)
    @JsonIgnore
    private List<InventoryTransfer> sentTransfers;

    @OneToMany(mappedBy = "toBranch", fetch = FetchType.LAZY)
    @JsonIgnore
    private List<InventoryTransfer> receivedTransfers;

}
