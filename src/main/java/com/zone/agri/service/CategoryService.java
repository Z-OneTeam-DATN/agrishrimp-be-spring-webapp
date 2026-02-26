package com.zone.agri.service;

import com.zone.agri.dto.admin.CategoryDTO;
import com.zone.agri.entity.Category;
import com.zone.agri.entity.Product;
import com.zone.agri.entity.enums.CategoryStatus;
import com.zone.agri.entity.enums.ProductStatus;
import com.zone.agri.entity.enums.VariantStatus;
import com.zone.agri.common.CloudinaryService;
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
@Slf4j // Thêm log để theo dõi
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository; // ✅ Inject thêm ProductRepository
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
                .orElseThrow(() -> new RuntimeException("Không tìm thấy danh mục với ID: " + id));
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

    @Transactional // ✅ Rất quan trọng để rollback nếu có lỗi
    public CategoryDTO updateCategory(Long id, CategoryDTO dto) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy danh mục"));

        // Lưu lại trạng thái cũ để so sánh
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
            // Lấy tất cả sản phẩm thuộc danh mục này đang có trạng thái ACTIVE
            List<Product> products = productRepository.findAllWithFilter(null, id, ProductStatus.ACTIVE);

            if (products != null && !products.isEmpty()) {
                for (Product p : products) {
                    p.setStatus(ProductStatus.INACTIVE); // Ẩn sản phẩm
                    if (p.getVariants() != null) {
                        p.getVariants().forEach(v -> v.setStatus(VariantStatus.INACTIVE)); // Ẩn biến thể
                    }
                }
                productRepository.saveAll(products);
                log.info("Hệ thống tự động ẩn {} sản phẩm thuộc danh mục ID: {}", products.size(), id);
            }
        }

        return convertToDTO(savedCategory);
    }

    @Transactional
    public void deleteCategory(Long id) {
        categoryRepository.deleteById(id);
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