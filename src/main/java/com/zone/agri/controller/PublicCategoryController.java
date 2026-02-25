package com.zone.agri.controller;

import com.zone.agri.dto.admin.CategoryDTO;
import com.zone.agri.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/public/categories") // Base path for public category APIs
@RequiredArgsConstructor
@Tag(name = "Public Category APIs", description = "Các API công khai để lấy thông tin danh mục")
@CrossOrigin(origins = "http://localhost:3000")
public class PublicCategoryController {

    private final CategoryService categoryService;

    @Operation(summary = "Lấy danh sách danh mục đang hoạt động", description = "Trả về danh sách danh mục sản phẩm đang hoạt động cho người dùng cuối. Không yêu cầu xác thực.")
    @GetMapping
    public ResponseEntity<List<CategoryDTO>> getPublicCategories() {
        return ResponseEntity.ok(categoryService.getPublicCategories());
    }
}
