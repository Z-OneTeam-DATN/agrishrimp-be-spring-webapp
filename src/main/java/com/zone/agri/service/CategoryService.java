package com.zone.agri.service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional; // Chắc chắn đã import đúng class Exception của bạn

import com.zone.agri.common.CloudinaryService;
import com.zone.agri.dto.response.admin.CategoryDTO;
import com.zone.agri.entity.Category;
import com.zone.agri.entity.enums.CategoryStatus;
import com.zone.agri.exception.ConflictException;
import com.zone.agri.exception.NotFoundException;
import com.zone.agri.repository.CategoryRepository;
import com.zone.agri.repository.ProductRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class CategoryService {
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final CloudinaryService cloudinaryService;

    public List<CategoryDTO> getAllCategories(String keyword, CategoryStatus status) {
        List<Category> categories = categoryRepository.searchCategories(keyword, status);
        // Pre-calculate counts in batch if needed or at least minimize overhead
        return categories.stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    // TỐI ƯU: Logic tính tổng sản phẩm đệ quy
    private long countAllProductsRecursive(Category category) {
        if (category == null)
            return 0;
        long count = productRepository.countByCategoryId(category.getId());
        if (category.getChildren() != null && !category.getChildren().isEmpty()) {
            for (Category child : category.getChildren()) {
                count += countAllProductsRecursive(child);
            }
        }
        return count;
    }

    @Transactional
    public CategoryDTO createCategory(CategoryDTO dto) {
        if (categoryRepository.existsByNameIgnoreCase(dto.getName())) {
            throw new ConflictException("Tên danh mục '" + dto.getName() + "' đã tồn tại!");
        }

        Category category = new Category();
        mapToEntity(category, dto);
        return convertToDTO(categoryRepository.save(category));
    }

    @Transactional
    public CategoryDTO updateCategory(Long id, CategoryDTO dto) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy danh mục"));

        if (categoryRepository.existsByNameIgnoreCaseAndIdNot(dto.getName(), id)) {
            throw new ConflictException("Tên danh mục '" + dto.getName() + "' đã tồn tại!");
        }

        CategoryStatus oldStatus = category.getStatus();
        mapToEntity(category, dto);

        // Logic ẩn sản phẩm & danh mục con khi ẩn danh mục cha
        if (oldStatus == CategoryStatus.ACTIVE && category.getStatus() == CategoryStatus.INACTIVE) {
            cascadeHide(category);
        }

        // Khi mở lại danh mục cha, khôi phục trạng thái danh mục con và sản phẩm thuộc
        // cây danh mục
        if (oldStatus == CategoryStatus.INACTIVE && category.getStatus() == CategoryStatus.ACTIVE) {
            cascadeRestore(category);
        }

        return convertToDTO(categoryRepository.save(category));
    }

    private void handleImageUpload(Category category, String imageUrl) {
        if (imageUrl != null && imageUrl.startsWith("data:image")) {
            category.setImageUrl(cloudinaryService.uploadImage(imageUrl));
        } else {
            category.setImageUrl(imageUrl);
        }
    }

    private void mapToEntity(Category entity, CategoryDTO dto) {
        entity.setName(dto.getName());
        entity.setStatus(dto.getStatus());
        handleImageUpload(entity, dto.getImageUrl());

        if (dto.getParentId() != null) {
            if (entity.getId() != null && entity.getId().equals(dto.getParentId())) {
                throw new ConflictException("Danh mục không thể làm cha của chính nó!");
            }
            Category parent = categoryRepository.findById(dto.getParentId())
                    .orElseThrow(
                            () -> new NotFoundException("Không tìm thấy danh mục cha với ID: " + dto.getParentId()));
            entity.setParent(parent);
        } else {
            entity.setParent(null);
        }
    }

    private void cascadeHide(Category category) {
        // Ẩn tất cả sản phẩm thuộc danh mục này
        productRepository.deactivateByCategoryId(category.getId());

        // Đệ quy để ẩn các danh mục con và sản phẩm của con
        List<Category> children = category.getChildren();
        if (children != null && !children.isEmpty()) {
            for (Category child : children) {
                if (child.getStatus() != CategoryStatus.INACTIVE) {
                    child.setStatus(CategoryStatus.INACTIVE);
                    categoryRepository.save(child);
                }
                cascadeHide(child);
            }
        }
    }

    private void cascadeRestore(Category category) {
        // Mở lại tất cả sản phẩm thuộc danh mục này
        productRepository.activateByCategoryId(category.getId());

        // Đệ quy mở lại danh mục con và sản phẩm của con
        List<Category> children = category.getChildren();
        if (children != null && !children.isEmpty()) {
            for (Category child : children) {
                if (child.getStatus() != CategoryStatus.ACTIVE) {
                    child.setStatus(CategoryStatus.ACTIVE);
                    categoryRepository.save(child);
                }
                cascadeRestore(child);
            }
        }
    }

    @Transactional
    public void deleteCategory(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Danh mục không tồn tại."));

        // Chặn xóa nếu có sản phẩm (tính cả SP trong danh mục con)
        long totalProducts = countAllProductsRecursive(category);
        if (totalProducts > 0) {
            throw new ConflictException(
                    "Không thể xóa! Danh mục này hoặc danh mục con đang chứa " + totalProducts + " sản phẩm.");
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
        List<Category> categories = categoryRepository.findByStatus(CategoryStatus.ACTIVE);
        if (categories == null)
            return new ArrayList<>();

        return categories.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public CategoryDTO getCategoryById(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy danh mục với ID: " + id));
        return convertToDTO(category);
    }
}