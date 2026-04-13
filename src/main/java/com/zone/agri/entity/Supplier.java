package com.zone.agri.entity;

import com.zone.agri.entity.enums.SupplierStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "suppliers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Supplier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    // FE: Mã NCC (tự sinh hoặc nhập) - FE hiển thị #NCC-001
    @Column(name = "code", length = 20, unique = true, nullable = false)
    String code;

    // FE: Tên công ty / Pháp nhân
    @Column(name = "name", length = 255, nullable = false)
    String name;

    // FE: Mã số thuế
    @Column(name = "tax_code", length = 50, unique = true)
    String taxCode;

    // --- THÔNG TIN LIÊN HỆ ---
    // FE: Họ và tên người đại diện
    @Column(name = "contact_name", length = 100)
    String contactName;

    // FE: Số điện thoại di động
    @Column(name = "phone", length = 20)
    String phone;

    // FE: Email liên hệ
    @Column(name = "email", length = 100)
    String email;

    // --- ĐỊA CHỈ ---
    // FE: Tỉnh / Thành phố (Lưu ID hoặc tên)
    @Column(name = "province_id", length = 50)
    String provinceId;

    // FE: Địa chỉ chi tiết
    @Column(name = "address_detail", columnDefinition = "TEXT")
    String addressDetail;

    // --- TRẠNG THÁI ---
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    SupplierStatus status;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    LocalDateTime updatedAt;

    // Quan hệ với đơn nhập hàng (InventoryNote)
    @OneToMany(mappedBy = "supplier", fetch = FetchType.LAZY)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    List<InventoryNote> inventoryNotes;

    // --- QUAN HỆ VỚI SẢN PHẨM ---
    @ManyToMany(mappedBy = "suppliers", fetch = FetchType.LAZY)
    @Builder.Default
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    java.util.Set<Product> products = new java.util.HashSet<>();
}