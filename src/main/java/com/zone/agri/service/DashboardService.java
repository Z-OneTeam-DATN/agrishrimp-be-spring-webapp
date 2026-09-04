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
    private final SubOrderRepository subOrderRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final InventoryNoteRepository inventoryNoteRepository;
    private final InventoryTransactionRepository inventoryTransactionRepository;
    private final VisitService visitService;
    private final ReturnRequestRepository returnRequestRepository;

    @Value("${dashboard.low-stock-threshold:10}")
    private int lowStockThreshold;

    private BigDecimal getSafeBigDecimal(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private Long resolveBranchId(Long requestBranchId) {
        return AuthUtils.resolveRequestedOrUserBranch(requestBranchId, "DASHBOARD_VIEW");
    }

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

    private BigDecimal sumNetRevenue(LocalDateTime start, LocalDateTime end, Long branchId) {
        BigDecimal legacyRevenue = getSafeBigDecimal(orderRepository.sumLegacyRevenue(start, end, branchId));
        BigDecimal subOrderRevenue = subOrderRepository.findRevenueRows(start, end, branchId).stream()
                .map(this::netSubOrderRevenue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return legacyRevenue.add(subOrderRevenue);
    }

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

    public DashboardStatsResponse getStats(Long branchId) {
        Long finalBranchId = resolveBranchId(branchId);

        LocalDateTime startOfTime = LocalDateTime.of(2000, 1, 1, 0, 0);
        LocalDateTime now = LocalDateTime.now();

        LocalDateTime yesterdayEnd = LocalDate.now().minusDays(1).atTime(java.time.LocalTime.MAX);

        long totalOrders = countTotalOrders(finalBranchId);
        long ordersAsOfYesterday = countTotalOrdersBefore(yesterdayEnd, finalBranchId);

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

    private static final double MAX_CHANGE_PERCENT = 999.9;

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

    private record OrderQualityCounts(long delivered, long returned, long cancelled) {
    }

    private OrderQualityCounts collectOrderQuality(LocalDateTime start, LocalDateTime end, Long branchId) {
        long delivered = orderRepository.countDeliveredOrders(start, end, branchId)
                + subOrderRepository.countDeliveredByBranchId(start, end, branchId);
        long returnedSubOrders = orderRepository.countReturnedOrders(start, end, branchId)
                + subOrderRepository.countReturnedByBranchId(start, end, branchId);
        long refundedRequests = returnRequestRepository.countRefundedRequests(start, end, branchId);
        long returned = returnedSubOrders + refundedRequests;
        long cancelled = orderRepository.countCancelledOrders(start, end, branchId)
                + subOrderRepository.countCancelledByBranchId(start, end, branchId);

        return new OrderQualityCounts(delivered, returned, cancelled);
    }

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

    public DailyBusinessResultsResponse getDailyResults(Long branchId) {
        Long finalBranchId = resolveBranchId(branchId);

        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime todayEnd = LocalDateTime.now();

        LocalDateTime yesterdayStart = LocalDate.now().minusDays(1).atStartOfDay();
        LocalDateTime yesterdayEnd = LocalDate.now().minusDays(1).atTime(java.time.LocalTime.MAX);

        BigDecimal todayRevenue = sumNetRevenue(todayStart, todayEnd, finalBranchId);
        BigDecimal yesterdayRevenue = sumNetRevenue(yesterdayStart, yesterdayEnd, finalBranchId);

        BigDecimal todayCost = getSafeBigDecimal(orderRepository.sumTotalCost(todayStart, todayEnd, finalBranchId));
        BigDecimal yesterdayCost = getSafeBigDecimal(orderRepository.sumTotalCost(yesterdayStart, yesterdayEnd, finalBranchId));

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

    public List<RecentActivityResponse> getRecentActivities(Long branchId) {
        Long finalBranchId = resolveBranchId(branchId);
        List<RecentActivityResponse> activities = new ArrayList<>();

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

    private static class DailyAggregate {
        private BigDecimal revenue = BigDecimal.ZERO;
        private BigDecimal cost = BigDecimal.ZERO;
        private long orders = 0L;
    }

    private Map<LocalDate, DailyAggregate> collectDailyAggregates(
            LocalDateTime start, LocalDateTime end, Long branchId) {
        Map<LocalDate, DailyAggregate> byDate = new HashMap<>();

        for (Object[] row : orderRepository.getLegacyDailyStats(start, end, branchId)) {
            LocalDate date = ((java.sql.Date) row[0]).toLocalDate();
            DailyAggregate aggregate = byDate.computeIfAbsent(date, key -> new DailyAggregate());
            aggregate.revenue = aggregate.revenue.add(getSafeBigDecimal((BigDecimal) row[1]));
            aggregate.orders += row[2] == null ? 0L : (Long) row[2];
        }

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

    private static final int MIN_MONTH_BUCKETS = 6;
    private static final int MIN_DAY_BUCKETS = 7;

    private static final int MAX_MONTH_BUCKETS = 24;
    private static final int MAX_DAY_BUCKETS = 62;

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

    public InventoryInfoResponse getInventoryInfo(Long branchId) {
        Long finalBranchId = resolveBranchId(branchId);

        long totalItems = inventoryRepository.countDistinctProducts(finalBranchId);
        long lowStockCount = inventoryRepository.getLowStockProductIds(lowStockThreshold, finalBranchId).size();
        long outOfStockCount = (finalBranchId == null)
                ? inventoryRepository.countOutOfStockProducts(null)
                : inventoryRepository.countOutOfStockProductsForBranch(finalBranchId);
        BigDecimal totalValue = getSafeBigDecimal(inventoryRepository.sumTotalValue(finalBranchId));

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

    public List<TopProductResponse> getTopProducts(Long branchId, int limit) {
        Long finalBranchId = resolveBranchId(branchId);

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

    public List<CategoryDistributionResponse> getCategoryDistribution(Long branchId) {
        Long finalBranchId = resolveBranchId(branchId);

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

