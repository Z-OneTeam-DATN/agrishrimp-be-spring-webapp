package com.zone.agri.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
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
    private static final Set<String> RESERVED_CATEGORY_SLUGS = Set.of(
            "admin", "api", "ai-doctor", "benh-tom", "blog", "checkout", "dang-nhap",
            "danh-muc", "gioi-thieu", "login", "san-pham", "signup", "vat-tu-thuy-san");

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final CloudinaryService cloudinaryService;

    @Transactional(readOnly = true)
    public List<CategoryDTO> getAllCategories(String keyword, CategoryStatus status) {
        String normalizedKeyword = normalizeSearchKeyword(keyword);
        List<Category> categories = categoryRepository.searchCategories(normalizedKeyword, status);
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
        String normalizedName = normalizeCategoryName(dto.getName());
        if (categoryRepository.existsByNameIgnoreCase(normalizedName)) {
            throw new ConflictException("Tên danh mục '" + normalizedName + "' đã tồn tại!", true);
        }

        Category category = new Category();
        dto.setName(normalizedName);
        mapToEntity(category, dto);
        return convertToDTO(categoryRepository.save(category));
    }

    @Transactional
    public CategoryDTO updateCategory(Long id, CategoryDTO dto) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy danh mục"));

        String normalizedName = normalizeCategoryName(dto.getName());
        if (categoryRepository.existsByNameIgnoreCaseAndIdNot(normalizedName, id)) {
            throw new ConflictException("Tên danh mục '" + normalizedName + "' đã tồn tại!", true);
        }

        CategoryStatus oldStatus = category.getStatus();
        CategoryStatus nextStatus = dto.getStatus() != null ? dto.getStatus() : oldStatus;
        if (oldStatus == CategoryStatus.ACTIVE
                && nextStatus == CategoryStatus.INACTIVE
                && hasActiveDescendant(category)) {
            throw new ConflictException(
                    "Không thể ẩn danh mục cha khi vẫn còn danh mục con đang hiển thị.",
                    true);
        }

        dto.setName(normalizedName);
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

    private String toSlug(String input) {
        if (input == null)
            return "";
        String normalized = java.text.Normalizer.normalize(input, java.text.Normalizer.Form.NFD);
        return normalized.replaceAll("[\\p{InCombiningDiacriticalMarks}]", "")
                .toLowerCase(Locale.ENGLISH)
                .replaceAll("[^a-z0-9\\s]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("^-|-$", "");
    }

    private String resolveCategorySlug(Category entity, CategoryDTO dto) {
        String currentSlug = entity.getSlug();
        if (currentSlug != null && !currentSlug.isBlank()) {
            return currentSlug;
        }

        String seed = dto.getSlug() != null && !dto.getSlug().isBlank() ? dto.getSlug() : dto.getName();
        String base = toSlug(seed);
        if (base.isBlank() || RESERVED_CATEGORY_SLUGS.contains(base)) {
            base = "danh-muc";
        }

        String candidate = base;
        int suffix = 2;
        Long currentId = entity.getId();
        while (
                productRepository.existsBySlug(candidate)
                        || (currentId == null
                        ? categoryRepository.existsBySlugIgnoreCase(candidate)
                        : categoryRepository.existsBySlugIgnoreCaseAndIdNot(candidate, currentId))) {
            candidate = base + "-" + suffix++;
        }
        return candidate;
    }

    private void handleImageUpload(Category category, String imageUrl) {
        if (imageUrl != null && imageUrl.startsWith("data:image")) {
            String slug = toSlug(category.getName());
            category.setImageUrl(cloudinaryService.uploadImage(imageUrl, "categories", slug));
        } else {
            category.setImageUrl(imageUrl);
        }
    }

    private void mapToEntity(Category entity, CategoryDTO dto) {
        entity.setName(normalizeCategoryName(dto.getName()));
        entity.setSlug(resolveCategorySlug(entity, dto));
        entity.setStatus(dto.getStatus() != null ? dto.getStatus() : CategoryStatus.ACTIVE);
        handleImageUpload(entity, dto.getImageUrl());

        if (dto.getParentId() != null) {
            if (entity.getId() != null && entity.getId().equals(dto.getParentId())) {
                throw new ConflictException("Danh mục không thể làm cha của chính nó!", true);
            }

            Category parent = categoryRepository.findById(dto.getParentId())
                    .orElseThrow(
                            () -> new NotFoundException("Không tìm thấy danh mục cha với ID: " + dto.getParentId()));
            if (parent.getStatus() == CategoryStatus.INACTIVE) {
                throw new ConflictException(
                        "Không thể tạo hoặc cập nhật danh mục con dưới danh mục cha đang ẩn.",
                        true);
            }
            if (entity.getId() != null && isDescendantOf(parent, entity.getId())) {
                throw new ConflictException(
                        "Không thể chọn danh mục con làm danh mục cha vì sẽ tạo vòng lặp cây danh mục.",
                        true);
            }
            entity.setParent(parent);
        } else {
            entity.setParent(null);
        }
    }

    private String normalizeCategoryName(String name) {
        if (name == null) {
            return "";
        }

        return name.trim().replaceAll("\\s+", " ");
    }

    private String normalizeSearchKeyword(String keyword) {
        if (keyword == null) {
            return null;
        }

        String normalized = keyword.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private boolean isDescendantOf(Category candidateParent, Long categoryId) {
        Category current = candidateParent;
        while (current != null) {
            if (current.getId() != null && current.getId().equals(categoryId)) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }

    private boolean hasActiveDescendant(Category category) {
        List<Category> children = category.getChildren();
        if (children == null || children.isEmpty()) {
            return false;
        }

        for (Category child : children) {
            if (child.getStatus() == null || child.getStatus() == CategoryStatus.ACTIVE) {
                return true;
            }
            if (hasActiveDescendant(child)) {
                return true;
            }
        }
        return false;
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
                    "Không thể xóa! Danh mục này hoặc danh mục con đang chứa " + totalProducts + " sản phẩm.", true);
        }

        categoryRepository.delete(category);
    }

    private CategoryDTO convertToDTO(Category entity) {
        CategoryDTO dto = new CategoryDTO();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setSlug(entity.getSlug());
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

    @Transactional(readOnly = true)
    public List<CategoryDTO> getPublicCategories() {
        List<Category> categories = categoryRepository.findByStatus(CategoryStatus.ACTIVE);
        if (categories == null)
            return new ArrayList<>();

        return categories.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public CategoryDTO getPublicCategoryBySlug(String slug) {
        Category category = categoryRepository.findBySlugIgnoreCase(slug)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy danh mục với slug: " + slug));
        if (category.getStatus() != CategoryStatus.ACTIVE) {
            throw new NotFoundException("Danh mục không tồn tại hoặc đã ngừng hoạt động.");
        }
        return convertToDTO(category);
    }

    @Transactional(readOnly = true)
    public CategoryDTO getCategoryById(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy danh mục với ID: " + id));
        return convertToDTO(category);
    }

}
