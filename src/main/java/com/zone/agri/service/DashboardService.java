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

        long totalOrders;
        long ordersAsOfYesterday;
        if (finalBranchId != null) {
            totalOrders = subOrderRepository.countAllByBranchIdExceptCancelled(finalBranchId);
            ordersAsOfYesterday = subOrderRepository.countAllByBranchIdExceptCancelledBefore(yesterdayEnd, finalBranchId);
        } else {
            totalOrders = orderRepository.countAllOrdersExceptCancelled(null);
            ordersAsOfYesterday = orderRepository.countAllOrdersExceptCancelledBefore(yesterdayEnd, null);
        }
        // Doanh thu dùng chung 1 công thức (đơn cũ + đơn đã tách chi nhánh, giảm giá phân bổ theo tỉ lệ)
        // cho cả 2 nhánh trên, để "Tổng doanh thu" của 1 chi nhánh cụ thể cộng dồn đúng khớp với
        // "Tổng doanh thu" khi xem toàn hệ thống. Chỉ fetch dữ liệu lịch sử 1 lần (xem hàm dưới).
        BigDecimal[] revenueSnapshot = sumNetRevenueNowAndAsOf(startOfTime, now, yesterdayEnd, finalBranchId);
        BigDecimal totalRevenue = revenueSnapshot[0];
        BigDecimal revenueAsOfYesterday = revenueSnapshot[1];

        long totalCustomers = userRepository.countCustomers(finalBranchId);
        long customersAsOfYesterday = userRepository.countCustomersBefore(finalBranchId, yesterdayEnd);
        long totalProducts = productRepository.countActiveProducts();

        return DashboardStatsResponse.builder()
                .totalOrders(totalOrders)
                .totalRevenue(totalRevenue)
                .totalCustomers(totalCustomers)
                .totalProducts(totalProducts)
                .revenueChangePercent(calculateGrowthPercent(totalRevenue, revenueAsOfYesterday))
                .revenueIsNew(isNewBaseline(totalRevenue, revenueAsOfYesterday))
                .ordersChangePercent(calculateGrowthPercent(totalOrders, ordersAsOfYesterday))
                .ordersIsNew(isNewBaseline(totalOrders, ordersAsOfYesterday))
                .customersChangePercent(calculateGrowthPercent(totalCustomers, customersAsOfYesterday))
                .customersIsNew(isNewBaseline(totalCustomers, customersAsOfYesterday))
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

    // Khi mốc so sánh (hôm qua) = 0, tăng trưởng thực tế là vô hạn/không xác định — trả về 0%
    // thay vì bịa ra "+100%", và đánh dấu isNew=true để tầng hiển thị tự quyết cách diễn giải
    // (ví dụ hiện badge "Mới" thay vì một con số % gây hiểu lầm).
    private double calculateGrowthPercent(BigDecimal today, BigDecimal yesterday) {
        if (yesterday == null || yesterday.compareTo(BigDecimal.ZERO) == 0) {
            return 0.0;
        }
        return today.subtract(yesterday)
                .divide(yesterday, 4, java.math.RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .doubleValue();
    }

    private boolean isNewBaseline(BigDecimal today, BigDecimal yesterday) {
        boolean noYesterdayBaseline = yesterday == null || yesterday.compareTo(BigDecimal.ZERO) == 0;
        return noYesterdayBaseline && today != null && today.compareTo(BigDecimal.ZERO) > 0;
    }

    private double calculateGrowthPercent(long today, long yesterday) {
        if (yesterday == 0) {
            return 0.0;
        }
        return ((double) (today - yesterday) / yesterday) * 100.0;
    }

    private boolean isNewBaseline(long today, long yesterday) {
        return yesterday == 0 && today > 0;
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
        long todayOrders, yesterdayOrders;
        if (finalBranchId != null) {
            todayOrders = subOrderRepository.countSuccessByBranchId(todayStart, todayEnd, finalBranchId);
            yesterdayOrders = subOrderRepository.countSuccessByBranchId(yesterdayStart, yesterdayEnd, finalBranchId);
        } else {
            todayOrders = orderRepository.countSuccessOrders(todayStart, todayEnd, null);
            yesterdayOrders = orderRepository.countSuccessOrders(yesterdayStart, yesterdayEnd, null);
        }

        BigDecimal todayProfit = todayRevenue.subtract(todayCost);
        BigDecimal yesterdayProfit = yesterdayRevenue.subtract(yesterdayCost);

        return DailyBusinessResultsResponse.builder()
                .todayRevenue(todayRevenue)
                .yesterdayRevenue(yesterdayRevenue)
                .revenueChangePercent(calculateGrowthPercent(todayRevenue, yesterdayRevenue))
                .revenueIsNew(isNewBaseline(todayRevenue, yesterdayRevenue))
                .todayProfit(todayProfit)
                .yesterdayProfit(yesterdayProfit)
                .profitChangePercent(calculateGrowthPercent(todayProfit, yesterdayProfit))
                .profitIsNew(isNewBaseline(todayProfit, yesterdayProfit))
                .todayOrders(todayOrders)
                .yesterdayOrders(yesterdayOrders)
                .orderChangePercent(calculateGrowthPercent(todayOrders, yesterdayOrders))
                .orderIsNew(isNewBaseline(todayOrders, yesterdayOrders))
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

        BigDecimal currentRevenue = sumNetRevenue(currentStart, currentEnd, finalBranchId);
        BigDecimal previousRevenue = sumNetRevenue(previousStart, previousEnd, finalBranchId);

        BigDecimal currentCost = getSafeBigDecimal(orderRepository.sumTotalCost(currentStart, currentEnd, finalBranchId));
        BigDecimal previousCost = getSafeBigDecimal(orderRepository.sumTotalCost(previousStart, previousEnd, finalBranchId));

        long currentOrders, previousOrders;
        if (finalBranchId != null) {
            currentOrders = subOrderRepository.countSuccessByBranchId(currentStart, currentEnd, finalBranchId);
            previousOrders = subOrderRepository.countSuccessByBranchId(previousStart, previousEnd, finalBranchId);
        } else {
            currentOrders = orderRepository.countSuccessOrders(currentStart, currentEnd, null);
            previousOrders = orderRepository.countSuccessOrders(previousStart, previousEnd, null);
        }

        BigDecimal currentProfit = currentRevenue.subtract(currentCost);
        BigDecimal previousProfit = previousRevenue.subtract(previousCost);

        return MonthlyBusinessResultsResponse.builder()
                .yearMonth(targetMonth.toString())
                .currentMonthRevenue(currentRevenue)
                .previousMonthRevenue(previousRevenue)
                .revenueChangePercent(calculateGrowthPercent(currentRevenue, previousRevenue))
                .revenueIsNew(isNewBaseline(currentRevenue, previousRevenue))
                .currentMonthProfit(currentProfit)
                .previousMonthProfit(previousProfit)
                .profitChangePercent(calculateGrowthPercent(currentProfit, previousProfit))
                .profitIsNew(isNewBaseline(currentProfit, previousProfit))
                .currentMonthOrders(currentOrders)
                .previousMonthOrders(previousOrders)
                .orderChangePercent(calculateGrowthPercent(currentOrders, previousOrders))
                .orderIsNew(isNewBaseline(currentOrders, previousOrders))
                .build();
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

    // 2.3 BIỂU ĐỒ HIỆU SUẤT DOANH SỐ
    public SalesPerformanceResponse getSalesPerformance(Long branchId) {
        Long finalBranchId = resolveBranchId(branchId);
        LocalDateTime endDate = LocalDateTime.now();
        LocalDateTime startDate = LocalDate.now().minusDays(6).atStartOfDay();

        List<Object[]> costsRaw = orderRepository.getDailyCosts(startDate, endDate, finalBranchId);

        Map<LocalDate, BigDecimal> revenueMap = new HashMap<>();
        Map<LocalDate, Long> orderCountMap = new HashMap<>();
        Map<LocalDate, BigDecimal> costMap = new HashMap<>();

        // Đơn cũ (không có SubOrder) gộp theo ngày trực tiếp từ Order.finalAmount.
        List<Object[]> legacyStats = orderRepository.getLegacyDailyStats(startDate, endDate, finalBranchId);
        for (Object[] row : legacyStats) {
            LocalDate date = ((java.sql.Date) row[0]).toLocalDate();
            revenueMap.merge(date, (BigDecimal) row[1], BigDecimal::add);
            orderCountMap.merge(date, (Long) row[2], Long::sum);
        }

        // Đơn đã tách chi nhánh: cộng dồn theo ngày trong Java vì mỗi dòng cần phân bổ giảm giá riêng.
        for (SubOrderRepository.DashboardRevenueRow row : subOrderRepository.findRevenueRows(startDate, endDate, finalBranchId)) {
            LocalDate date = row.getCreatedAt().toLocalDate();
            revenueMap.merge(date, netSubOrderRevenue(row), BigDecimal::add);
            orderCountMap.merge(date, 1L, Long::sum);
        }

        for (Object[] row : costsRaw) {
            LocalDate date = ((java.sql.Date) row[0]).toLocalDate();
            costMap.put(date, (BigDecimal) row[1]);
        }

        List<SalesPerformanceResponse.DataPoint> dataPoints = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate date = LocalDate.now().minusDays(i);
            BigDecimal revenue = revenueMap.getOrDefault(date, BigDecimal.ZERO);
            BigDecimal cost = costMap.getOrDefault(date, BigDecimal.ZERO);
            long orders = orderCountMap.getOrDefault(date, 0L);

            dataPoints.add(SalesPerformanceResponse.DataPoint.builder()
                    .date(date)
                    .revenue(revenue)
                    .profit(revenue.subtract(cost))
                    .orderCount(orders)
                    .build());
        }

        return SalesPerformanceResponse.builder()
                .data(dataPoints)
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

        return InventoryInfoResponse.builder()
                .totalItems(totalItems)
                .lowStockCount(lowStockCount)
                .outOfStockCount(outOfStockCount)
                .totalInventoryValue(totalValue)
                .valueChangePercent(calculateGrowthPercent(totalValue, valueAsOfYesterday))
                .valueIsNew(isNewBaseline(totalValue, valueAsOfYesterday))
                .build();
    }

    // TOP SẢN PHẨM BÁN CHẠY
    public List<TopProductResponse> getTopProducts(Long branchId, int limit) {
        Long finalBranchId = resolveBranchId(branchId);
        List<ProductRepository.TopProductProjection> topProducts = productRepository.getTopSellingProducts(finalBranchId, PageRequest.of(0, limit));

        return topProducts.stream().map(p -> TopProductResponse.builder()
                .productId(p.getProductId())
                .productName(p.getProductName())
                .quantitySold(p.getQuantitySold())
                .revenue(p.getRevenue())
                .imageUrl(p.getImageUrl())
                .build()
        ).collect(Collectors.toList());
    }

    // TÓM TẮT ĐƠN HÀNG THEO TRẠNG THÁI
    public PendingOrdersSummaryResponse getPendingOrdersSummary(Long branchId) {
        Long finalBranchId = resolveBranchId(branchId);

        if (finalBranchId != null) {
            return PendingOrdersSummaryResponse.builder()
                    .pendingApproval(subOrderRepository.countByStatusAndBranchId(OrderStatus.PENDING, finalBranchId))
                    .pendingPayment(subOrderRepository.countByStatusAndBranchId(OrderStatus.AWAITING_PAYMENT, finalBranchId))
                    .pendingPacking(subOrderRepository.countByStatusAndBranchId(OrderStatus.PROCESSING, finalBranchId))
                    .pendingPickup(subOrderRepository.countByStatusAndBranchId(OrderStatus.READY_FOR_PICKUP, finalBranchId))
                    .shipping(subOrderRepository.countByStatusAndBranchId(OrderStatus.SHIPPING, finalBranchId))
                    .cancelPending(subOrderRepository.countByStatusAndBranchId(OrderStatus.CANCELLED, finalBranchId))
                    .build();
        } else {
            return PendingOrdersSummaryResponse.builder()
                    .pendingApproval(orderRepository.countByStatus(OrderStatus.PENDING, null))
                    .pendingPayment(orderRepository.countByStatus(OrderStatus.AWAITING_PAYMENT, null))
                    .pendingPacking(orderRepository.countByStatus(OrderStatus.PROCESSING, null))
                    .pendingPickup(orderRepository.countByStatus(OrderStatus.READY_FOR_PICKUP, null))
                    .shipping(orderRepository.countByStatus(OrderStatus.SHIPPING, null))
                    .cancelPending(orderRepository.countByStatus(OrderStatus.CANCELLED, null))
                    .build();
        }
    }

    // TỶ TRỌNG DOANH THU THEO DANH MỤC (Vẽ biểu đồ tròn)
    public List<CategoryDistributionResponse> getCategoryDistribution(Long branchId) {
        Long finalBranchId = resolveBranchId(branchId);
        List<ProductRepository.CategorySalesProjection> projections;

        if (finalBranchId != null) {
            projections = subOrderRepository.getCategorySalesByBranch(finalBranchId);
        } else {
            projections = productRepository.getCategorySalesSystemWide();
        }

        BigDecimal totalRevenueAll = projections.stream()
                .map(p -> p.getTotalRevenue() != null ? p.getTotalRevenue() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return projections.stream().map(p -> {
            BigDecimal revenue = p.getTotalRevenue() != null ? p.getTotalRevenue() : BigDecimal.ZERO;
            double percentage = 0.0;
            if (totalRevenueAll.compareTo(BigDecimal.ZERO) > 0) {
                percentage = revenue.multiply(BigDecimal.valueOf(100))
                        .divide(totalRevenueAll, 2, java.math.RoundingMode.HALF_UP)
                        .doubleValue();
            }

            return CategoryDistributionResponse.builder()
                    .categoryId(p.getCategoryId())
                    .categoryName(p.getCategoryName())
                    .totalRevenue(revenue)
                    .totalQuantity(p.getTotalQuantity())
                    .percentage(percentage)
                    .build();
        }).collect(Collectors.toList());
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
