package com.zone.agri.dto.response.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyBusinessResultsResponse {
    private BigDecimal todayRevenue;
    private BigDecimal yesterdayRevenue;
    private double revenueChangePercent;
    private boolean revenueIsNew;

    private BigDecimal todayProfit;
    private BigDecimal yesterdayProfit;
    private double profitChangePercent;
    private boolean profitIsNew;

    private long todayOrders;
    private long yesterdayOrders;
    private double orderChangePercent;
    private boolean orderIsNew;

    // Bản đầy đủ của 3 phép so sánh trên (kèm giá trị kỳ trước + cờ comparable) — trang tổng quan
    // dùng các trường này để chọn cách diễn đạt thay vì in thẳng % ra màn hình.
    private MetricChangeResponse revenueChange;
    private MetricChangeResponse profitChange;
    private MetricChangeResponse orderChange;

    // Chất lượng vận hành trong kỳ — đếm theo thời điểm SỰ KIỆN xảy ra (receivedAt/returnedAt/
    // cancelledAt), không phải createdAt của đơn, nên phản ánh đúng "hôm nay xử lý được gì" bất kể
    // đơn đó tạo từ lúc nào. Tầng hiển thị tự suy ra tỷ lệ % từ 3 số đếm này (xem ghi chú ở
    // MetricChangeResponse về lý do không tính sẵn % ở đây: mẫu số phụ thuộc cách chọn kết hợp
    // 3 số, để backend tính sẵn 1 kiểu sẽ ép UI theo đúng cách diễn giải đó).
    private long deliveredOrders;
    private long returnedOrders;
    private long cancelledOrders;
    private MetricChangeResponse deliveredChange;
    private MetricChangeResponse returnedChange;
    private MetricChangeResponse cancelledChange;
}
