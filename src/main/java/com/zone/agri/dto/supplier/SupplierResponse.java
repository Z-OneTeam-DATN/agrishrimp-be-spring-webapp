package com.zone.agri.dto.supplier;

import com.zone.agri.entity.Supplier;
import com.zone.agri.entity.enums.PaymentTerm;
import com.zone.agri.entity.enums.SupplierStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupplierResponse {

    private Long id;
    private String code;
    private String name;
    private String taxCode;

    private CategoryDto category;

    // Class con để định dạng dữ liệu danh mục trả về FE
    @Data
    @AllArgsConstructor
    public static class CategoryDto {
        private Long id;
        private String name;
    }

    // Thông tin liên hệ chính
    private String contactName;
    private String phone;
    private String email;

    // Địa chỉ
    private String addressDetail;
    private String provinceId;

    // Tài chính
    private PaymentTerm paymentTerm;
    private BigDecimal creditLimit;
    private Double discount;
    private BigDecimal currentDebt;

    // Ngân hàng
    private String bankAccountNumber;
    private String bankName;
    private String bankAccountHolder;

    // Trạng thái & Ghi chú
    private SupplierStatus status;
    private String note;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // --- Hàm tiện ích Map từ Entity sang Response ---
    public static SupplierResponse fromEntity(Supplier supplier) {
        return SupplierResponse.builder()
                .id(supplier.getId())
                .code(supplier.getCode())
                .name(supplier.getName())
                .taxCode(supplier.getTaxCode())

                .category(supplier.getCategory() != null
                        ? new CategoryDto(supplier.getCategory().getId(), supplier.getCategory().getName())
                        : null)

                .contactName(supplier.getContactName())
                .phone(supplier.getPhone())
                .email(supplier.getEmail())
                .addressDetail(supplier.getAddressDetail())
                .provinceId(supplier.getProvinceId())
                .paymentTerm(supplier.getPaymentTerm())
                .creditLimit(supplier.getCreditLimit())
                .discount(supplier.getDiscount())
                .currentDebt(supplier.getCurrentDebt())
                .bankAccountNumber(supplier.getBankAccountNumber())
                .bankName(supplier.getBankName())
                .bankAccountHolder(supplier.getBankAccountHolder())
                .status(supplier.getStatus())
                .note(supplier.getNote())
                .createdAt(supplier.getCreatedAt())
                .updatedAt(supplier.getUpdatedAt())
                .build();
    }
}