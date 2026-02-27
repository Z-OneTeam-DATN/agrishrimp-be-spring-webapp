package com.zone.agri.service;

import com.zone.agri.dto.admin.CategoryDTO;
import com.zone.agri.entity.Category;
import com.zone.agri.entity.Product;
import com.zone.agri.entity.enums.CategoryStatus;
import com.zone.agri.entity.enums.ProductStatus;
import com.zone.agri.entity.enums.VariantStatus;
import com.zone.agri.common.CloudinaryService;
import com.zone.agri.exception.ConflictException; // Chắc chắn đã import đúng class Exception của bạn
import com.zone.agri.exception.NotFoundException;
import com.zone.agri.repository.CategoryRepository;
import com.zone.agri.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CategoryService {
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final CloudinaryService cloudinaryService;

    public List<CategoryDTO> getAllCategories(String keyword, CategoryStatus status) {
        List<Category> categories = categoryRepository.searchCategories(keyword, status);
        return categories.stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    // Logic tính tổng sản phẩm: Bản thân + các con
    private long countAllProductsRecursive(Category category) {
        long count = productRepository.countByCategoryId(category.getId());
        if (category.getChildren() != null) {
            for (Category child : category.getChildren()) {
                count += countAllProductsRecursive(child);
            }
        }
        return count;
    }

    @Transactional
    public CategoryDTO createCategory(CategoryDTO dto) {
        // 1. Bắt lỗi trùng tên
        if (categoryRepository.existsByNameIgnoreCase(dto.getName())) {
            throw new ConflictException("Tên danh mục '" + dto.getName() + "' đã tồn tại!");
        }

        Category category = new Category();
        category.setName(dto.getName());
        category.setStatus(dto.getStatus());

        if (dto.getImageUrl() != null && dto.getImageUrl().startsWith("data:image")) {
            category.setImageUrl(cloudinaryService.uploadImage(dto.getImageUrl()));
        } else {
            category.setImageUrl(dto.getImageUrl());
        }

        if (dto.getParentId() != null) {
            category.setParent(categoryRepository.findById(dto.getParentId()).orElse(null));
        }

        return convertToDTO(categoryRepository.save(category));
    }

    @Transactional
    public CategoryDTO updateCategory(Long id, CategoryDTO dto) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy danh mục"));

        // 2. Bắt lỗi trùng tên (trừ chính nó)
        if (categoryRepository.existsByNameIgnoreCaseAndIdNot(dto.getName(), id)) {
            throw new ConflictException("Tên danh mục '" + dto.getName() + "' đã tồn tại!");
        }

        CategoryStatus oldStatus = category.getStatus();
        category.setName(dto.getName());
        category.setStatus(dto.getStatus());

        if (dto.getImageUrl() != null && dto.getImageUrl().startsWith("data:image")) {
            category.setImageUrl(cloudinaryService.uploadImage(dto.getImageUrl()));
        }

        if (dto.getParentId() != null) {
            category.setParent(categoryRepository.findById(dto.getParentId()).orElse(null));
        } else {
            category.setParent(null);
        }

        // 3. Logic ẩn sản phẩm & danh mục con khi ẩn danh mục cha
        if (oldStatus == CategoryStatus.ACTIVE && dto.getStatus() == CategoryStatus.INACTIVE) {
            cascadeHide(category);
        }

        return convertToDTO(categoryRepository.save(category));
    }

    private void cascadeHide(Category category) {
        // Ẩn tất cả sản phẩm thuộc danh mục này bằng Query hàng loạt đã viết ở Bước 1
        productRepository.deactivateByCategoryId(category.getId());

        // Đệ quy để ẩn các danh mục con và sản phẩm của con
        if (category.getChildren() != null) {
            for (Category child : category.getChildren()) {
                child.setStatus(CategoryStatus.INACTIVE);
                categoryRepository.save(child); // Cập nhật trạng thái danh mục con
                cascadeHide(child); // Tiếp tục đệ quy xuống cấp sâu hơn
            }
        }
    }

    @Transactional
    public void deleteCategory(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Danh mục không tồn tại."));

        // 4. Chặn xóa nếu có sản phẩm (tính cả SP trong danh mục con)
        long totalProducts = countAllProductsRecursive(category);
        if (totalProducts > 0) {
            throw new ConflictException("Không thể xóa! Danh mục này hoặc danh mục con đang chứa " + totalProducts + " sản phẩm.");
        }

        categoryRepository.delete(category);
    }

    private CategoryDTO convertToDTO(Category entity) {
        CategoryDTO dto = new CategoryDTO();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setImageUrl(entity.getImageUrl());
        dto.setStatus(entity.getStatus());

        // Cột số lượng sản phẩm hiển thị trên bảng sẽ là tổng đệ quy
        dto.setProductCount(countAllProductsRecursive(entity));

        if (entity.getParent() != null) {
            dto.setParentId(entity.getParent().getId());
            dto.setParentName(entity.getParent().getName());
        }
        return dto;
    }

    public List<CategoryDTO> getPublicCategories() {
        // Sử dụng CategoryStatus.ACTIVE để lọc
        List<Category> categories = categoryRepository.findByStatus(CategoryStatus.ACTIVE);

        if (categories == null || categories.isEmpty()) {
            return new ArrayList<>();
        }

        // Map sang DTO (convertToDTO đã có logic tính tổng số sản phẩm đệ quy)
        return categories.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public CategoryDTO getCategoryById(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy danh mục với ID: " + id));

        // Sử dụng convertToDTO để đảm bảo productCount được tính toán chính xác
        return convertToDTO(category);
    }
}