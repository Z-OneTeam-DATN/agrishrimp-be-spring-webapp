package com.zone.agri.dto.request.voucher;

import com.zone.agri.entity.enums.VoucherDiscountType;
import com.zone.agri.entity.enums.VoucherStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
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
public class VoucherRequest {
    @NotBlank(message = "Mã voucher không được để trống")
    private String code;

    @NotBlank(message = "Tên chương trình voucher không được để trống")
    private String title;

    @NotNull(message = "Loại giảm giá không được để trống")
    private VoucherDiscountType discountType;

    // ĐÃ ĐỔI THÀNH value
    @NotNull(message = "Giá trị giảm giá không được để trống")
    @Min(value = 0, message = "Giá trị giảm giá phải lớn hơn hoặc bằng 0")
    private BigDecimal value;

    @Min(value = 0, message = "Giảm tối đa phải lớn hơn hoặc bằng 0")
    private BigDecimal maxDiscount;

    // ĐÃ ĐỔI THÀNH maxUsagePerUser
    @NotNull(message = "Số lần sử dụng tối đa mỗi người không được để trống")
    @Positive(message = "Số lần sử dụng tối đa phải lớn hơn 0")
    private Integer maxUsagePerUser;

    @NotNull(message = "Giá trị đơn hàng tối thiểu không được để trống")
    @Min(value = 0, message = "Giá trị đơn hàng tối thiểu phải lớn hơn hoặc bằng 0")
    private BigDecimal minOrderValue;

    @NotNull(message = "Ngày bắt đầu không được để trống")
    private LocalDateTime startDate;

    @NotNull(message = "Ngày kết thúc không được để trống")
    private LocalDateTime endDate;

    @NotNull(message = "Số lượng không được để trống")
    @Positive(message = "Số lượng phải lớn hơn 0")
    private Integer quantity;

    private VoucherStatus status;
}