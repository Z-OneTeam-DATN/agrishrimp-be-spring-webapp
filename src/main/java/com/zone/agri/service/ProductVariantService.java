package com.zone.agri.service;

import com.zone.agri.dto.product.ProductVariantResponse;
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
    private final ProductService productService; // Dùng chung hàm map của ProductService

    // [CẬP NHẬT QUAN TRỌNG]: Nhận thêm branchId và trả về ProductVariantResponse
    public List<ProductVariantResponse> searchVariants(String keyword, Long branchId) {
        String searchKey = (keyword == null) ? "" : keyword.trim();

        // Gọi hàm search mới trong Repo
        return variantRepo.searchByKeyword(searchKey).stream().map(v -> {

            // Map sang DTO cơ bản
            ProductVariantResponse resp = productService.mapVariantToResponse(v);

            // Bổ sung tên sản phẩm để hiển thị ở Dropdown
            if (v.getProduct() != null) {
                // Tạm thời dùng field unit để chứa tên, hoặc nếu DTO bạn có field productName thì gán vào đó
                resp.setUnit(v.getProduct().getName());
            }

            // NẾU CÓ CHI NHÁNH XUẤT -> Ép lấy tồn kho thực tế của chi nhánh đó
            if (branchId != null) {
                Integer branchStock = inventoryRepo.findByBranchIdAndProductVariantId(branchId, v.getId())
                        .map(Inventory::getQuantity)
                        .orElse(0);
                resp.setQuantity(branchStock);
            }

            return resp;
        }).collect(Collectors.toList());
    }
}