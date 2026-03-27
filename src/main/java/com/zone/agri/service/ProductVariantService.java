package com.zone.agri.service;

import com.zone.agri.dto.response.product.ProductVariantResponse;
import com.zone.agri.entity.Inventory;
import com.zone.agri.repository.InventoryRepository;
import com.zone.agri.repository.ProductVariantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductVariantService {

    private final ProductVariantRepository variantRepo;
    private final InventoryRepository inventoryRepo;
    private final ProductService productService; // Dùng chung hàm map của ProductService để giữ nguyên phân quyền & tính giá

    // [CẬP NHẬT LÔ HÀNG ĐỘNG]: Nhận thêm branchId và trả về ProductVariantResponse có chứa danh sách Lô (Batches)
    public List<ProductVariantResponse> searchVariants(String keyword, Long branchId) {
        String searchKey = (keyword == null) ? "" : keyword.trim();

        // Gọi hàm search mới trong Repo
        return variantRepo.searchByKeyword(searchKey).stream().map(v -> {

            // 1. Map sang DTO cơ bản (Hàm này đã tự tính giá bán = giá vốn * 1.3 và nạp danh sách Lô hàng)
            ProductVariantResponse resp = productService.mapVariantToResponse(v);

            // 2. Gán tên sản phẩm chuẩn xác (DTO mới đã có field productName)
            if (v.getProduct() != null) {
                resp.setProductName(v.getProduct().getName());
            }

            // 3. NẾU CÓ CHI NHÁNH XUẤT -> Ép lấy tồn kho thực tế và các lô hàng của chi nhánh đó
            if (branchId != null) {
                // Quét toàn bộ kho của biến thể này, lọc ra các lô CÒN HÀNG của ĐÚNG CHI NHÁNH ĐÓ
                List<Inventory> branchInventories = inventoryRepo.findByProductVariantId(v.getId()).stream()
                        .filter(inv -> inv.getBranch() != null && inv.getBranch().getId().equals(branchId))
                        .filter(inv -> inv.getQuantity() != null && inv.getQuantity() > 0)
                        .collect(Collectors.toList());

                // Tính tổng tồn kho của tất cả các lô tại chi nhánh này
                int totalBranchStock = branchInventories.stream().mapToInt(Inventory::getQuantity).sum();
                resp.setQuantity(totalBranchStock);

                // Lọc lại danh sách Batch (Lô hàng) trong Response để POS/Tạo Đơn chỉ thấy lô của chi nhánh mình
                if (resp.getBatches() != null) {
                    List<Long> branchInvIds = branchInventories.stream().map(Inventory::getId).collect(Collectors.toList());

                    List<ProductVariantResponse.BatchInfoDto> filteredBatches = resp.getBatches().stream()
                            .filter(b -> branchInvIds.contains(b.getInventoryId()))
                            .collect(Collectors.toList());

                    resp.setBatches(filteredBatches);
                }
            }

            return resp;
        }).collect(Collectors.toList());
    }
}