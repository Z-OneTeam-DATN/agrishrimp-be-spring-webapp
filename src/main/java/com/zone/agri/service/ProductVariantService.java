package com.zone.agri.service;

import com.zone.agri.dto.response.product.ProductVariantResponse;
import com.zone.agri.entity.Inventory;
import com.zone.agri.entity.ProductVariant;
import com.zone.agri.repository.InventoryRepository;
import com.zone.agri.repository.ProductVariantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import com.zone.agri.dto.response.product.LowStockReportResponse;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductVariantService {

    private final ProductVariantRepository variantRepo;
    private final InventoryRepository inventoryRepo;
    private final ProductService productService; // Dùng chung hàm map của ProductService để giữ nguyên phân quyền &
                                                 // tính giá

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

        // Gọi hàm search mới trong Repo
        return variantRepo.findAllActiveWithProduct(searchKey, supplierCode).stream().map(v -> {

            // 1. Map sang DTO cơ bản (Hàm này đã tự tính giá bán = giá vốn * 1.3 và nạp
            // danh sách Lô hàng)
            ProductVariantResponse resp = productService.mapVariantToResponse(v);

            // 2. Gán tên sản phẩm chuẩn xác (DTO mới đã có field productName)
            if (v.getProduct() != null) {
                resp.setProductName(v.getProduct().getName());
            }

            // 3. Tính toán tồn kho dựa trên branchId (nếu có) hoặc tổng hệ thống (nếu
            // branchId == null - Admin)
            List<Inventory> inventories = inventoryRepo.findByProductVariantId(v.getId());
            int totalStock;

            if (branchId != null) {
                // Quét toàn bộ kho của biến thể này, lọc ra các lô của ĐÚNG CHI NHÁNH ĐÓ
                List<Inventory> branchInventories = inventories.stream()
                        .filter(inv -> inv.getBranch() != null && inv.getBranch().getId().equals(branchId))
                        .collect(Collectors.toList());

                // Tính tổng tồn kho của tất cả các lô tại chi nhánh này (bao gồm cả lô đã hết
                // để biết lịch sử nếu cần, nhưng thường là > 0)
                totalStock = branchInventories.stream().mapToInt(i -> Objects.requireNonNullElse(i.getQuantity(), 0))
                        .sum();

                // Cập nhật lại danh sách Batch cho đúng chi nhánh
                if (resp.getBatches() != null) {
                    List<Long> branchInvIds = branchInventories.stream()
                            .filter(inv -> inv.getQuantity() > 0) // Chỉ hiện lô còn hàng để xuất
                            .map(Inventory::getId).collect(Collectors.toList());

                    List<ProductVariantResponse.BatchInfoDto> filteredBatches = resp.getBatches().stream()
                            .filter(b -> branchInvIds.contains(b.getInventoryId()))
                            .collect(Collectors.toList());

                    resp.setBatches(filteredBatches);
                }
            } else {
                // Admin search: Tính tổng tồn kho toàn hệ thống
                totalStock = inventories.stream().mapToInt(i -> Objects.requireNonNullElse(i.getQuantity(), 0)).sum();
            }

            resp.setQuantity(totalStock);

            // 4. Cảnh báo tồn kho thấp (Dưới 10 sản phẩm)
            resp.setLowStock(totalStock < 10);

            return resp;
        })
                // 5. Sắp xếp: Tồn kho thấp lên đầu (isLowStock = true trước), sau đó theo tên
                // sản phẩm
                .sorted(Comparator.comparing(ProductVariantResponse::isLowStock, Comparator.reverseOrder())
                        .thenComparing(ProductVariantResponse::getProductName, Comparator.nullsLast(String::compareTo)))
                .collect(Collectors.toList());
    }
}