package com.zone.agri.entity;

import com.zone.agri.entity.enums.PaymentTerm;
import com.zone.agri.entity.enums.SupplierStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id") // Lưu ID của danh mục vào bảng Supplier
    private Category category;

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

    // --- TÀI CHÍNH & THANH TOÁN ---
    // FE: Chu kỳ thanh toán (Immediate, Net15, Net30...)
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_term", length = 50)
    PaymentTerm paymentTerm;

    // FE: Hạn mức công nợ tối đa
    @Column(name = "credit_limit", precision = 19, scale = 2)
    BigDecimal creditLimit;

    // FE: Chiết khấu (%)
    @Column(name = "discount")
    Double discount;

    // FE: Công nợ hiện tại (Hiển thị ở bảng danh sách)
    @Column(name = "current_debt", precision = 19, scale = 2)
    BigDecimal currentDebt;

    // --- NGÂN HÀNG ---
    @Column(name = "bank_account_number", length = 50)
    String bankAccountNumber;

    @Column(name = "bank_name", length = 100)
    String bankName;

    @Column(name = "bank_account_holder", length = 100)
    String bankAccountHolder;

    // --- TRẠNG THÁI & GHI CHÚ ---
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    SupplierStatus status;

    @Column(name = "note", columnDefinition = "TEXT")
    String note;

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
}