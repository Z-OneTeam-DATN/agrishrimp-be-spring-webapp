package com.zone.agri.entity;

import com.zone.agri.entity.enums.InventoryNoteStatus;
import com.zone.agri.entity.enums.InventoryNoteType;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "inventory_notes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class InventoryNote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "code", length = 20)
    String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", columnDefinition = "ENUM('IMPORT', 'EXPORT')")
    InventoryNoteType type;

    @Column(name = "reason", columnDefinition = "TEXT")
    String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", columnDefinition = "ENUM('PENDING', 'COMPLETED', 'CANCELLED')")
    InventoryNoteStatus status;

    @Column(name = "total_amount", precision = 38, scale = 2)
    BigDecimal totalAmount;

    @Column(name = "created_at")
    LocalDateTime createdAt;

    @Column(name = "note", columnDefinition = "TEXT")
    String note; // Lưu "Ghi chú đơn" từ UI

    @Column(name = "tags")
    String tags; // Lưu danh sách "Thẻ phân loại" (có thể lưu dạng chuỗi cách nhau bởi dấu phẩy)

    @Column(name = "deliverer")
    String deliverer; // Lưu tên "Nhân viên/Người giao hàng" từ UI

    @Column(name = "entry_date")
    LocalDateTime entryDate; // Lưu "Ngày hẹn giao" từ UI

    @Column(name = "payment_amount", precision = 38, scale = 2)
    BigDecimal paymentAmount; // Lưu "Thanh toán cho NCC" từ UI

    @Column(name = "debt_amount", precision = 38, scale = 2)
    BigDecimal debtAmount; // Lưu "Còn phải trả" từ UI (tổng tiền - thanh toán)

    // --- KHÓA NGOẠI ---

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id") // Chi nhánh thực hiện phiếu
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    Branch branch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    Supplier supplier;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "partner_branch_id") // Chi nhánh đối tác (trong trường hợp chuyển kho)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    Branch partnerBranch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by") // Người tạo phiếu
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    User createdBy;

    // --- QUAN HỆ 1-N (Chi tiết phiếu) ---
    @OneToMany(mappedBy = "inventoryNote", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @ToString.Exclude
    List<InventoryNoteDetail> details;
}