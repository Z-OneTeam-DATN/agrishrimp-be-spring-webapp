package com.zone.agri.service;

import com.zone.agri.common.AuthUtils;
import com.zone.agri.dto.response.report.SalesReportDetailResponse;
import com.zone.agri.dto.response.report.SalesReportSummaryResponse;
import com.zone.agri.dto.response.user.UserDetail;
import com.zone.agri.entity.Order;
import com.zone.agri.entity.SubOrder;
import com.zone.agri.entity.SubOrderItem;
import com.zone.agri.entity.User;
import com.zone.agri.entity.enums.OrderStatus;
import com.zone.agri.entity.enums.PaymentMethod;
import com.zone.agri.entity.enums.PaymentStatus;
import com.zone.agri.repository.BranchRepository;
import com.zone.agri.repository.OrderRepository;
import com.zone.agri.repository.SubOrderRepository;
import com.zone.agri.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SalesReportService {

    private static final Set<OrderStatus> SUCCESS_STATUSES = Set.of(OrderStatus.COMPLETED, OrderStatus.SHIPPING);

    private final SubOrderRepository subOrderRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final BranchRepository branchRepository;

    public SalesReportSummaryResponse getSummary(LocalDate startDate, LocalDate endDate, Long branchId) {
        Long finalBranchId = resolveBranchId(branchId);
        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atTime(LocalTime.MAX);
        List<SubOrder> subOrders = subOrderRepository.findReportData(startDateTime, endDateTime, finalBranchId);
        List<SubOrder> successfulSubOrders = subOrders.stream()
                .filter(item -> SUCCESS_STATUSES.contains(item.getStatus()))
                .toList();
        List<SubOrder> returnedSubOrders = subOrders.stream()
                .filter(item -> item.getStatus() == OrderStatus.RETURNED)
                .toList();

        List<SalesReportSummaryResponse.TrendPoint> trend = buildTrend(startDate, endDate, finalBranchId);
        BigDecimal totalRevenue = successfulSubOrders.stream()
                .map(this::getSubOrderAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalProfit = trend.stream()
                .map(SalesReportSummaryResponse.TrendPoint::getProfit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<OrderStatus, Long> deliveryBreakdown = subOrders.stream()
                .collect(Collectors.groupingBy(SubOrder::getStatus, () -> new EnumMap<>(OrderStatus.class), Collectors.counting()));

        Map<Long, Order> uniqueOrders = subOrders.stream()
                .map(SubOrder::getOrder)
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(Order::getId, Function.identity(), (left, right) -> left, LinkedHashMap::new));

        BigDecimal paidAmount = successfulSubOrders.stream()
                .filter(item -> item.getOrder() != null && item.getOrder().getPaymentStatus() == PaymentStatus.PAID)
                .map(this::getSubOrderAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long paidOrders = uniqueOrders.values().stream()
                .filter(order -> order.getPaymentStatus() == PaymentStatus.PAID)
                .count();
        long unpaidOrders = uniqueOrders.values().stream()
                .filter(order -> order.getPaymentStatus() != PaymentStatus.PAID)
                .count();

        long totalProductsSold = successfulSubOrders.stream()
                .flatMap(item -> safeItems(item).stream())
                .mapToLong(item -> item.getQuantity() == null ? 0 : item.getQuantity())
                .sum();

        long totalSuccessOrders = successfulSubOrders.size();
        BigDecimal averageOrderValue = totalSuccessOrders == 0
                ? BigDecimal.ZERO
                : totalRevenue.divide(BigDecimal.valueOf(totalSuccessOrders), 2, RoundingMode.HALF_UP);

        return SalesReportSummaryResponse.builder()
                .startDate(startDate)
                .endDate(endDate)
                .branchId(finalBranchId)
                .branchName(resolveBranchName(finalBranchId))
                .revenue(SalesReportSummaryResponse.RevenueSection.builder()
                        .totalRevenue(totalRevenue)
                        .totalProfit(totalProfit)
                        .totalOrders(totalSuccessOrders)
                        .trend(trend)
                        .build())
                .delivery(SalesReportSummaryResponse.DeliverySection.builder()
                        .totalShipments(subOrders.stream().filter(item -> item.getStatus() != OrderStatus.CANCELLED).count())
                        .breakdown(deliveryBreakdown.entrySet().stream()
                                .sorted(Map.Entry.comparingByKey())
                                .map(entry -> SalesReportSummaryResponse.BreakdownItem.builder()
                                        .key(entry.getKey().name())
                                        .label(formatStatus(entry.getKey()))
                                        .count(entry.getValue())
                                        .build())
                                .toList())
                        .build())
                .returns(SalesReportSummaryResponse.ReturnSection.builder()
                        .totalReturnedOrders(returnedSubOrders.size())
                        .totalReturnedAmount(returnedSubOrders.stream()
                                .map(this::getSubOrderAmount)
                                .reduce(BigDecimal.ZERO, BigDecimal::add))
                        .build())
                .payment(SalesReportSummaryResponse.PaymentSection.builder()
                        .paidAmount(paidAmount)
                        .paidOrders(paidOrders)
                        .unpaidOrders(unpaidOrders)
                        .build())
                .orders(SalesReportSummaryResponse.OrderSection.builder()
                        .totalOrders(uniqueOrders.size())
                        .totalProductsSold(totalProductsSold)
                        .averageOrderValue(averageOrderValue)
                        .build())
                .build();
    }

    public SalesReportDetailResponse getDetail(String type, LocalDate startDate, LocalDate endDate, Long branchId) {
        Long finalBranchId = resolveBranchId(branchId);
        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atTime(LocalTime.MAX);
        List<SubOrder> subOrders = subOrderRepository.findReportData(startDateTime, endDateTime, finalBranchId);
        List<SalesReportSummaryResponse.TrendPoint> trend = buildTrend(startDate, endDate, finalBranchId);
        Map<Long, String> employeeNames = resolveEmployeeNames(subOrders);

        return switch (type) {
            case "revenue_time" -> buildRevenueTimeDetail(trend);
            case "revenue_employee" -> buildRevenueEmployeeDetail(subOrders, employeeNames);
            case "delivery_detail" -> buildDeliveryDetail(subOrders);
            case "returns_by_order" -> buildReturnByOrderDetail(subOrders);
            case "returns_by_product" -> buildReturnByProductDetail(subOrders);
            case "payment_time" -> buildPaymentTimeDetail(subOrders);
            case "payment_employee" -> buildPaymentEmployeeDetail(subOrders, employeeNames);
            case "payment_method" -> buildPaymentMethodDetail(subOrders);
            case "payment_branch" -> buildPaymentBranchDetail(subOrders);
            case "order_stats" -> buildOrderStatsDetail(subOrders);
            case "order_product" -> buildOrderProductDetail(subOrders);
            case "order_detail" -> buildOrderDetail(subOrders);
            default -> throw new IllegalArgumentException("Unsupported sales report detail type: " + type);
        };
    }

    private SalesReportDetailResponse buildRevenueTimeDetail(List<SalesReportSummaryResponse.TrendPoint> trend) {
        List<Map<String, Object>> rows = trend.stream()
                .map(point -> row(
                        "date", point.getDate(),
                        "revenue", point.getRevenue(),
                        "profit", point.getProfit(),
                        "orderCount", point.getOrderCount()
                ))
                .toList();

        return detail(
                "revenue_time",
                "Báo cáo doanh thu theo thời gian",
                "Theo dõi doanh thu, lợi nhuận và số đơn theo từng ngày trong kỳ.",
                columns(
                        column("date", "Ngày", "left"),
                        column("revenue", "Doanh thu", "right"),
                        column("profit", "Lợi nhuận", "right"),
                        column("orderCount", "Số đơn", "right")
                ),
                rows,
                row(
                        "totalRevenue", trend.stream().map(SalesReportSummaryResponse.TrendPoint::getRevenue).reduce(BigDecimal.ZERO, BigDecimal::add),
                        "totalProfit", trend.stream().map(SalesReportSummaryResponse.TrendPoint::getProfit).reduce(BigDecimal.ZERO, BigDecimal::add),
                        "totalOrders", trend.stream().mapToLong(SalesReportSummaryResponse.TrendPoint::getOrderCount).sum()
                )
        );
    }

    private SalesReportDetailResponse buildRevenueEmployeeDetail(List<SubOrder> subOrders, Map<Long, String> employeeNames) {
        List<Map<String, Object>> rows = subOrders.stream()
                .filter(item -> SUCCESS_STATUSES.contains(item.getStatus()))
                .collect(Collectors.groupingBy(
                        item -> item.getCreatedByUserId() == null ? 0L : item.getCreatedByUserId(),
                        LinkedHashMap::new,
                        Collectors.toList()))
                .entrySet().stream()
                .map(entry -> {
                    List<SubOrder> items = entry.getValue();
                    BigDecimal revenue = items.stream().map(this::getSubOrderAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
                    long orderCount = items.size();
                    BigDecimal avg = orderCount == 0 ? BigDecimal.ZERO :
                            revenue.divide(BigDecimal.valueOf(orderCount), 2, RoundingMode.HALF_UP);
                    return row(
                            "employeeName", employeeNames.getOrDefault(entry.getKey(), "Hệ thống"),
                            "branchNames", items.stream().map(item -> item.getBranch() != null ? item.getBranch().getName() : "N/A").distinct().collect(Collectors.joining(", ")),
                            "orderCount", orderCount,
                            "revenue", revenue,
                            "averageOrderValue", avg
                    );
                })
                .sorted((a, b) -> new BigDecimal(String.valueOf(b.get("revenue"))).compareTo(new BigDecimal(String.valueOf(a.get("revenue")))))
                .toList();

        return detail(
                "revenue_employee",
                "Báo cáo doanh thu theo nhân viên",
                "Tổng hợp số đơn và doanh thu do từng nhân viên tạo trong kỳ.",
                columns(
                        column("employeeName", "Nhân viên", "left"),
                        column("branchNames", "Chi nhánh", "left"),
                        column("orderCount", "Số đơn", "right"),
                        column("revenue", "Doanh thu", "right"),
                        column("averageOrderValue", "Trung bình/đơn", "right")
                ),
                rows,
                row("employees", rows.size())
        );
    }

    private SalesReportDetailResponse buildDeliveryDetail(List<SubOrder> subOrders) {
        List<Map<String, Object>> rows = subOrders.stream()
                .map(item -> row(
                        "createdAt", item.getCreatedAt(),
                        "orderCode", item.getOrder() != null ? item.getOrder().getCode() : "N/A",
                        "customerName", item.getOrder() != null && item.getOrder().getUser() != null ? item.getOrder().getUser().getFullName() : "Khách vãng lai",
                        "branchName", item.getBranch() != null ? item.getBranch().getName() : "N/A",
                        "status", formatStatus(item.getStatus()),
                        "carrier", item.getCarrier() == null ? "N/A" : item.getCarrier(),
                        "estimatedDays", item.getEstimatedDays() == null ? "N/A" : item.getEstimatedDays(),
                        "amount", getSubOrderAmount(item)
                ))
                .toList();

        return detail(
                "delivery_detail",
                "Báo cáo giao hàng chi tiết",
                "Danh sách các đơn giao hàng theo trạng thái xử lý trong kỳ.",
                columns(
                        column("createdAt", "Thời gian", "left"),
                        column("orderCode", "Mã đơn", "left"),
                        column("customerName", "Khách hàng", "left"),
                        column("branchName", "Chi nhánh", "left"),
                        column("status", "Trạng thái", "left"),
                        column("carrier", "Đơn vị VC", "left"),
                        column("estimatedDays", "Dự kiến giao", "left"),
                        column("amount", "Giá trị", "right")
                ),
                rows,
                row("totalShipments", rows.size())
        );
    }

    private SalesReportDetailResponse buildReturnByOrderDetail(List<SubOrder> subOrders) {
        List<Map<String, Object>> rows = subOrders.stream()
                .filter(item -> item.getStatus() == OrderStatus.RETURNED)
                .map(item -> row(
                        "createdAt", item.getCreatedAt(),
                        "orderCode", item.getOrder() != null ? item.getOrder().getCode() : "N/A",
                        "customerName", item.getOrder() != null && item.getOrder().getUser() != null ? item.getOrder().getUser().getFullName() : "Khách vãng lai",
                        "branchName", item.getBranch() != null ? item.getBranch().getName() : "N/A",
                        "paymentMethod", item.getOrder() != null ? formatPaymentMethod(item.getOrder().getPaymentMethod()) : "N/A",
                        "returnAmount", getSubOrderAmount(item)
                ))
                .toList();

        return detail(
                "returns_by_order",
                "Trả hàng theo đơn hàng",
                "Danh sách các đơn đã được đánh dấu trả hàng trong kỳ.",
                columns(
                        column("createdAt", "Thời gian", "left"),
                        column("orderCode", "Mã đơn", "left"),
                        column("customerName", "Khách hàng", "left"),
                        column("branchName", "Chi nhánh", "left"),
                        column("paymentMethod", "Thanh toán", "left"),
                        column("returnAmount", "Giá trị trả", "right")
                ),
                rows,
                row("totalReturnedAmount", rows.stream()
                        .map(item -> (BigDecimal) item.get("returnAmount"))
                        .reduce(BigDecimal.ZERO, BigDecimal::add))
        );
    }

    private SalesReportDetailResponse buildReturnByProductDetail(List<SubOrder> subOrders) {
        List<Map<String, Object>> rows = subOrders.stream()
                .filter(item -> item.getStatus() == OrderStatus.RETURNED)
                .flatMap(item -> safeItems(item).stream())
                .collect(Collectors.groupingBy(
                        item -> item.getProductVariant().getId(),
                        LinkedHashMap::new,
                        Collectors.toList()))
                .values().stream()
                .map(items -> {
                    SubOrderItem first = items.get(0);
                    long quantity = items.stream().mapToLong(item -> item.getQuantity() == null ? 0 : item.getQuantity()).sum();
                    BigDecimal amount = items.stream()
                            .map(item -> safeMultiply(item.getUnitPrice(), item.getQuantity()))
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    return row(
                            "productName", first.getProductVariant().getProduct().getName(),
                            "sku", first.getProductVariant().getSku(),
                            "quantity", quantity,
                            "returnAmount", amount
                    );
                })
                .sorted((a, b) -> new BigDecimal(String.valueOf(b.get("returnAmount"))).compareTo(new BigDecimal(String.valueOf(a.get("returnAmount")))))
                .toList();

        return detail(
                "returns_by_product",
                "Trả hàng theo sản phẩm",
                "Tổng hợp các sản phẩm bị trả trong kỳ.",
                columns(
                        column("productName", "Sản phẩm", "left"),
                        column("sku", "SKU", "left"),
                        column("quantity", "Số lượng trả", "right"),
                        column("returnAmount", "Giá trị trả", "right")
                ),
                rows,
                row("products", rows.size())
        );
    }

    private SalesReportDetailResponse buildPaymentTimeDetail(List<SubOrder> subOrders) {
        List<Map<String, Object>> rows = subOrders.stream()
                .filter(item -> SUCCESS_STATUSES.contains(item.getStatus()))
                .collect(Collectors.groupingBy(item -> item.getCreatedAt().toLocalDate(), LinkedHashMap::new, Collectors.toList()))
                .entrySet().stream()
                .map(entry -> row(
                        "date", entry.getKey(),
                        "paidOrders", entry.getValue().stream().filter(item -> item.getOrder() != null && item.getOrder().getPaymentStatus() == PaymentStatus.PAID).count(),
                        "unpaidOrders", entry.getValue().stream().filter(item -> item.getOrder() == null || item.getOrder().getPaymentStatus() != PaymentStatus.PAID).count(),
                        "paidAmount", entry.getValue().stream()
                                .filter(item -> item.getOrder() != null && item.getOrder().getPaymentStatus() == PaymentStatus.PAID)
                                .map(this::getSubOrderAmount)
                                .reduce(BigDecimal.ZERO, BigDecimal::add)
                ))
                .sorted(Comparator.comparing(entry -> (LocalDate) entry.get("date")))
                .toList();

        return detail(
                "payment_time",
                "Báo cáo thanh toán theo thời gian",
                "Theo dõi lượng đơn đã thanh toán và giá trị thanh toán theo ngày.",
                columns(
                        column("date", "Ngày", "left"),
                        column("paidOrders", "Đã thanh toán", "right"),
                        column("unpaidOrders", "Chưa thanh toán", "right"),
                        column("paidAmount", "Giá trị đã thu", "right")
                ),
                rows,
                row("totalPaidAmount", rows.stream()
                        .map(item -> (BigDecimal) item.get("paidAmount"))
                        .reduce(BigDecimal.ZERO, BigDecimal::add))
        );
    }

    private SalesReportDetailResponse buildPaymentEmployeeDetail(List<SubOrder> subOrders, Map<Long, String> employeeNames) {
        List<Map<String, Object>> rows = subOrders.stream()
                .filter(item -> SUCCESS_STATUSES.contains(item.getStatus()))
                .collect(Collectors.groupingBy(
                        item -> item.getCreatedByUserId() == null ? 0L : item.getCreatedByUserId(),
                        LinkedHashMap::new,
                        Collectors.toList()))
                .entrySet().stream()
                .map(entry -> {
                    List<SubOrder> items = entry.getValue();
                    BigDecimal paidAmount = items.stream()
                            .filter(item -> item.getOrder() != null && item.getOrder().getPaymentStatus() == PaymentStatus.PAID)
                            .map(this::getSubOrderAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    return row(
                            "employeeName", employeeNames.getOrDefault(entry.getKey(), "Hệ thống"),
                            "paidOrders", items.stream().filter(item -> item.getOrder() != null && item.getOrder().getPaymentStatus() == PaymentStatus.PAID).count(),
                            "unpaidOrders", items.stream().filter(item -> item.getOrder() == null || item.getOrder().getPaymentStatus() != PaymentStatus.PAID).count(),
                            "paidAmount", paidAmount
                    );
                })
                .toList();

        return detail(
                "payment_employee",
                "Báo cáo thanh toán theo nhân viên",
                "Tổng hợp hiệu quả thu tiền theo nhân viên tạo đơn.",
                columns(
                        column("employeeName", "Nhân viên", "left"),
                        column("paidOrders", "Đơn đã thu", "right"),
                        column("unpaidOrders", "Đơn chưa thu", "right"),
                        column("paidAmount", "Giá trị đã thu", "right")
                ),
                rows,
                row("employees", rows.size())
        );
    }

    private SalesReportDetailResponse buildPaymentMethodDetail(List<SubOrder> subOrders) {
        List<Map<String, Object>> rows = subOrders.stream()
                .filter(item -> SUCCESS_STATUSES.contains(item.getStatus()))
                .collect(Collectors.groupingBy(
                        item -> item.getOrder() == null ? null : item.getOrder().getPaymentMethod(),
                        LinkedHashMap::new,
                        Collectors.toList()))
                .entrySet().stream()
                .map(entry -> row(
                        "paymentMethod", formatPaymentMethod(entry.getKey()),
                        "orderCount", entry.getValue().size(),
                        "paidOrders", entry.getValue().stream().filter(item -> item.getOrder() != null && item.getOrder().getPaymentStatus() == PaymentStatus.PAID).count(),
                        "amount", entry.getValue().stream().map(this::getSubOrderAmount).reduce(BigDecimal.ZERO, BigDecimal::add)
                ))
                .toList();

        return detail(
                "payment_method",
                "Báo cáo theo phương thức thanh toán",
                "So sánh số đơn và doanh thu theo phương thức thanh toán.",
                columns(
                        column("paymentMethod", "Phương thức", "left"),
                        column("orderCount", "Số đơn", "right"),
                        column("paidOrders", "Đơn đã thu", "right"),
                        column("amount", "Doanh thu", "right")
                ),
                rows,
                row("methods", rows.size())
        );
    }

    private SalesReportDetailResponse buildPaymentBranchDetail(List<SubOrder> subOrders) {
        List<Map<String, Object>> rows = subOrders.stream()
                .filter(item -> SUCCESS_STATUSES.contains(item.getStatus()))
                .collect(Collectors.groupingBy(
                        item -> item.getBranch() == null ? "N/A" : item.getBranch().getName(),
                        LinkedHashMap::new,
                        Collectors.toList()))
                .entrySet().stream()
                .map(entry -> row(
                        "branchName", entry.getKey(),
                        "orderCount", entry.getValue().size(),
                        "paidOrders", entry.getValue().stream().filter(item -> item.getOrder() != null && item.getOrder().getPaymentStatus() == PaymentStatus.PAID).count(),
                        "paidAmount", entry.getValue().stream()
                                .filter(item -> item.getOrder() != null && item.getOrder().getPaymentStatus() == PaymentStatus.PAID)
                                .map(this::getSubOrderAmount)
                                .reduce(BigDecimal.ZERO, BigDecimal::add)
                ))
                .toList();

        return detail(
                "payment_branch",
                "Báo cáo thanh toán theo chi nhánh",
                "Tổng hợp tình hình thanh toán của từng chi nhánh.",
                columns(
                        column("branchName", "Chi nhánh", "left"),
                        column("orderCount", "Số đơn", "right"),
                        column("paidOrders", "Đơn đã thu", "right"),
                        column("paidAmount", "Giá trị đã thu", "right")
                ),
                rows,
                row("branches", rows.size())
        );
    }

    private SalesReportDetailResponse buildOrderStatsDetail(List<SubOrder> subOrders) {
        List<Map<String, Object>> rows = subOrders.stream()
                .collect(Collectors.groupingBy(SubOrder::getStatus, LinkedHashMap::new, Collectors.toList()))
                .entrySet().stream()
                .map(entry -> row(
                        "status", formatStatus(entry.getKey()),
                        "orderCount", entry.getValue().size(),
                        "revenue", entry.getValue().stream().map(this::getSubOrderAmount).reduce(BigDecimal.ZERO, BigDecimal::add),
                        "productCount", entry.getValue().stream()
                                .flatMap(item -> safeItems(item).stream())
                                .mapToLong(item -> item.getQuantity() == null ? 0 : item.getQuantity())
                                .sum()
                ))
                .toList();

        return detail(
                "order_stats",
                "Báo cáo thống kê theo đơn hàng",
                "Tổng hợp số lượng đơn và giá trị đơn theo từng trạng thái.",
                columns(
                        column("status", "Trạng thái", "left"),
                        column("orderCount", "Số đơn", "right"),
                        column("revenue", "Giá trị", "right"),
                        column("productCount", "Số lượng SP", "right")
                ),
                rows,
                row("statuses", rows.size())
        );
    }

    private SalesReportDetailResponse buildOrderProductDetail(List<SubOrder> subOrders) {
        List<Map<String, Object>> rows = subOrders.stream()
                .filter(item -> SUCCESS_STATUSES.contains(item.getStatus()))
                .flatMap(item -> safeItems(item).stream())
                .collect(Collectors.groupingBy(item -> item.getProductVariant().getId(), LinkedHashMap::new, Collectors.toList()))
                .values().stream()
                .map(items -> {
                    SubOrderItem first = items.get(0);
                    long quantity = items.stream().mapToLong(item -> item.getQuantity() == null ? 0 : item.getQuantity()).sum();
                    BigDecimal revenue = items.stream()
                            .map(item -> safeMultiply(item.getUnitPrice(), item.getQuantity()))
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    return row(
                            "productName", first.getProductVariant().getProduct().getName(),
                            "sku", first.getProductVariant().getSku(),
                            "quantitySold", quantity,
                            "revenue", revenue
                    );
                })
                .sorted((a, b) -> new BigDecimal(String.valueOf(b.get("revenue"))).compareTo(new BigDecimal(String.valueOf(a.get("revenue")))))
                .toList();

        return detail(
                "order_product",
                "Báo cáo thống kê theo sản phẩm",
                "Theo dõi sản lượng và doanh thu của từng sản phẩm bán ra.",
                columns(
                        column("productName", "Sản phẩm", "left"),
                        column("sku", "SKU", "left"),
                        column("quantitySold", "Số lượng bán", "right"),
                        column("revenue", "Doanh thu", "right")
                ),
                rows,
                row("products", rows.size())
        );
    }

    private SalesReportDetailResponse buildOrderDetail(List<SubOrder> subOrders) {
        List<Map<String, Object>> rows = subOrders.stream()
                .filter(item -> SUCCESS_STATUSES.contains(item.getStatus()))
                .map(item -> row(
                        "createdAt", item.getCreatedAt(),
                        "orderCode", item.getOrder() != null ? item.getOrder().getCode() : "N/A",
                        "customerName", item.getOrder() != null && item.getOrder().getUser() != null ? item.getOrder().getUser().getFullName() : "Khách vãng lai",
                        "branchName", item.getBranch() != null ? item.getBranch().getName() : "N/A",
                        "paymentMethod", item.getOrder() != null ? formatPaymentMethod(item.getOrder().getPaymentMethod()) : "N/A",
                        "status", formatStatus(item.getStatus()),
                        "amount", getSubOrderAmount(item)
                ))
                .toList();

        return detail(
                "order_detail",
                "Báo cáo bán hàng chi tiết",
                "Danh sách đơn bán hàng thành công trong kỳ.",
                columns(
                        column("createdAt", "Thời gian", "left"),
                        column("orderCode", "Mã đơn", "left"),
                        column("customerName", "Khách hàng", "left"),
                        column("branchName", "Chi nhánh", "left"),
                        column("paymentMethod", "Thanh toán", "left"),
                        column("status", "Trạng thái", "left"),
                        column("amount", "Giá trị đơn", "right")
                ),
                rows,
                row("orders", rows.size())
        );
    }

    private List<SalesReportSummaryResponse.TrendPoint> buildTrend(LocalDate startDate, LocalDate endDate, Long branchId) {
        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atTime(LocalTime.MAX);
        List<Object[]> revenueRows;
        List<Object[]> costRows;

        if (branchId != null) {
            revenueRows = subOrderRepository.getDailyStatsByBranchId(startDateTime, endDateTime, branchId);
            costRows = subOrderRepository.getDailyCostsByBranchId(startDateTime, endDateTime, branchId);
        } else {
            revenueRows = orderRepository.getDailyStats(startDateTime, endDateTime, null);
            costRows = orderRepository.getDailyCosts(startDateTime, endDateTime, null);
        }

        Map<LocalDate, BigDecimal> revenueMap = new LinkedHashMap<>();
        Map<LocalDate, Long> orderCountMap = new LinkedHashMap<>();
        Map<LocalDate, BigDecimal> costMap = new LinkedHashMap<>();

        for (Object[] row : revenueRows) {
            LocalDate date = ((java.sql.Date) row[0]).toLocalDate();
            revenueMap.put(date, row[1] == null ? BigDecimal.ZERO : (BigDecimal) row[1]);
            orderCountMap.put(date, row[2] == null ? 0L : ((Number) row[2]).longValue());
        }

        for (Object[] row : costRows) {
            LocalDate date = ((java.sql.Date) row[0]).toLocalDate();
            costMap.put(date, row[1] == null ? BigDecimal.ZERO : (BigDecimal) row[1]);
        }

        List<SalesReportSummaryResponse.TrendPoint> trend = new ArrayList<>();
        for (LocalDate cursor = startDate; !cursor.isAfter(endDate); cursor = cursor.plusDays(1)) {
            BigDecimal revenue = revenueMap.getOrDefault(cursor, BigDecimal.ZERO);
            BigDecimal cost = costMap.getOrDefault(cursor, BigDecimal.ZERO);
            trend.add(SalesReportSummaryResponse.TrendPoint.builder()
                    .date(cursor)
                    .revenue(revenue)
                    .profit(revenue.subtract(cost))
                    .orderCount(orderCountMap.getOrDefault(cursor, 0L))
                    .build());
        }
        return trend;
    }

    private Map<Long, String> resolveEmployeeNames(List<SubOrder> subOrders) {
        List<Long> ids = subOrders.stream()
                .map(SubOrder::getCreatedByUserId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }

        return userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(User::getId, User::getFullName));
    }

    private Long resolveBranchId(Long requestBranchId) {
        UserDetail currentUser = AuthUtils.getUserDetail();
        if (currentUser == null) {
            throw new AccessDeniedException("Người dùng chưa đăng nhập.");
        }

        String roleSlug = currentUser.getRole().getSlug();
        if ("ADMIN".equals(roleSlug) || "SUPER_ADMIN".equals(roleSlug)) {
            return requestBranchId;
        }

        Long userBranchId = currentUser.getBranchId();
        if (userBranchId == null) {
            throw new AccessDeniedException("Người dùng không thuộc chi nhánh nào.");
        }

        return userBranchId;
    }

    private String resolveBranchName(Long branchId) {
        if (branchId == null) {
            return "Tất cả chi nhánh";
        }
        return branchRepository.findById(branchId)
                .map(branch -> branch.getName())
                .orElse("Chi nhánh");
    }

    private BigDecimal getSubOrderAmount(SubOrder subOrder) {
        return safeBigDecimal(subOrder.getSubtotal()).add(safeBigDecimal(subOrder.getShippingFee()));
    }

    private BigDecimal safeBigDecimal(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private BigDecimal safeMultiply(BigDecimal price, Integer quantity) {
        return safeBigDecimal(price).multiply(BigDecimal.valueOf(quantity == null ? 0 : quantity));
    }

    private List<SubOrderItem> safeItems(SubOrder subOrder) {
        return subOrder.getItems() == null ? List.of() : subOrder.getItems();
    }

    private String formatStatus(OrderStatus status) {
        if (status == null) {
            return "N/A";
        }
        return switch (status) {
            case PENDING -> "Chờ xác nhận";
            case AWAITING_PAYMENT -> "Chờ thanh toán";
            case AWAITING_REPLENISHMENT -> "Chờ bổ sung";
            case CONFIRMED -> "Đã xác nhận";
            case PROCESSING -> "Đang xử lý";
            case READY_FOR_PICKUP -> "Sẵn sàng lấy";
            case SHIPPING -> "Đang giao";
            case COMPLETED -> "Hoàn thành";
            case CANCELLED -> "Đã hủy";
            case RETURNED -> "Đã trả hàng";
        };
    }

    private String formatPaymentMethod(PaymentMethod method) {
        if (method == null) {
            return "N/A";
        }
        return switch (method) {
            case COD -> "COD";
            case PAYOS -> "PayOS";
        };
    }

    private SalesReportDetailResponse detail(String type,
                                             String label,
                                             String description,
                                             List<SalesReportDetailResponse.Column> columns,
                                             List<Map<String, Object>> rows,
                                             Map<String, Object> totals) {
        return SalesReportDetailResponse.builder()
                .type(type)
                .label(label)
                .description(description)
                .columns(columns)
                .rows(rows)
                .totals(totals)
                .totalRows(rows.size())
                .build();
    }

    private List<SalesReportDetailResponse.Column> columns(SalesReportDetailResponse.Column... columns) {
        return List.of(columns);
    }

    private SalesReportDetailResponse.Column column(String key, String label, String align) {
        return SalesReportDetailResponse.Column.builder()
                .key(key)
                .label(label)
                .align(align)
                .build();
    }

    private Map<String, Object> row(Object... values) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) {
            result.put(String.valueOf(values[i]), values[i + 1]);
        }
        return result;
    }
}
