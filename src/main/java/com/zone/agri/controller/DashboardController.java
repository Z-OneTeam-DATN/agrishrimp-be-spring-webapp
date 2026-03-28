package com.zone.agri.controller;

import com.zone.agri.dto.response.dashboard.*;
import com.zone.agri.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard API", description = "API cho trang quản trị - Thống kê")
@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'SUPER_ADMIN', 'STAFF')")
public class DashboardController {

    private final DashboardService dashboardService;

    @Operation(summary = "Tổng quan các chỉ số (Stats)")
    @GetMapping("/stats")
    public ResponseEntity<DashboardStatsResponse> getStats(
            @RequestParam(required = false) Long branchId) {
        return ResponseEntity.ok(dashboardService.getStats(branchId));
    }

    @Operation(summary = "Kết quả kinh doanh ngày")
    @GetMapping("/daily-results")
    public ResponseEntity<DailyBusinessResultsResponse> getDailyResults(
            @RequestParam(required = false) Long branchId) {
        return ResponseEntity.ok(dashboardService.getDailyResults(branchId));
    }

    @Operation(summary = "Hoạt động gần đây")
    @GetMapping("/recent-activities")
    public ResponseEntity<List<RecentActivityResponse>> getRecentActivities(
            @RequestParam(required = false) Long branchId) {
        return ResponseEntity.ok(dashboardService.getRecentActivities(branchId));
    }

    @Operation(summary = "Thông tin kho hàng")
    @GetMapping("/inventory-info")
    public ResponseEntity<InventoryInfoResponse> getInventoryInfo(
            @RequestParam(required = false) Long branchId) {
        return ResponseEntity.ok(dashboardService.getInventoryInfo(branchId));
    }

    @Operation(summary = "Top sản phẩm bán chạy (30 ngày)")
    @GetMapping("/top-products")
    public ResponseEntity<List<TopProductResponse>> getTopProducts(
            @RequestParam(required = false) Long branchId,
            @RequestParam(defaultValue = "5") int limit) {
        return ResponseEntity.ok(dashboardService.getTopProducts(branchId, limit));
    }

    @Operation(summary = "Biểu đồ hiệu suất doanh số (7 ngày)")
    @GetMapping("/sales-performance")
    public ResponseEntity<SalesPerformanceResponse> getSalesPerformance(
            @RequestParam(required = false) Long branchId) {
        return ResponseEntity.ok(dashboardService.getSalesPerformance(branchId));
    }

    @Operation(summary = "Danh sách đơn hàng chờ duyệt")
    @GetMapping("/pending-orders")
    public ResponseEntity<List<PendingOrderResponse>> getPendingOrders(
            @RequestParam(required = false) Long branchId,
            @RequestParam(defaultValue = "5") int limit) {
        return ResponseEntity.ok(dashboardService.getPendingOrders(branchId, limit));
    }

    @Operation(summary = "Tóm tắt số lượng đơn hàng theo trạng thái")
    @GetMapping("/pending-orders-summary")
    public ResponseEntity<PendingOrdersSummaryResponse> getPendingOrdersSummary(
            @RequestParam(required = false) Long branchId) {
        return ResponseEntity.ok(dashboardService.getPendingOrdersSummary(branchId));
    }

    @Operation(summary = "Tỷ trọng doanh thu theo danh mục (Pie Chart)")
    @GetMapping("/category-distribution")
    public ResponseEntity<List<CategoryDistributionResponse>> getCategoryDistribution(
            @RequestParam(required = false) Long branchId) {
        return ResponseEntity.ok(dashboardService.getCategoryDistribution(branchId));
    }
}
