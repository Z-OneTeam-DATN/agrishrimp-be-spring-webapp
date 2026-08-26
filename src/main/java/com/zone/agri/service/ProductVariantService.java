package com.zone.agri.service;

import com.zone.agri.dto.response.product.ProductVariantResponse;
import com.zone.agri.dto.response.inventory.InventorySearchResponse;
import com.zone.agri.entity.Inventory;
import com.zone.agri.entity.ProductVariant;
import com.zone.agri.repository.InventoryRepository;
import com.zone.agri.repository.ProductVariantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import com.zone.agri.dto.response.product.LowStockReportResponse;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductVariantService {

    private final ProductVariantRepository variantRepo;
    private final InventoryRepository inventoryRepo;
    private final com.zone.agri.repository.BranchRepository branchRepo;
    private final SettingService settingService;

    /**
     * BÁO CÁO SẢN PHẨM DƯỚI ĐỊNH MỨC (LOW STOCK REPORT)
     * Chỉ lấy các biến thể có tổng tồn kho dưới 10 tại chi nhánh yêu cầu
     */
    public List<LowStockReportResponse> getLowStockReport(Long branchId) {
        int threshold = 10;

        // 1. Lấy ID chi nhánh tổng (WAREHOUSE đầu tiên hoặc mặc định 1L)
        Long mainBranchId = branchRepo.findAll().stream()
                .filter(b -> b != null && b.getBranchType() != null && b.getBranchType().trim().toLowerCase().contains("warehouse"))
                .map(com.zone.agri.entity.Branch::getId)
                .findFirst()
                .orElse(1L);

        // 2. Lấy TẤT CẢ các Variant đang kinh doanh (ACTIVE)
        List<ProductVariant> allVariants = variantRepo.findAllActiveWithProduct(null, null);

        // Fetch all inventories with eager relations once to avoid O(N) lazy loading queries
        List<Inventory> allDbInventories = inventoryRepo.findAllWithBranchAndVariant();
        Map<Long, List<Inventory>> inventoriesByVariantId = allDbInventories.stream()
                .filter(i -> i.getProductVariant() != null)
                .collect(Collectors.groupingBy(i -> i.getProductVariant().getId()));

        // 3. Tính toán tồn kho cho từng biến thể tại chi nhánh yêu cầu
        return allVariants.stream().map(v -> {
            // Lấy TẤT CẢ các bản ghi kho của biến thể này từ map
            List<Inventory> allInventories = inventoriesByVariantId.getOrDefault(v.getId(), Collections.emptyList());

            // Tồn kho chi nhánh tổng
            int mainBranchQty = allInventories.stream()
                    .filter(i -> i.getBranch() != null && i.getBranch().getId().equals(mainBranchId))
                    .mapToInt(i -> Objects.requireNonNullElse(i.getQuantity(), 0))
                    .sum();

            List<Inventory> inventories = allInventories;
            // Lọc theo chi nhánh nếu có
            if (branchId != null) {
                inventories = allInventories.stream()
                        .filter(inv -> inv.getBranch() != null && inv.getBranch().getId().equals(branchId))
                        .collect(Collectors.toList());
            }

            // Tổng tồn kho (nếu inventories rỗng -> totalStock = 0)
            int totalStock = inventories.stream()
                    .mapToInt(i -> Objects.requireNonNullElse(i.getQuantity(), 0))
                    .sum();

            // Định mức (Threshold): dùng đúng Inventory.minStock đã cấu hình cho biến thể/chi
            // nhánh này (lấy giá trị lớn nhất nếu có nhiều lô cùng chi nhánh); chỉ rơi về mặc định
            // 10 khi chưa ai cấu hình minStock cho bản ghi kho nào
            int currentThreshold = inventories.stream()
                    .map(Inventory::getMinStock)
                    .filter(Objects::nonNull)
                    .max(Integer::compareTo)
                    .orElse(threshold);

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
                    .mainBranchQuantity(mainBranchQty)
                    .build();
        })
                .filter(Objects::nonNull)
                .filter(LowStockReportResponse::isLowStock) // Lọc những đứa tồn kho thấp (bao gồm cả 0)
                .sorted(Comparator.comparing(LowStockReportResponse::getQuantity)) // Hết hàng (0) sẽ lên đầu bảng
                .collect(Collectors.toList());
    }

    // [CẬP NHẬT LÔ HÀNG ĐỘNG]: Nhận thêm branchId và trả về ProductVariantResponse
    // có chứa danh sách Lô (Batches)
    public List<ProductVariantResponse> searchVariants(String keyword, Long branchId, String supplierCode) {
        String searchKey = (keyword == null) ? "" : keyword.trim();

        List<InventorySearchResponse> inventoryRows = inventoryRepo.searchInventoryForCheck(searchKey, branchId);
        Map<Long, List<InventorySearchResponse>> rowsByVariantId = inventoryRows.stream()
                .filter(row -> row.getVariantId() != null)
                .collect(Collectors.groupingBy(
                        InventorySearchResponse::getVariantId,
                        LinkedHashMap::new,
                        Collectors.toList()));

        return rowsByVariantId.values().stream()
                .map(rows -> {
                    InventorySearchResponse firstRow = rows.get(0);
                    ProductVariant variant = variantRepo.findById(firstRow.getVariantId())
                            .orElse(null);
                    Long categoryId = variant != null
                            && variant.getProduct() != null
                            && variant.getProduct().getCategory() != null
                                    ? variant.getProduct().getCategory().getId()
                                    : null;

                    int totalStock = rows.stream()
                            .map(InventorySearchResponse::getQuantity)
                            .filter(Objects::nonNull)
                            .mapToInt(Integer::intValue)
                            .sum();

                    List<ProductVariantResponse.BatchInfoDto> batches = rows.stream()
                            .filter(row -> Objects.requireNonNullElse(row.getQuantity(), 0) > 0)
                            .map(row -> {
                                BigDecimal importPrice = row.getImportPrice() != null
                                        ? row.getImportPrice()
                                        : BigDecimal.ZERO;
                                BigDecimal sellingPrice = settingService.calculateSellingPrice(
                                        importPrice,
                                        categoryId,
                                        row.getExpiryDate());

                                return ProductVariantResponse.BatchInfoDto.builder()
                                        .inventoryId(null)
                                        .branchName(row.getBranchName())
                                        .batchNumber(row.getBatchNumber())
                                        .quantity(row.getQuantity())
                                        .importPrice(importPrice)
                                        .sellingPrice(sellingPrice)
                                        .expiryDate(row.getExpiryDate() != null ? row.getExpiryDate().toLocalDate().toString() : null)
                                        .build();
                            })
                            .collect(Collectors.toList());

                    BigDecimal firstImportPrice = batches.stream()
                            .map(ProductVariantResponse.BatchInfoDto::getImportPrice)
                            .filter(Objects::nonNull)
                            .findFirst()
                            .orElse(BigDecimal.ZERO);
                    BigDecimal sellingPrice = firstImportPrice.compareTo(BigDecimal.ZERO) > 0
                            ? settingService.calculateSellingPrice(firstImportPrice, categoryId, null)
                            : null;

                    return ProductVariantResponse.builder()
                            .id(firstRow.getVariantId())
                            .sku(firstRow.getSku())
                            .barcode(firstRow.getBarcode())
                            .productName(firstRow.getProductName() != null ? firstRow.getProductName() : "Sản phẩm không xác định")
                            .price(sellingPrice)
                            .importPrice(firstImportPrice)
                            .imageUrl(firstRow.getImageUrl())
                            .status(null)
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
