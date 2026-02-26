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

    public List<CategoryDTO> getAllCategories() {
        List<Category> categories = categoryRepository.findAll();
        if (categories == null || categories.isEmpty()) {
            return new ArrayList<>();
        }
        return categories.stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    public List<CategoryDTO> getPublicCategories() {
        List<Category> categories = categoryRepository.findByStatus(CategoryStatus.ACTIVE);
        if (categories == null || categories.isEmpty()) {
            return new ArrayList<>();
        }
        return categories.stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    public CategoryDTO getCategoryById(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy danh mục với ID: " + id));
        return convertToDTO(category);
    }

    @Transactional
    public CategoryDTO createCategory(CategoryDTO dto) {
        Category category = new Category();
        category.setName(dto.getName());
        category.setDescription(dto.getDescription());
        category.setStatus(dto.getStatus());

        String imageUrl = dto.getImageUrl();
        if (imageUrl != null && imageUrl.startsWith("data:image")) {
            imageUrl = cloudinaryService.uploadImage(imageUrl);
        }
        category.setImageUrl(imageUrl);

        if (dto.getParentId() != null) {
            Category parent = categoryRepository.findById(dto.getParentId()).orElse(null);
            category.setParent(parent);
        }

        Category savedCategory = categoryRepository.save(category);
        return convertToDTO(savedCategory);
    }

    @Transactional
    public CategoryDTO updateCategory(Long id, CategoryDTO dto) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy danh mục"));

        CategoryStatus oldStatus = category.getStatus();

        category.setName(dto.getName());
        category.setDescription(dto.getDescription());
        category.setStatus(dto.getStatus());

        String imageUrl = dto.getImageUrl();
        if (imageUrl != null && imageUrl.startsWith("data:image")) {
            imageUrl = cloudinaryService.uploadImage(imageUrl);
        }
        category.setImageUrl(imageUrl);

        if (dto.getParentId() != null) {
            Category parent = categoryRepository.findById(dto.getParentId()).orElse(null);
            category.setParent(parent);
        } else {
            category.setParent(null);
        }

        Category savedCategory = categoryRepository.save(category);

        // ✅ LOGIC TỰ ĐỘNG ẨN SẢN PHẨM KHI ẨN DANH MỤC
        if (oldStatus == CategoryStatus.ACTIVE && dto.getStatus() == CategoryStatus.INACTIVE) {
            List<Product> products = productRepository.findAllWithFilter(null, id, ProductStatus.ACTIVE);
            if (products != null && !products.isEmpty()) {
                for (Product p : products) {
                    p.setStatus(ProductStatus.INACTIVE);
                    if (p.getVariants() != null) {
                        p.getVariants().forEach(v -> v.setStatus(VariantStatus.INACTIVE));
                    }
                }
                productRepository.saveAll(products);
                log.info("Hệ thống tự động ẩn {} sản phẩm thuộc danh mục ID: {}", products.size(), id);
            }
        }

        return convertToDTO(savedCategory);
    }

    /**
     * ✅ Cập nhật logic xóa: Chặn xóa nếu có ràng buộc dữ liệu
     */
    @Transactional
    public void deleteCategory(Long id) {
        // 1. Kiểm tra danh mục có tồn tại không
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Danh mục không tồn tại hoặc đã bị xóa trước đó."));

        // 2. Kiểm tra xem có sản phẩm nào đang thuộc danh mục này không
        boolean hasProducts = productRepository.existsByCategoryId(id);
        if (hasProducts) {
            log.warn("Thao tác bị chặn: Cố gắng xóa danh mục ID {} đang có sản phẩm liên kết.", id);
            throw new ConflictException("Danh mục này đã có sản phẩm. Bạn không thể xóa, vui lòng chuyển sản phẩm sang danh mục khác hoặc ẩn danh mục này.");
        }

        // 3. Kiểm tra xem có danh mục con không (Nếu xóa cha thì con mất gốc)
        if (category.getChildren() != null && !category.getChildren().isEmpty()) {
            throw new ConflictException("Danh mục này đang chứa các danh mục con. Vui lòng xóa các danh mục con trước.");
        }

        // 4. Nếu hợp lệ thì thực hiện xóa
        categoryRepository.delete(category);
        log.info("Đã xóa vĩnh viễn danh mục ID: {}", id);
    }

    private CategoryDTO convertToDTO(Category entity) {
        CategoryDTO dto = new CategoryDTO();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setDescription(entity.getDescription());
        dto.setImageUrl(entity.getImageUrl());
        dto.setStatus(entity.getStatus());

        if (entity.getParent() != null) {
            dto.setParentId(entity.getParent().getId());
            dto.setParentName(entity.getParent().getName());
        }
        return dto;
    }
}