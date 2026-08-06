package com.zone.agri.controller;

import com.zone.agri.dto.response.product.ProductVariantResponse;
import com.zone.agri.service.ProductVariantService;
import com.zone.agri.dto.response.product.LowStockReportResponse;
import com.zone.agri.security.annotation.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/product-variants")
@RequiredArgsConstructor
@Tag(name = "Product Variant Management", description = "Quản lý biến thể sản phẩm")
public class ProductVariantController {

    private final ProductVariantService variantService;

    @Operation(summary = "Báo cáo sản phẩm dưới định mức (Low Stock Report)")
    @RequirePermission("REPORT_INVENTORY_VIEW")
    @GetMapping("/low-stock")
    public ResponseEntity<List<LowStockReportResponse>> getLowStockReport(
            @RequestParam(required = false) Long branchId) {
        Long finalBranchId = com.zone.agri.common.AuthUtils.resolveRequestedOrUserBranch(
                branchId, "REPORT_INVENTORY_VIEW", "REPORT_INVENTORY_VIEW_ALL_BRANCHES");
        return ResponseEntity.ok(variantService.getLowStockReport(finalBranchId));
    }

    @Operation(summary = "Tìm kiếm biến thể sản phẩm (Dùng cho tạo đơn/chuyển kho)")
    @GetMapping("/search")
    // [CẬP NHẬT QUAN TRỌNG]: Nhận thêm branchId (không bắt buộc) và đổi kiểu trả về
    public ResponseEntity<List<ProductVariantResponse>> searchVariants(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) String supplierCode) {

        return ResponseEntity.ok(variantService.searchVariants(keyword, branchId, supplierCode));
    }
}
