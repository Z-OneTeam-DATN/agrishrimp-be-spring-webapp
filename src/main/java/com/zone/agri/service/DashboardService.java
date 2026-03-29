package com.zone.agri.service;

import com.zone.agri.common.AuthUtils;
import com.zone.agri.dto.response.dashboard.*;
import com.zone.agri.dto.response.user.UserDetail;
import com.zone.agri.entity.Order;
import com.zone.agri.entity.User;
import com.zone.agri.entity.InventoryNote;
import com.zone.agri.entity.SubOrder;
import com.zone.agri.entity.enums.OrderStatus;
import com.zone.agri.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
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

    private BigDecimal getSafeBigDecimal(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private Long resolveBranchId(Long requestBranchId) {
        UserDetail currentUser = AuthUtils.getUserDetail();
        if (currentUser == null) throw new AccessDeniedException("Người dùng chưa đăng nhập.");

        String roleSlug = currentUser.getRole().getSlug();
        
        if (roleSlug.equals("ADMIN") || roleSlug.equals("SUPER_ADMIN")) {
            return requestBranchId;
        }

        Long userBranchId = currentUser.getBranchId();
        if (userBranchId == null) throw new AccessDeniedException("Người dùng không thuộc chi nhánh nào.");

        return userBranchId;
    }

    // 2.1 TỔNG QUAN CHỈ SỐ
    public DashboardStatsResponse getStats(Long branchId) {
        Long finalBranchId = resolveBranchId(branchId);
        
        LocalDateTime startOfTime = LocalDateTime.of(2000, 1, 1, 0, 0);
        LocalDateTime now = LocalDateTime.now();

        long totalOrders;
        BigDecimal totalRevenue;

        if (finalBranchId != null) {
            // Lấy từ SubOrder cho từng chi nhánh
            totalOrders = subOrderRepository.countAllByBranchIdExceptCancelled(finalBranchId);
            totalRevenue = getSafeBigDecimal(subOrderRepository.sumRevenueByBranchId(startOfTime, now, finalBranchId));
        } else {
            // Lấy từ Order cho toàn hệ thống
            totalOrders = orderRepository.countAllOrdersExceptCancelled(null);
            totalRevenue = getSafeBigDecimal(orderRepository.sumTotalRevenue(startOfTime, now, null));
        }
        
        long totalCustomers = userRepository.countCustomers(finalBranchId);
        long totalProducts = productRepository.countActiveProducts();

        return DashboardStatsResponse.builder()
                .totalOrders(totalOrders)
                .totalRevenue(totalRevenue)
                .totalCustomers(totalCustomers)
                .totalProducts(totalProducts)
                .build();
    }

    private double calculateGrowthPercent(BigDecimal today, BigDecimal yesterday) {
        if (yesterday == null || yesterday.compareTo(BigDecimal.ZERO) == 0) {
            return (today != null && today.compareTo(BigDecimal.ZERO) > 0) ? 100.0 : 0.0;
        }
        return today.subtract(yesterday)
                .divide(yesterday, 4, java.math.RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .doubleValue();
    }

    private double calculateGrowthPercent(long today, long yesterday) {
        if (yesterday == 0) {
            return today > 0 ? 100.0 : 0.0;
        }
        return ((double) (today - yesterday) / yesterday) * 100.0;
    }

    // 2.2 KẾT QUẢ KINH DOANH NGÀY
    public DailyBusinessResultsResponse getDailyResults(Long branchId) {
        Long finalBranchId = resolveBranchId(branchId);
        
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime todayEnd = LocalDateTime.now();
        
        LocalDateTime yesterdayStart = LocalDate.now().minusDays(1).atStartOfDay();
        LocalDateTime yesterdayEnd = LocalDate.now().minusDays(1).atTime(java.time.LocalTime.MAX);

        BigDecimal todayRevenue, todayCost, yesterdayRevenue, yesterdayCost;
        long todayOrders, yesterdayOrders;

        if (finalBranchId != null) {
            todayRevenue = getSafeBigDecimal(subOrderRepository.sumRevenueByBranchId(todayStart, todayEnd, finalBranchId));
            todayCost = getSafeBigDecimal(orderRepository.sumTotalCost(todayStart, todayEnd, finalBranchId)); // Cost vẫn dùng logic InventoryTransaction + branchId
            todayOrders = subOrderRepository.countSuccessByBranchId(todayStart, todayEnd, finalBranchId);

            yesterdayRevenue = getSafeBigDecimal(subOrderRepository.sumRevenueByBranchId(yesterdayStart, yesterdayEnd, finalBranchId));
            yesterdayCost = getSafeBigDecimal(orderRepository.sumTotalCost(yesterdayStart, yesterdayEnd, finalBranchId));
            yesterdayOrders = subOrderRepository.countSuccessByBranchId(yesterdayStart, yesterdayEnd, finalBranchId);
        } else {
            todayRevenue = getSafeBigDecimal(orderRepository.sumTotalRevenue(todayStart, todayEnd, null));
            todayCost = getSafeBigDecimal(orderRepository.sumTotalCost(todayStart, todayEnd, null));
            todayOrders = orderRepository.countSuccessOrders(todayStart, todayEnd, null);

            yesterdayRevenue = getSafeBigDecimal(orderRepository.sumTotalRevenue(yesterdayStart, yesterdayEnd, null));
            yesterdayCost = getSafeBigDecimal(orderRepository.sumTotalCost(yesterdayStart, yesterdayEnd, null));
            yesterdayOrders = orderRepository.countSuccessOrders(yesterdayStart, yesterdayEnd, null);
        }

        BigDecimal todayProfit = todayRevenue.subtract(todayCost);
        BigDecimal yesterdayProfit = yesterdayRevenue.subtract(yesterdayCost);

        return DailyBusinessResultsResponse.builder()
                .todayRevenue(todayRevenue)
                .yesterdayRevenue(yesterdayRevenue)
                .revenueChangePercent(calculateGrowthPercent(todayRevenue, yesterdayRevenue))
                .todayProfit(todayProfit)
                .yesterdayProfit(yesterdayProfit)
                .profitChangePercent(calculateGrowthPercent(todayProfit, yesterdayProfit))
                .todayOrders(todayOrders)
                .yesterdayOrders(yesterdayOrders)
                .orderChangePercent(calculateGrowthPercent(todayOrders, yesterdayOrders))
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

        List<Object[]> statsRaw;
        List<Object[]> costsRaw = orderRepository.getDailyCosts(startDate, endDate, finalBranchId);

        if (finalBranchId != null) {
            statsRaw = subOrderRepository.getDailyStatsByBranchId(startDate, endDate, finalBranchId);
        } else {
            statsRaw = orderRepository.getDailyStats(startDate, endDate, null);
        }

        Map<LocalDate, BigDecimal> revenueMap = new HashMap<>();
        Map<LocalDate, Long> orderCountMap = new HashMap<>();
        Map<LocalDate, BigDecimal> costMap = new HashMap<>();

        for (Object[] row : statsRaw) {
            LocalDate date = ((java.sql.Date) row[0]).toLocalDate();
            revenueMap.put(date, (BigDecimal) row[1]);
            orderCountMap.put(date, (Long) row[2]);
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
        long lowStockCount = inventoryRepository.getLowStockProductIds(10, finalBranchId).size();
        long outOfStockCount = inventoryRepository.countOutOfStockProducts(finalBranchId);
        BigDecimal totalValue = getSafeBigDecimal(inventoryRepository.sumTotalValue(finalBranchId));

        return InventoryInfoResponse.builder()
                .totalItems(totalItems)
                .lowStockCount(lowStockCount)
                .outOfStockCount(outOfStockCount)
                .totalInventoryValue(totalValue)
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
