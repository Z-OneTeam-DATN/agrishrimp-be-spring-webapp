package com.zone.agri.service;

import com.zone.agri.dto.admin.CategoryDTO;
import com.zone.agri.entity.Category;
import com.zone.agri.entity.enums.CategoryStatus;
import com.zone.agri.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public List<CategoryDTO> getAllCategories() {
        return categoryRepository.findAll().stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    public CategoryDTO getCategoryById(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy danh mục với ID: " + id));
        return convertToDTO(category);
    }

    public Category createCategory(CategoryDTO dto) {
        Category category = new Category();
        category.setName(dto.getName());
        category.setDescription(dto.getDescription());
        category.setImageUrl(dto.getImageUrl());
        category.setStatus(dto.getStatus());

        // Xử lý Parent
        if (dto.getParentId() != null) {
            Category parent = categoryRepository.findById(dto.getParentId()).orElse(null);
            category.setParent(parent);
        }

        return categoryRepository.save(category);
    }

    public void deleteCategory(Long id) {
        categoryRepository.deleteById(id);
    }

    // Helper chuyển đổi
    private CategoryDTO convertToDTO(Category entity) {
        CategoryDTO dto = new CategoryDTO();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setDescription(entity.getDescription());
        dto.setImageUrl(entity.getImageUrl());
        dto.setStatus(entity.getStatus());
        dto.setDescription(entity.getDescription()); // Đừng quên dòng này!

        if (entity.getParent() != null) {
            dto.setParentId(entity.getParent().getId());
            dto.setParentName(entity.getParent().getName());
        }
        return dto;
    }
    public Category updateCategory(Long id, CategoryDTO dto) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy danh mục"));

        category.setName(dto.getName());
        category.setDescription(dto.getDescription());
        category.setImageUrl(dto.getImageUrl());
        category.setStatus(dto.getStatus());

        // Cập nhật cha
        if (dto.getParentId() != null) {
            Category parent = categoryRepository.findById(dto.getParentId()).orElse(null);
            category.setParent(parent);
        } else {
            category.setParent(null);
        }

        return categoryRepository.save(category);
    }
}