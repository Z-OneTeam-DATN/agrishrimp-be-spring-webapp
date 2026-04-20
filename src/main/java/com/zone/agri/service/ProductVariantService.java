package com.zone.agri.service;

import com.zone.agri.dto.response.product.ProductVariantResponse;
import com.zone.agri.entity.Inventory;
import com.zone.agri.entity.ProductVariant;
import com.zone.agri.entity.enums.BranchStatus;
import com.zone.agri.repository.InventoryRepository;
import com.zone.agri.repository.ProductVariantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import com.zone.agri.dto.response.product.LowStockReportResponse;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductVariantService {

    private final ProductVariantRepository variantRepo;
    private final InventoryRepository inventoryRepo;
    /**
     * BÁO CÁO SẢN PHẨM DƯỚI ĐỊNH MỨC (LOW STOCK REPORT)
     * Chỉ lấy các biến thể có tổng tồn kho dưới 10 tại chi nhánh yêu cầu
     */
    public List<LowStockReportResponse> getLowStockReport(Long branchId) {
        int threshold = 10;

        // 1. Lấy TẤT CẢ các Variant đang kinh doanh (ACTIVE)
        List<ProductVariant> allVariants = variantRepo.findAllActiveWithProduct(null, null);

        // 2. Tính toán tồn kho cho từng biến thể tại chi nhánh yêu cầu
        return allVariants.stream().map(v -> {
            // Lấy TẤT CẢ các bản ghi kho của biến thể này
            List<Inventory> inventories = inventoryRepo.findByProductVariantId(v.getId());

            // Lọc theo chi nhánh nếu có
            if (branchId != null) {
                inventories = inventories.stream()
                        .filter(inv -> inv.getBranch() != null && inv.getBranch().getId().equals(branchId))
                        .collect(Collectors.toList());
            }

            // Tổng tồn kho (nếu inventories rỗng -> totalStock = 0)
            int totalStock = inventories.stream()
                    .mapToInt(i -> Objects.requireNonNullElse(i.getQuantity(), 0))
                    .sum();

            // Định mức (Threshold): 10 theo yêu cầu
            int currentThreshold = threshold;

            // Ngày nhập hàng gần nhất
            LocalDateTime lastImport = inventories.stream()
                    .map(Inventory::getLastReceiptDate)
                    .filter(Objects::nonNull)
                    .max(LocalDateTime::compareTo)
                    .orElse(null);

            return LowStockReportResponse.builder()
                    .variantId(v.getId())
                    .sku(v.getSku())
                    .productName(v.getProduct() != null ? v.getProduct().getName() : "Sản phẩm không xác định")
                    .unit(null)
                    .quantity(totalStock)
                    .minThreshold(currentThreshold)
                    .shortage(Math.max(0, currentThreshold - totalStock))
                    .isLowStock(totalStock < currentThreshold)
                    .lastImportDate(lastImport)
                    .build();
        })
                .filter(LowStockReportResponse::isLowStock) // Lọc những đứa tồn kho thấp (bao gồm cả 0)
                .sorted(Comparator.comparing(LowStockReportResponse::getQuantity)) // Hết hàng (0) sẽ lên đầu bảng
                .collect(Collectors.toList());
    }

    // [CẬP NHẬT LÔ HÀNG ĐỘNG]: Nhận thêm branchId và trả về ProductVariantResponse
    // có chứa danh sách Lô (Batches)
    public List<ProductVariantResponse> searchVariants(String keyword, Long branchId, String supplierCode) {
        String searchKey = (keyword == null) ? "" : keyword.trim();

        return variantRepo.findAllActiveWithProduct(searchKey, supplierCode).stream().map(v -> {
            List<Inventory> inventories = inventoryRepo.findByProductVariantId(v.getId()).stream()
                    .filter(inv -> inv.getBranch() != null)
                    .filter(inv -> inv.getBranch().getStatus() == null
                            || inv.getBranch().getStatus() == BranchStatus.ACTIVE)
                    .collect(Collectors.toList());

            List<Inventory> scopedInventories = branchId != null
                    ? inventories.stream()
                            .filter(inv -> inv.getBranch() != null && inv.getBranch().getId().equals(branchId))
                            .collect(Collectors.toList())
                    : inventories;

            int totalStock = scopedInventories.stream()
                    .mapToInt(i -> Objects.requireNonNullElse(i.getQuantity(), 0))
                    .sum();

            List<ProductVariantResponse.BatchInfoDto> batches = scopedInventories.stream()
                    .filter(inv -> Objects.requireNonNullElse(inv.getQuantity(), 0) > 0)
                    .map(inv -> ProductVariantResponse.BatchInfoDto.builder()
                            .inventoryId(inv.getId())
                            .branchName(inv.getBranch() != null ? inv.getBranch().getName() : null)
                            .batchNumber(inv.getBatchNumber())
                            .quantity(inv.getQuantity())
                            .importPrice(inv.getImportPrice() != null ? inv.getImportPrice() : BigDecimal.ZERO)
                            .sellingPrice(null)
                            .expiryDate(inv.getExpiryDate() != null ? inv.getExpiryDate().toLocalDate().toString() : null)
                            .build())
                    .collect(Collectors.toList());

            BigDecimal firstImportPrice = batches.stream()
                    .map(ProductVariantResponse.BatchInfoDto::getImportPrice)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(BigDecimal.ZERO);

            return ProductVariantResponse.builder()
                    .id(v.getId())
                    .sku(v.getSku())
                    .barcode(v.getBarcode())
                    .productName(v.getProduct() != null ? v.getProduct().getName() : "Sản phẩm không xác định")
                    .price(null)
                    .importPrice(firstImportPrice)
                    .imageUrl(v.getImageUrl())
                    .status(v.getStatus())
                    .quantity(totalStock)
                    .lowStock(totalStock < 10)
                    .batches(batches)
                    .attributeValues(Collections.emptyList())
                    .build();
        })
                .sorted(Comparator.comparing(ProductVariantResponse::isLowStock, Comparator.reverseOrder())
                        .thenComparing(ProductVariantResponse::getProductName, Comparator.nullsLast(String::compareTo)))
                .collect(Collectors.toList());
    }
}
