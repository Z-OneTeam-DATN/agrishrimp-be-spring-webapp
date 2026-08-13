package com.zone.agri.dto.response.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Kết quả so sánh 1 chỉ số giữa kỳ này và kỳ trước.
 *
 * Chỉ riêng con số % là KHÔNG đủ để hiển thị đúng: khi kỳ trước quá nhỏ (hoặc bằng 0, hoặc âm vì
 * lỗ), % nhảy lên hàng nghìn hoặc mất hết ý nghĩa toán học. Vì vậy DTO này trả kèm giá trị gốc của
 * cả 2 kỳ + cờ {@code comparable} để tầng hiển thị biết khi nào được phép in ra "%", khi nào phải
 * chuyển sang cách diễn đạt khác ("Mới", "gấp N lần", chênh lệch tuyệt đối).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetricChangeResponse {

    /** Giá trị kỳ này (hôm nay / khoảng đang chọn). */
    private BigDecimal current;

    /** Giá trị kỳ trước dùng làm mốc so sánh (hôm qua / khoảng liền trước cùng độ dài). */
    private BigDecimal previous;

    /** current - previous. Luôn có nghĩa, kể cả khi % không có nghĩa. */
    private BigDecimal changeAmount;

    /** Chỉ có nghĩa khi comparable = true. Đã chặn trần ±999.9% để UI không vỡ khung. */
    private double changePercent;

    /** True khi phần trăm tính được và có nghĩa (kỳ trước > 0). */
    private boolean comparable;

    /** True khi kỳ trước = 0 nhưng kỳ này đã phát sinh — tăng trưởng là vô hạn, hiển thị "Mới". */
    private boolean newBaseline;

    /** True khi kỳ trước âm (lỗ) — % tăng trưởng đảo dấu và gây hiểu lầm nên bị chặn. */
    private boolean negativeBaseline;

    /** UP | DOWN | FLAT — suy từ changeAmount, dùng để tô màu/chọn icon mà không phụ thuộc %. */
    private String direction;
}
