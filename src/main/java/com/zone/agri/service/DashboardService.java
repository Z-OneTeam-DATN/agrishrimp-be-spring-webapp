package com.zone.agri.service;

import com.zone.agri.common.AuthUtils;
import com.zone.agri.dto.response.dashboard.*;
import com.zone.agri.dto.response.user.UserDetail;
import com.zone.agri.entity.Order;
import com.zone.agri.entity.User;
import com.zone.agri.entity.InventoryNote;
import com.zone.agri.entity.SubOrder;
import com.zone.agri.entity.enums.OrderStatus;
import com.zone.agri.entity.enums.UserStatus;
import com.zone.agri.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final OrderRepository orderRepository;
    private final SubOrderRepository subOrderRepository; // Thêm SubOrderRepository
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final InventoryNoteRepository inventoryNoteRepository;
    private final InventoryTransactionRepository inventoryTransactionRepository;
    private final VisitService visitService;

    @Value("${dashboard.low-stock-threshold:10}")
    private int lowStockThreshold;

    private BigDecimal getSafeBigDecimal(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private Long resolveBranchId(Long requestBranchId) {
        return AuthUtils.resolveRequestedOrUserBranch(requestBranchId, "DASHBOARD_VIEW");
    }

    // Phân bổ giảm giá (voucher) của Order cha xuống từng SubOrder theo tỉ lệ subtotal,
    // vì discountAmount chỉ được lưu ở cấp Order chứ không tách theo chi nhánh.
    // Giống hệt logic allocateDiscount trong FinancialService để số liệu khớp giữa các báo cáo.
    private BigDecimal allocateDiscount(BigDecimal subtotal, BigDecimal orderSubtotal, BigDecimal orderDiscount) {
        if (subtotal.compareTo(BigDecimal.ZERO) <= 0
                || orderSubtotal.compareTo(BigDecimal.ZERO) <= 0
                || orderDiscount.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return orderDiscount.multiply(subtotal)
                .divide(orderSubtotal, 2, java.math.RoundingMode.HALF_UP);
    }

    private BigDecimal netSubOrderRevenue(SubOrderRepository.DashboardRevenueRow row) {
        BigDecimal subtotal = getSafeBigDecimal(row.getSubtotal());
        BigDecimal shippingFee = getSafeBigDecimal(row.getShippingFee());
        BigDecimal allocatedDiscount = allocateDiscount(
                subtotal, getSafeBigDecimal(row.getOrderSubtotal()), getSafeBigDecimal(row.getOrderDiscountAmount()));
        return subtotal.add(shippingFee).subtract(allocatedDiscount);
    }

    // Doanh thu thật (đã trừ giảm giá, đã gồm ship) = đơn hàng cũ không tách chi nhánh (Order.finalAmount)
    // + đơn đã tách chi nhánh (SubOrder, giảm giá phân bổ theo tỉ lệ). Dùng chung cho cả xem theo
    // 1 chi nhánh lẫn xem toàn hệ thống để 2 số liệu luôn khớp nhau (branchId = null nghĩa là không lọc).
    private BigDecimal sumNetRevenue(LocalDateTime start, LocalDateTime end, Long branchId) {
        BigDecimal legacyRevenue = getSafeBigDecimal(orderRepository.sumLegacyRevenue(start, end, branchId));
        BigDecimal subOrderRevenue = subOrderRepository.findRevenueRows(start, end, branchId).stream()
                .map(this::netSubOrderRevenue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return legacyRevenue.add(subOrderRevenue);
    }

    // Lấy doanh thu toàn bộ lịch sử [start, now] CHỈ 1 LẦN rồi suy ra "tính đến now" và "tính đến
    // 1 mốc trước đó" (asOf) từ cùng 1 tập dữ liệu — tránh quét lại lịch sử đầy đủ lần thứ 2 trên
    // DB. Quan trọng vì DB chạy qua SSH tunnel từ xa, sumNetRevenue(start=năm 2000) gọi 2 lần liền
    // (hôm nay + hôm qua) từng làm getStats() chậm hẳn do nhân đôi round-trip cho phần nặng nhất.
    private BigDecimal[] sumNetRevenueNowAndAsOf(
            LocalDateTime start, LocalDateTime now, LocalDateTime asOf, Long branchId) {
        BigDecimal totalNow = BigDecimal.ZERO;
        BigDecimal totalAsOf = BigDecimal.ZERO;

        for (OrderRepository.LegacyRevenueRow row : orderRepository.findLegacyRevenueRows(start, now, branchId)) {
            BigDecimal amount = getSafeBigDecimal(row.getFinalAmount());
            totalNow = totalNow.add(amount);
            if (row.getCreatedAt() != null && !row.getCreatedAt().isAfter(asOf)) {
                totalAsOf = totalAsOf.add(amount);
            }
        }

        for (SubOrderRepository.DashboardRevenueRow row : subOrderRepository.findRevenueRows(start, now, branchId)) {
            BigDecimal amount = netSubOrderRevenue(row);
            totalNow = totalNow.add(amount);
            if (row.getCreatedAt() != null && !row.getCreatedAt().isAfter(asOf)) {
                totalAsOf = totalAsOf.add(amount);
            }
        }

        return new BigDecimal[] { totalNow, totalAsOf };
    }

    // 2.1 TỔNG QUAN CHỈ SỐ
    public DashboardStatsResponse getStats(Long branchId) {
        Long finalBranchId = resolveBranchId(branchId);

        LocalDateTime startOfTime = LocalDateTime.of(2000, 1, 1, 0, 0);
        LocalDateTime now = LocalDateTime.now();
        // Mốc "hôm qua" cho các số luỹ kế (Tổng...) — so cuối hôm qua với hiện tại, khác với dòng
        // "hôm nay/hôm qua" ở KẾT QUẢ KINH DOANH NGÀY vốn so 2 khoảng phát sinh riêng biệt trong ngày.
        LocalDateTime yesterdayEnd = LocalDate.now().minusDays(1).atTime(java.time.LocalTime.MAX);

        long totalOrders = countTotalOrders(finalBranchId);
        long ordersAsOfYesterday = countTotalOrdersBefore(yesterdayEnd, finalBranchId);
        // Doanh thu dùng chung 1 công thức (đơn cũ + đơn đã tách chi nhánh, giảm giá phân bổ theo tỉ lệ)
        // cho cả 2 nhánh trên, để "Tổng doanh thu" của 1 chi nhánh cụ thể cộng dồn đúng khớp với
        // "Tổng doanh thu" khi xem toàn hệ thống. Chỉ fetch dữ liệu lịch sử 1 lần (xem hàm dưới).
        BigDecimal[] revenueSnapshot = sumNetRevenueNowAndAsOf(startOfTime, now, yesterdayEnd, finalBranchId);
        BigDecimal totalRevenue = revenueSnapshot[0];
        BigDecimal revenueAsOfYesterday = revenueSnapshot[1];

        long totalCustomers = userRepository.countCustomers(finalBranchId);
        long customersAsOfYesterday = userRepository.countCustomersBefore(finalBranchId, yesterdayEnd);
        long totalProducts = productRepository.countActiveProducts();

        MetricChangeResponse revenueChange = buildChange(totalRevenue, revenueAsOfYesterday);
        MetricChangeResponse ordersChange = buildChange(totalOrders, ordersAsOfYesterday);
        MetricChangeResponse customersChange = buildChange(totalCustomers, customersAsOfYesterday);

        return DashboardStatsResponse.builder()
                .totalOrders(totalOrders)
                .totalRevenue(totalRevenue)
                .totalCustomers(totalCustomers)
                .totalProducts(totalProducts)
                .revenueChangePercent(revenueChange.getChangePercent())
                .revenueIsNew(revenueChange.isNewBaseline())
                .ordersChangePercent(ordersChange.getChangePercent())
                .ordersIsNew(ordersChange.isNewBaseline())
                .customersChangePercent(customersChange.getChangePercent())
                .customersIsNew(customersChange.isNewBaseline())
                .revenueChange(revenueChange)
                .ordersChange(ordersChange)
                .customersChange(customersChange)
                .build();
    }

    public CustomerInsightsResponse getCustomerInsights(Long branchId) {
        Long finalBranchId = resolveBranchId(branchId);
        LocalDateTime startOfMonth = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        LocalDateTime now = LocalDateTime.now();

        // Lượt truy cập là traffic toàn site (tự đo qua middleware, không phải GA4 thật) nên không
        // lọc theo chi nhánh — mọi chi nhánh cùng nhìn thấy 1 con số traffic storefront chung.
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        VisitService.VisitInsights visitInsights = visitService.getInsights(todayStart, now);

        return CustomerInsightsResponse.builder()
                .totalCustomers(userRepository.countCustomers(finalBranchId))
                .activeCustomers(userRepository.countCustomersByStatus(finalBranchId, UserStatus.ACTIVE))
                .newCustomersThisMonth(userRepository.countCustomersCreatedBetween(finalBranchId, startOfMonth, now))
                .todayVisitors(visitInsights.visitors())
                .todayPageViews(visitInsights.pageViews())
                .build();
    }

    // Trần hiển thị của % tăng trưởng. Khi mốc so sánh rất nhỏ (bán được 1 đơn hôm qua, 30 đơn
    // hôm nay) thì % thật là +2900% — đúng về toán nhưng vô nghĩa khi đọc và làm vỡ khung thẻ số.
    // Vượt trần thì UI chuyển sang cách nói "gấp N lần", nên ở đây chỉ cần chặn để số không phình.
    private static final double MAX_CHANGE_PERCENT = 999.9;

    /**
     * Quy tắc so sánh kỳ này với kỳ trước — dùng chung cho MỌI chỉ số của trang tổng quan.
     *
     * <p>Có 3 trường hợp % không được phép hiển thị vì sai nghiệp vụ:
     * <ul>
     *   <li>kỳ trước = 0, kỳ này > 0 → tăng trưởng vô hạn, không phải "+100%" → newBaseline ("Mới");</li>
     *   <li>kỳ trước = 0, kỳ này = 0 → không có gì để so → FLAT, 0%;</li>
     *   <li>kỳ trước < 0 (kỳ trước lỗ) → công thức (nay-trước)/trước đảo dấu: lỗ ít đi lại ra số âm,
     *       nên chặn lại và để UI hiển thị chênh lệch tuyệt đối thay vì %.</li>
     * </ul>
     * Chỉ khi kỳ trước > 0 thì % mới có nghĩa; khi đó vẫn kẹp trong ±{@value #MAX_CHANGE_PERCENT}%.
     */
    private MetricChangeResponse buildChange(BigDecimal current, BigDecimal previous) {
        BigDecimal safeCurrent = getSafeBigDecimal(current);
        BigDecimal safePrevious = getSafeBigDecimal(previous);
        BigDecimal changeAmount = safeCurrent.subtract(safePrevious);

        boolean hasPositiveBaseline = safePrevious.compareTo(BigDecimal.ZERO) > 0;
        boolean negativeBaseline = safePrevious.compareTo(BigDecimal.ZERO) < 0;

        double percent = 0.0;
        if (hasPositiveBaseline) {
            double raw = changeAmount
                    .divide(safePrevious, 4, java.math.RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .doubleValue();
            percent = Math.max(-MAX_CHANGE_PERCENT, Math.min(MAX_CHANGE_PERCENT, raw));
        }

        return MetricChangeResponse.builder()
                .current(safeCurrent)
                .previous(safePrevious)
                .changeAmount(changeAmount)
                .changePercent(percent)
                .comparable(hasPositiveBaseline)
                .newBaseline(!hasPositiveBaseline && !negativeBaseline
                        && safeCurrent.compareTo(BigDecimal.ZERO) > 0)
                .negativeBaseline(negativeBaseline)
                .direction(resolveDirection(changeAmount.signum()))
                .build();
    }

    private MetricChangeResponse buildChange(long current, long previous) {
        return buildChange(BigDecimal.valueOf(current), BigDecimal.valueOf(previous));
    }

    private String resolveDirection(int signum) {
        if (signum > 0) {
            return "UP";
        }
        return signum < 0 ? "DOWN" : "FLAT";
    }

    // Bộ đếm chất lượng vận hành trong 1 khoảng thời gian: bao nhiêu đơn giao thành công, bị hoàn
    // (giao thất bại phải trả về kho), bị huỷ. Cả 3 đều đếm theo thời điểm sự kiện xảy ra (không
    // phải createdAt) — xem ghi chú ở OrderRepository#countDeliveredOrders.
    private record OrderQualityCounts(long delivered, long returned, long cancelled) {
    }

    // Đơn cũ (chưa tách chi nhánh) + đơn đã tách chi nhánh đều phải cộng dồn ở CẢ 2 chế độ xem —
    // trước đây chọn 1 chi nhánh cụ thể chỉ đếm SubOrder, bỏ sót hoàn toàn đơn cũ của chi nhánh đó
    // (dữ liệu hiện tại gần như 100% là đơn cũ nên số liệu bị hụt rất nhiều khi lọc theo chi nhánh).
    private OrderQualityCounts collectOrderQuality(LocalDateTime start, LocalDateTime end, Long branchId) {
        return new OrderQualityCounts(
                orderRepository.countDeliveredOrders(start, end, branchId)
                        + subOrderRepository.countDeliveredByBranchId(start, end, branchId),
                orderRepository.countReturnedOrders(start, end, branchId)
                        + subOrderRepository.countReturnedByBranchId(start, end, branchId),
                orderRepository.countCancelledOrders(start, end, branchId)
                        + subOrderRepository.countCancelledByBranchId(start, end, branchId));
    }

    // Tổng số đơn (đếm gộp đơn cũ + đơn đã tách chi nhánh) — dùng ở cả "Tổng đơn hàng" luỹ kế và
    // "Đơn hàng" phát sinh trong kỳ, cho cả 2 chế độ xem chi nhánh.
    private long countTotalOrders(Long branchId) {
        return orderRepository.countAllOrdersExceptCancelled(branchId)
                + subOrderRepository.countAllByBranchIdExceptCancelled(branchId);
    }

    private long countTotalOrdersBefore(LocalDateTime endDate, Long branchId) {
        return orderRepository.countAllOrdersExceptCancelledBefore(endDate, branchId)
                + subOrderRepository.countAllByBranchIdExceptCancelledBefore(endDate, branchId);
    }

    private long countSuccessOrdersMerged(LocalDateTime start, LocalDateTime end, Long branchId) {
        return orderRepository.countSuccessOrders(start, end, branchId)
                + subOrderRepository.countSuccessByBranchId(start, end, branchId);
    }

    private long countByStatusMerged(OrderStatus status, Long branchId) {
        return orderRepository.countByStatus(status, branchId)
                + subOrderRepository.countByStatusAndBranchId(status, branchId);
    }

    private MonthlyBusinessResultsResponse buildBusinessResultsResponse(
            String periodKey,
            LocalDateTime currentStart,
            LocalDateTime currentEnd,
            LocalDateTime previousStart,
            LocalDateTime previousEnd,
            Long finalBranchId) {
        BigDecimal currentRevenue = sumNetRevenue(currentStart, currentEnd, finalBranchId);
        BigDecimal previousRevenue = sumNetRevenue(previousStart, previousEnd, finalBranchId);

        BigDecimal currentCost = getSafeBigDecimal(orderRepository.sumTotalCost(currentStart, currentEnd, finalBranchId));
        BigDecimal previousCost = getSafeBigDecimal(orderRepository.sumTotalCost(previousStart, previousEnd, finalBranchId));

        long currentOrders = countSuccessOrdersMerged(currentStart, currentEnd, finalBranchId);
        long previousOrders = countSuccessOrdersMerged(previousStart, previousEnd, finalBranchId);

        BigDecimal currentProfit = currentRevenue.subtract(currentCost);
        BigDecimal previousProfit = previousRevenue.subtract(previousCost);

        MetricChangeResponse revenueChange = buildChange(currentRevenue, previousRevenue);
        MetricChangeResponse profitChange = buildChange(currentProfit, previousProfit);
        MetricChangeResponse orderChange = buildChange(currentOrders, previousOrders);

        OrderQualityCounts currentQuality = collectOrderQuality(currentStart, currentEnd, finalBranchId);
        OrderQualityCounts previousQuality = collectOrderQuality(previousStart, previousEnd, finalBranchId);
        MetricChangeResponse deliveredChange = buildChange(currentQuality.delivered(), previousQuality.delivered());
        MetricChangeResponse returnedChange = buildChange(currentQuality.returned(), previousQuality.returned());
        MetricChangeResponse cancelledChange = buildChange(currentQuality.cancelled(), previousQuality.cancelled());

        return MonthlyBusinessResultsResponse.builder()
                .yearMonth(periodKey)
                .currentMonthRevenue(currentRevenue)
                .previousMonthRevenue(previousRevenue)
                .revenueChangePercent(revenueChange.getChangePercent())
                .revenueIsNew(revenueChange.isNewBaseline())
                .currentMonthProfit(currentProfit)
                .previousMonthProfit(previousProfit)
                .profitChangePercent(profitChange.getChangePercent())
                .profitIsNew(profitChange.isNewBaseline())
                .currentMonthOrders(currentOrders)
                .previousMonthOrders(previousOrders)
                .orderChangePercent(orderChange.getChangePercent())
                .orderIsNew(orderChange.isNewBaseline())
                .revenueChange(revenueChange)
                .profitChange(profitChange)
                .orderChange(orderChange)
                .deliveredOrders(currentQuality.delivered())
                .returnedOrders(currentQuality.returned())
                .cancelledOrders(currentQuality.cancelled())
                .deliveredChange(deliveredChange)
                .returnedChange(returnedChange)
                .cancelledChange(cancelledChange)
                .build();
    }

    // 2.2 KẾT QUẢ KINH DOANH NGÀY
    public DailyBusinessResultsResponse getDailyResults(Long branchId) {
        Long finalBranchId = resolveBranchId(branchId);

        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime todayEnd = LocalDateTime.now();

        LocalDateTime yesterdayStart = LocalDate.now().minusDays(1).atStartOfDay();
        LocalDateTime yesterdayEnd = LocalDate.now().minusDays(1).atTime(java.time.LocalTime.MAX);

        // Doanh thu: 1 công thức duy nhất (đơn cũ + SubOrder đã phân bổ giảm giá) cho mọi phạm vi.
        BigDecimal todayRevenue = sumNetRevenue(todayStart, todayEnd, finalBranchId);
        BigDecimal yesterdayRevenue = sumNetRevenue(yesterdayStart, yesterdayEnd, finalBranchId);

        // Giá vốn đã dùng chung công thức branchId-nullable sẵn có, không cần tách nhánh.
        BigDecimal todayCost = getSafeBigDecimal(orderRepository.sumTotalCost(todayStart, todayEnd, finalBranchId));
        BigDecimal yesterdayCost = getSafeBigDecimal(orderRepository.sumTotalCost(yesterdayStart, yesterdayEnd, finalBranchId));

        // Số lượng đơn vẫn tách nhánh: Order.branch chỉ là "chi nhánh chính" lúc tạo đơn nên không
        // đáng tin cho đơn bị tách nhiều chi nhánh — phải đếm theo SubOrder khi lọc 1 chi nhánh cụ thể.
        long todayOrders = countSuccessOrdersMerged(todayStart, todayEnd, finalBranchId);
        long yesterdayOrders = countSuccessOrdersMerged(yesterdayStart, yesterdayEnd, finalBranchId);

        BigDecimal todayProfit = todayRevenue.subtract(todayCost);
        BigDecimal yesterdayProfit = yesterdayRevenue.subtract(yesterdayCost);

        MetricChangeResponse revenueChange = buildChange(todayRevenue, yesterdayRevenue);
        MetricChangeResponse profitChange = buildChange(todayProfit, yesterdayProfit);
        MetricChangeResponse orderChange = buildChange(todayOrders, yesterdayOrders);

        OrderQualityCounts todayQuality = collectOrderQuality(todayStart, todayEnd, finalBranchId);
        OrderQualityCounts yesterdayQuality = collectOrderQuality(yesterdayStart, yesterdayEnd, finalBranchId);
        MetricChangeResponse deliveredChange = buildChange(todayQuality.delivered(), yesterdayQuality.delivered());
        MetricChangeResponse returnedChange = buildChange(todayQuality.returned(), yesterdayQuality.returned());
        MetricChangeResponse cancelledChange = buildChange(todayQuality.cancelled(), yesterdayQuality.cancelled());

        return DailyBusinessResultsResponse.builder()
                .todayRevenue(todayRevenue)
                .yesterdayRevenue(yesterdayRevenue)
                .revenueChangePercent(revenueChange.getChangePercent())
                .revenueIsNew(revenueChange.isNewBaseline())
                .todayProfit(todayProfit)
                .yesterdayProfit(yesterdayProfit)
                .profitChangePercent(profitChange.getChangePercent())
                .profitIsNew(profitChange.isNewBaseline())
                .todayOrders(todayOrders)
                .yesterdayOrders(yesterdayOrders)
                .orderChangePercent(orderChange.getChangePercent())
                .orderIsNew(orderChange.isNewBaseline())
                .revenueChange(revenueChange)
                .profitChange(profitChange)
                .orderChange(orderChange)
                .deliveredOrders(todayQuality.delivered())
                .returnedOrders(todayQuality.returned())
                .cancelledOrders(todayQuality.cancelled())
                .deliveredChange(deliveredChange)
                .returnedChange(returnedChange)
                .cancelledChange(cancelledChange)
                .build();
    }

    // Kết quả kinh doanh theo tháng — dùng cho bộ lọc "xem theo tháng" ở trang tổng quan,
    // so sánh tháng được chọn với tháng liền trước. Nếu tháng được chọn là tháng hiện tại,
    // chỉ tính đến thời điểm hiện tại (không tính hết cả tháng vì chưa xảy ra).
    public MonthlyBusinessResultsResponse getMonthlyResults(Long branchId, YearMonth yearMonth) {
        Long finalBranchId = resolveBranchId(branchId);

        YearMonth targetMonth = yearMonth != null ? yearMonth : YearMonth.now();
        YearMonth previousMonth = targetMonth.minusMonths(1);
        LocalDateTime now = LocalDateTime.now();

        LocalDateTime currentStart = targetMonth.atDay(1).atStartOfDay();
        LocalDateTime currentEnd = targetMonth.equals(YearMonth.now())
                ? now
                : targetMonth.atEndOfMonth().atTime(java.time.LocalTime.MAX);

        LocalDateTime previousStart = previousMonth.atDay(1).atStartOfDay();
        LocalDateTime previousEnd = previousMonth.atEndOfMonth().atTime(java.time.LocalTime.MAX);

        return buildBusinessResultsResponse(
                targetMonth.toString(),
                currentStart,
                currentEnd,
                previousStart,
                previousEnd,
                finalBranchId);
    }

    public MonthlyBusinessResultsResponse getBusinessResults(
            Long branchId,
            LocalDate startDate,
            LocalDate endDate,
            YearMonth startMonth,
            YearMonth endMonth) {
        Long finalBranchId = resolveBranchId(branchId);
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();

        if (startMonth != null || endMonth != null) {
            YearMonth periodStartMonth = startMonth != null ? startMonth : endMonth;
            YearMonth periodEndMonth = endMonth != null ? endMonth : periodStartMonth;

            if (periodStartMonth.isAfter(periodEndMonth)) {
                YearMonth swap = periodStartMonth;
                periodStartMonth = periodEndMonth;
                periodEndMonth = swap;
            }

            YearMonth currentMonth = YearMonth.now();
            if (periodEndMonth.isAfter(currentMonth)) {
                periodEndMonth = currentMonth;
            }
            if (periodStartMonth.isAfter(periodEndMonth)) {
                periodStartMonth = periodEndMonth;
            }

            long monthCount = ChronoUnit.MONTHS.between(periodStartMonth, periodEndMonth) + 1;
            YearMonth previousEndMonth = periodStartMonth.minusMonths(1);
            YearMonth previousStartMonth = previousEndMonth.minusMonths(monthCount - 1);

            LocalDateTime currentStart = periodStartMonth.atDay(1).atStartOfDay();
            LocalDateTime currentEnd = periodEndMonth.equals(currentMonth)
                    ? now
                    : periodEndMonth.atEndOfMonth().atTime(java.time.LocalTime.MAX);
            LocalDateTime previousStart = previousStartMonth.atDay(1).atStartOfDay();
            LocalDateTime previousEnd = previousEndMonth.atEndOfMonth().atTime(java.time.LocalTime.MAX);

            String periodKey = periodStartMonth.equals(periodEndMonth)
                    ? periodStartMonth.toString()
                    : periodStartMonth + ".." + periodEndMonth;

            return buildBusinessResultsResponse(
                    periodKey,
                    currentStart,
                    currentEnd,
                    previousStart,
                    previousEnd,
                    finalBranchId);
        }

        LocalDate periodStartDate = startDate != null ? startDate : today;
        LocalDate periodEndDate = endDate != null ? endDate : periodStartDate;
        if (periodStartDate.isAfter(periodEndDate)) {
            LocalDate swap = periodStartDate;
            periodStartDate = periodEndDate;
            periodEndDate = swap;
        }
        if (periodEndDate.isAfter(today)) {
            periodEndDate = today;
        }
        if (periodStartDate.isAfter(periodEndDate)) {
            periodStartDate = periodEndDate;
        }

        long dayCount = ChronoUnit.DAYS.between(periodStartDate, periodEndDate) + 1;
        LocalDate previousEndDate = periodStartDate.minusDays(1);
        LocalDate previousStartDate = previousEndDate.minusDays(dayCount - 1);

        LocalDateTime currentStart = periodStartDate.atStartOfDay();
        LocalDateTime currentEnd = periodEndDate.equals(today)
                ? now
                : periodEndDate.atTime(java.time.LocalTime.MAX);
        LocalDateTime previousStart = previousStartDate.atStartOfDay();
        LocalDateTime previousEnd = previousEndDate.atTime(java.time.LocalTime.MAX);

        String periodKey = periodStartDate.equals(periodEndDate)
                ? periodStartDate.toString()
                : periodStartDate + ".." + periodEndDate;

        return buildBusinessResultsResponse(
                periodKey,
                currentStart,
                currentEnd,
                previousStart,
                previousEnd,
                finalBranchId);
    }

    // 2.4 HOẠT ĐỘNG GẦN ĐÂY
    public List<RecentActivityResponse> getRecentActivities(Long branchId) {
        Long finalBranchId = resolveBranchId(branchId);
        List<RecentActivityResponse> activities = new ArrayList<>();

        // 1. Lấy đơn hàng mới
        if (finalBranchId != null) {
            List<SubOrder> recentSubs = subOrderRepository.findByBranchIdOrderByCreatedAtDesc(finalBranchId);
            recentSubs.stream().limit(5).forEach(s -> {
                activities.add(RecentActivityResponse.builder()
                        .id("suborder-" + s.getId())
                        .type("ORDER")
                        .title("Đơn hàng mới: " + s.getOrder().getCode())
                        .timestamp(s.getOrder().getCreatedAt())
                        .user(s.getOrder().getUser() != null ? s.getOrder().getUser().getFullName() : "Khách vãng lai")
                        .build());
            });
        } else {
            List<Order> recentOrders = orderRepository.findRecentOrders(null, PageRequest.of(0, 5));
            for (Order order : recentOrders) {
                activities.add(RecentActivityResponse.builder()
                        .id("order-" + order.getId())
                        .type("ORDER")
                        .title("Đơn hàng mới: " + order.getCode())
                        .timestamp(order.getCreatedAt())
                        .user(order.getUser() != null ? order.getUser().getFullName() : "Khách vãng lai")
                        .build());
            }
        }

        // 2. Lấy khách hàng mới
        List<User> recentUsers = userRepository.findRecentCustomers(finalBranchId, PageRequest.of(0, 5));
        for (User user : recentUsers) {
            activities.add(RecentActivityResponse.builder()
                    .id("user-" + user.getId())
                    .type("CUSTOMER")
                    .title("Khách hàng mới: " + user.getFullName())
                    .timestamp(user.getCreatedAt())
                    .user(user.getFullName())
                    .build());
        }

        // 3. Lấy phiếu kho mới
        List<InventoryNote> recentNotes = inventoryNoteRepository.findRecentNotes(finalBranchId, PageRequest.of(0, 5));
        for (InventoryNote note : recentNotes) {
            activities.add(RecentActivityResponse.builder()
                    .id("note-" + note.getId())
                    .type("INVENTORY")
                    .title("Phiếu " + note.getType() + ": " + note.getCode())
                    .timestamp(note.getCreatedAt())
                    .user(note.getCreatedBy() != null ? note.getCreatedBy().getFullName() : "Hệ thống")
                    .build());
        }

        return activities.stream()
                .sorted((a, b) -> b.getTimestamp().compareTo(a.getTimestamp()))
                .limit(15)
                .collect(Collectors.toList());
    }

    // Số liệu gộp của 1 ngày — đơn vị nhỏ nhất mà cả biểu đồ 7 ngày lẫn biểu đồ cột theo tháng
    // đều dựng lên từ đó (theo tháng chỉ là cộng dồn các ngày trong tháng).
    private static class DailyAggregate {
        private BigDecimal revenue = BigDecimal.ZERO;
        private BigDecimal cost = BigDecimal.ZERO;
        private long orders = 0L;
    }

    // Gom doanh thu / giá vốn / số đơn theo từng ngày trong khoảng, dùng đúng công thức doanh thu
    // của toàn hệ thống (đơn cũ theo Order.finalAmount + đơn đã tách chi nhánh có phân bổ giảm giá).
    private Map<LocalDate, DailyAggregate> collectDailyAggregates(
            LocalDateTime start, LocalDateTime end, Long branchId) {
        Map<LocalDate, DailyAggregate> byDate = new HashMap<>();

        // Đơn cũ (không có SubOrder) gộp theo ngày trực tiếp từ Order.finalAmount.
        for (Object[] row : orderRepository.getLegacyDailyStats(start, end, branchId)) {
            LocalDate date = ((java.sql.Date) row[0]).toLocalDate();
            DailyAggregate aggregate = byDate.computeIfAbsent(date, key -> new DailyAggregate());
            aggregate.revenue = aggregate.revenue.add(getSafeBigDecimal((BigDecimal) row[1]));
            aggregate.orders += row[2] == null ? 0L : (Long) row[2];
        }

        // Đơn đã tách chi nhánh: cộng dồn theo ngày trong Java vì mỗi dòng cần phân bổ giảm giá riêng.
        for (SubOrderRepository.DashboardRevenueRow row : subOrderRepository.findRevenueRows(start, end, branchId)) {
            LocalDate date = row.getCreatedAt().toLocalDate();
            DailyAggregate aggregate = byDate.computeIfAbsent(date, key -> new DailyAggregate());
            aggregate.revenue = aggregate.revenue.add(netSubOrderRevenue(row));
            aggregate.orders += 1L;
        }

        for (Object[] row : orderRepository.getDailyCosts(start, end, branchId)) {
            LocalDate date = ((java.sql.Date) row[0]).toLocalDate();
            DailyAggregate aggregate = byDate.computeIfAbsent(date, key -> new DailyAggregate());
            aggregate.cost = aggregate.cost.add(getSafeBigDecimal((BigDecimal) row[1]));
        }

        return byDate;
    }

    // 2.3 BIỂU ĐỒ HIỆU SUẤT DOANH SỐ
    public SalesPerformanceResponse getSalesPerformance(Long branchId) {
        Long finalBranchId = resolveBranchId(branchId);
        LocalDateTime endDate = LocalDateTime.now();
        LocalDateTime startDate = LocalDate.now().minusDays(6).atStartOfDay();

        Map<LocalDate, DailyAggregate> byDate = collectDailyAggregates(startDate, endDate, finalBranchId);

        List<SalesPerformanceResponse.DataPoint> dataPoints = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate date = LocalDate.now().minusDays(i);
            DailyAggregate aggregate = byDate.getOrDefault(date, new DailyAggregate());

            dataPoints.add(SalesPerformanceResponse.DataPoint.builder()
                    .date(date)
                    .revenue(aggregate.revenue)
                    .profit(aggregate.revenue.subtract(aggregate.cost))
                    .orderCount(aggregate.orders)
                    .build());
        }

        return SalesPerformanceResponse.builder()
                .data(dataPoints)
                .build();
    }

    // Số cột tối thiểu của biểu đồ xu hướng: 1 cột đơn độc không so sánh được với gì cả, nên khi
    // người dùng chọn đúng 1 tháng (hoặc 1 ngày) vẫn kéo lùi mốc bắt đầu để có bối cảnh.
    private static final int MIN_MONTH_BUCKETS = 6;
    private static final int MIN_DAY_BUCKETS = 7;
    // Trần số cột để trục X không bị chèn chữ và truy vấn không quét quá rộng.
    private static final int MAX_MONTH_BUCKETS = 24;
    private static final int MAX_DAY_BUCKETS = 62;

    /**
     * Chuỗi doanh thu / giá vốn / lợi nhuận theo cột cho biểu đồ xu hướng.
     *
     * <p>Khoảng ngày quá dài sẽ tự hạ độ chi tiết xuống theo tháng (trả về trong
     * {@code granularity}) — 90 cột ngày cạnh nhau chỉ là 1 vệt mực, không đọc được.
     */
    public BusinessTrendResponse getBusinessTrend(
            Long branchId, String granularity, LocalDate startDate, LocalDate endDate) {
        Long finalBranchId = resolveBranchId(branchId);
        LocalDate today = LocalDate.now();

        boolean monthly = !"DAY".equalsIgnoreCase(granularity);

        LocalDate rangeEnd = endDate != null && !endDate.isAfter(today) ? endDate : today;
        LocalDate rangeStart = startDate != null && !startDate.isAfter(rangeEnd)
                ? startDate
                : (monthly
                        ? rangeEnd.withDayOfMonth(1).minusMonths(MIN_MONTH_BUCKETS - 1L)
                        : rangeEnd.minusDays(MIN_DAY_BUCKETS - 1L));

        // Khoảng ngày dài hơn trần cột ngày thì đọc theo tháng cho dễ nhìn.
        if (!monthly && ChronoUnit.DAYS.between(rangeStart, rangeEnd) + 1 > MAX_DAY_BUCKETS) {
            monthly = true;
        }

        if (monthly) {
            YearMonth startMonth = YearMonth.from(rangeStart);
            YearMonth endMonth = YearMonth.from(rangeEnd);
            long buckets = ChronoUnit.MONTHS.between(startMonth, endMonth) + 1;
            if (buckets < MIN_MONTH_BUCKETS) {
                startMonth = endMonth.minusMonths(MIN_MONTH_BUCKETS - 1L);
            } else if (buckets > MAX_MONTH_BUCKETS) {
                startMonth = endMonth.minusMonths(MAX_MONTH_BUCKETS - 1L);
            }
            rangeStart = startMonth.atDay(1);
        } else {
            long buckets = ChronoUnit.DAYS.between(rangeStart, rangeEnd) + 1;
            if (buckets < MIN_DAY_BUCKETS) {
                rangeStart = rangeEnd.minusDays(MIN_DAY_BUCKETS - 1L);
            }
        }

        LocalDateTime queryStart = rangeStart.atStartOfDay();
        LocalDateTime queryEnd = rangeEnd.equals(today)
                ? LocalDateTime.now()
                : rangeEnd.atTime(java.time.LocalTime.MAX);

        Map<LocalDate, DailyAggregate> byDate = collectDailyAggregates(queryStart, queryEnd, finalBranchId);

        List<BusinessTrendResponse.Point> points = new ArrayList<>();
        if (monthly) {
            Map<YearMonth, DailyAggregate> byMonth = new HashMap<>();
            for (Map.Entry<LocalDate, DailyAggregate> entry : byDate.entrySet()) {
                DailyAggregate bucket = byMonth.computeIfAbsent(
                        YearMonth.from(entry.getKey()), key -> new DailyAggregate());
                bucket.revenue = bucket.revenue.add(entry.getValue().revenue);
                bucket.cost = bucket.cost.add(entry.getValue().cost);
                bucket.orders += entry.getValue().orders;
            }

            YearMonth cursor = YearMonth.from(rangeStart);
            YearMonth last = YearMonth.from(rangeEnd);
            while (!cursor.isAfter(last)) {
                points.add(toTrendPoint(
                        cursor.toString(),
                        "T" + cursor.getMonthValue() + "/" + cursor.getYear(),
                        byMonth.getOrDefault(cursor, new DailyAggregate())));
                cursor = cursor.plusMonths(1);
            }
        } else {
            LocalDate cursor = rangeStart;
            while (!cursor.isAfter(rangeEnd)) {
                DailyAggregate bucket = byDate.getOrDefault(cursor, new DailyAggregate());
                points.add(toTrendPoint(
                        cursor.toString(),
                        String.format("%02d/%02d", cursor.getDayOfMonth(), cursor.getMonthValue()),
                        bucket));
                cursor = cursor.plusDays(1);
            }
        }

        String rangeLabel = monthly
                ? "Từ tháng " + YearMonth.from(rangeStart).getMonthValue() + "/" + rangeStart.getYear()
                        + " đến tháng " + YearMonth.from(rangeEnd).getMonthValue() + "/" + rangeEnd.getYear()
                : "Từ ngày " + rangeStart + " đến ngày " + rangeEnd;

        return BusinessTrendResponse.builder()
                .granularity(monthly ? "MONTH" : "DAY")
                .rangeLabel(rangeLabel)
                .points(points)
                .build();
    }

    private BusinessTrendResponse.Point toTrendPoint(String period, String label, DailyAggregate bucket) {
        return BusinessTrendResponse.Point.builder()
                .period(period)
                .label(label)
                .revenue(bucket.revenue)
                .cost(bucket.cost)
                .profit(bucket.revenue.subtract(bucket.cost))
                .orders(bucket.orders)
                .build();
    }

    // THÔNG TIN KHO (Thống kê nhanh)
    public InventoryInfoResponse getInventoryInfo(Long branchId) {
        Long finalBranchId = resolveBranchId(branchId);

        long totalItems = inventoryRepository.countDistinctProducts(finalBranchId);
        long lowStockCount = inventoryRepository.getLowStockProductIds(lowStockThreshold, finalBranchId).size();
        long outOfStockCount = inventoryRepository.countOutOfStockProducts(finalBranchId);
        BigDecimal totalValue = getSafeBigDecimal(inventoryRepository.sumTotalValue(finalBranchId));

        // Tồn kho không có bảng lưu vết lịch sử theo ngày, nên suy ngược "giá trị hôm qua" bằng
        // cách lấy giá trị hiện tại trừ đi biến động ròng (nhập/bán/điều chuyển...) phát sinh hôm nay.
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime now = LocalDateTime.now();
        BigDecimal netChangeToday = getSafeBigDecimal(
                inventoryTransactionRepository.sumNetValueChange(todayStart, now, finalBranchId));
        BigDecimal valueAsOfYesterday = totalValue.subtract(netChangeToday);
        MetricChangeResponse valueChange = buildChange(totalValue, valueAsOfYesterday);

        return InventoryInfoResponse.builder()
                .totalItems(totalItems)
                .lowStockCount(lowStockCount)
                .outOfStockCount(outOfStockCount)
                .totalInventoryValue(totalValue)
                .valueChangePercent(valueChange.getChangePercent())
                .valueIsNew(valueChange.isNewBaseline())
                .valueChange(valueChange)
                .build();
    }

    // Gộp dữ liệu sản phẩm bán chạy từ 2 nguồn (đơn cũ + đơn đã tách chi nhánh) theo productId, vì
    // 1 sản phẩm có thể vừa bán qua đơn cũ vừa bán qua đơn mới — cộng dồn số lượng/doanh thu lại
    // thay vì giữ 2 dòng riêng cho cùng 1 sản phẩm.
    private static class ProductSalesAggregate {
        String productName;
        String imageUrl;
        long quantitySold;
        BigDecimal revenue = BigDecimal.ZERO;
    }

    private void mergeProductRow(
            Map<Long, ProductSalesAggregate> byProduct, ProductRepository.TopProductProjection row) {
        if (row.getProductId() == null) {
            return;
        }
        ProductSalesAggregate aggregate = byProduct.computeIfAbsent(
                row.getProductId(), id -> new ProductSalesAggregate());
        aggregate.productName = row.getProductName();
        if (aggregate.imageUrl == null && row.getImageUrl() != null) {
            aggregate.imageUrl = row.getImageUrl();
        }
        aggregate.quantitySold += row.getQuantitySold() == null ? 0L : row.getQuantitySold();
        aggregate.revenue = aggregate.revenue.add(getSafeBigDecimal(row.getRevenue()));
    }

    // TOP SẢN PHẨM BÁN CHẠY
    public List<TopProductResponse> getTopProducts(Long branchId, int limit) {
        Long finalBranchId = resolveBranchId(branchId);

        // Đơn cũ (trước khi có luồng tách đơn theo chi nhánh) không có SubOrderItem, nên chỉ đọc
        // SubOrderItem sẽ bỏ sót toàn bộ sản phẩm bán qua đơn cũ — phải gộp cả 2 nguồn giống hệt
        // cách tính doanh thu (sumNetRevenueNowAndAsOf) đang làm ở trên.
        Map<Long, ProductSalesAggregate> byProduct = new HashMap<>();
        for (ProductRepository.TopProductProjection row : productRepository.getTopSellingProductsLegacy(finalBranchId)) {
            mergeProductRow(byProduct, row);
        }
        for (ProductRepository.TopProductProjection row : productRepository.getTopSellingProducts(finalBranchId)) {
            mergeProductRow(byProduct, row);
        }

        return byProduct.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue().quantitySold, a.getValue().quantitySold))
                .limit(limit)
                .map(entry -> TopProductResponse.builder()
                        .productId(entry.getKey())
                        .productName(entry.getValue().productName)
                        .quantitySold(entry.getValue().quantitySold)
                        .revenue(entry.getValue().revenue)
                        .imageUrl(entry.getValue().imageUrl)
                        .build())
                .collect(Collectors.toList());
    }

    // TÓM TẮT ĐƠN HÀNG THEO TRẠNG THÁI
    public PendingOrdersSummaryResponse getPendingOrdersSummary(Long branchId) {
        Long finalBranchId = resolveBranchId(branchId);

        return PendingOrdersSummaryResponse.builder()
                .pendingApproval(countByStatusMerged(OrderStatus.PENDING, finalBranchId))
                .pendingPayment(countByStatusMerged(OrderStatus.AWAITING_PAYMENT, finalBranchId))
                .pendingPacking(countByStatusMerged(OrderStatus.PROCESSING, finalBranchId))
                .pendingPickup(countByStatusMerged(OrderStatus.READY_FOR_PICKUP, finalBranchId))
                .shipping(countByStatusMerged(OrderStatus.SHIPPING, finalBranchId))
                .cancelPending(countByStatusMerged(OrderStatus.CANCELLED, finalBranchId))
                .build();
    }

    // Gộp dữ liệu doanh thu theo danh mục từ 2 nguồn (đơn cũ + đơn đã tách chi nhánh), cùng lý do
    // với mergeProductRow ở trên: 1 danh mục có thể có doanh thu từ cả 2 loại đơn.
    private static class CategorySalesAggregate {
        String categoryName;
        long totalQuantity;
        BigDecimal totalRevenue = BigDecimal.ZERO;
    }

    private void mergeCategoryRow(
            Map<Long, CategorySalesAggregate> byCategory, ProductRepository.CategorySalesProjection row) {
        if (row.getCategoryId() == null) {
            return;
        }
        CategorySalesAggregate aggregate = byCategory.computeIfAbsent(
                row.getCategoryId(), id -> new CategorySalesAggregate());
        aggregate.categoryName = row.getCategoryName();
        aggregate.totalQuantity += row.getTotalQuantity() == null ? 0L : row.getTotalQuantity();
        aggregate.totalRevenue = aggregate.totalRevenue.add(getSafeBigDecimal(row.getTotalRevenue()));
    }

    // TỶ TRỌNG DOANH THU THEO DANH MỤC (Vẽ biểu đồ tròn)
    public List<CategoryDistributionResponse> getCategoryDistribution(Long branchId) {
        Long finalBranchId = resolveBranchId(branchId);

        // Cùng lý do với getTopProducts(): đơn cũ không có SubOrderItem nên phải gộp cả 2 nguồn,
        // không được chỉ đọc 1 trong 2 theo branchId như trước (làm rỗng/thiếu số cơ cấu nhóm hàng).
        Map<Long, CategorySalesAggregate> byCategory = new HashMap<>();
        for (ProductRepository.CategorySalesProjection row : productRepository.getCategorySalesLegacy(finalBranchId)) {
            mergeCategoryRow(byCategory, row);
        }
        for (ProductRepository.CategorySalesProjection row : subOrderRepository.getCategorySalesByBranch(finalBranchId)) {
            mergeCategoryRow(byCategory, row);
        }

        BigDecimal totalRevenueAll = byCategory.values().stream()
                .map(a -> a.totalRevenue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return byCategory.entrySet().stream()
                .sorted((a, b) -> b.getValue().totalRevenue.compareTo(a.getValue().totalRevenue))
                .map(entry -> {
                    BigDecimal revenue = entry.getValue().totalRevenue;
                    double percentage = 0.0;
                    if (totalRevenueAll.compareTo(BigDecimal.ZERO) > 0) {
                        percentage = revenue.multiply(BigDecimal.valueOf(100))
                                .divide(totalRevenueAll, 2, java.math.RoundingMode.HALF_UP)
                                .doubleValue();
                    }

                    return CategoryDistributionResponse.builder()
                            .categoryId(entry.getKey())
                            .categoryName(entry.getValue().categoryName)
                            .totalRevenue(revenue)
                            .totalQuantity(entry.getValue().totalQuantity)
                            .percentage(percentage)
                            .build();
                })
                .collect(Collectors.toList());
    }

    // DANH SÁCH ĐƠN HÀNG CHỜ DUYỆT
    public List<PendingOrderResponse> getPendingOrders(Long branchId, int limit) {
        Long finalBranchId = resolveBranchId(branchId);
        
        if (finalBranchId != null) {
            List<SubOrder> subs = subOrderRepository.findPendingByBranchId(OrderStatus.PENDING, finalBranchId, PageRequest.of(0, limit));
            return subs.stream().map(s -> PendingOrderResponse.builder()
                    .id(s.getOrder().getId())
                    .orderCode(s.getOrder().getCode())
                    .customerName(s.getOrder().getUser() != null ? s.getOrder().getUser().getFullName() : "Khách vãng lai")
                    .orderDate(s.getOrder().getCreatedAt())
                    .totalAmount(s.getSubtotal())
                    .status(s.getStatus().name())
                    .build()
            ).collect(Collectors.toList());
        } else {
            List<Order> orders = orderRepository.findPendingOrders(OrderStatus.PENDING, null, PageRequest.of(0, limit));
            return orders.stream().map(o -> PendingOrderResponse.builder()
                    .id(o.getId())
                    .orderCode(o.getCode())
                    .customerName(o.getUser() != null ? o.getUser().getFullName() : "Khách vãng lai")
                    .orderDate(o.getCreatedAt())
                    .totalAmount(o.getTotalAmount())
                    .status(o.getStatus().name())
                    .build()
            ).collect(Collectors.toList());
        }
    }
}
