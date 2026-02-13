package com.zone.agri.controller;

import com.zone.agri.dto.common.MessageResponse;
import com.zone.agri.dto.supplier.SupplierRequest;
import com.zone.agri.dto.supplier.SupplierResponse;
import com.zone.agri.entity.Supplier;
import com.zone.agri.service.SupplierService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/suppliers")
@RequiredArgsConstructor
@Tag(name = "2. Supplier Management", description = "API quản lý nhà cung cấp: Thêm, sửa, xóa, tìm kiếm và phân trang")
public class SupplierController {

    private final SupplierService supplierService;

    // --- 1. TẠO MỚI ---
    @Operation(summary = "Thêm mới nhà cung cấp", description = "Tạo mới một nhà cung cấp với đầy đủ thông tin pháp nhân, liên hệ, tài chính.")
    @PostMapping
    public ResponseEntity<SupplierResponse> createSupplier(@Valid @RequestBody SupplierRequest request) {
        return ResponseEntity.ok(supplierService.createSupplier(request));
    }

    // --- 2. LẤY DANH SÁCH (TÌM KIẾM + PHÂN TRANG) ---
    // --- 2. LẤY DANH SÁCH (TÌM KIẾM + PHÂN TRANG + LỌC) ---
    @Operation(summary = "Lấy danh sách nhà cung cấp", description = "Hỗ trợ tìm kiếm, lọc theo danh mục, trạng thái và phân trang.")
    @GetMapping
    public ResponseEntity<Page<SupplierResponse>> getAllSuppliers(
            @Parameter(description = "Từ khóa tìm kiếm") @RequestParam(required = false) String keyword,
            @Parameter(description = "Lọc theo danh mục") @RequestParam(required = false) String category, // 👈 Thêm cái này
            @Parameter(description = "Lọc theo trạng thái") @RequestParam(required = false) String status,     // 👈 Thêm cái này
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(supplierService.getAllSuppliers(keyword, category, status, pageable));
    }

    // --- 3. CHI TIẾT ---
    @Operation(summary = "Lấy chi tiết nhà cung cấp", description = "Lấy toàn bộ thông tin của một nhà cung cấp theo ID.")
    @GetMapping("/{id}")
    public ResponseEntity<SupplierResponse> getSupplierById(@PathVariable Long id) {
        return ResponseEntity.ok(supplierService.getSupplierById(id));
    }

    // --- 4. CẬP NHẬT ---
    @Operation(summary = "Cập nhật thông tin", description = "Cập nhật thông tin nhà cung cấp dựa trên ID.")
    @PutMapping("/{id}")
    public ResponseEntity<SupplierResponse> updateSupplier(
            @PathVariable Long id,
            @Valid @RequestBody SupplierRequest request
    ) {
        return ResponseEntity.ok(supplierService.updateSupplier(id, request));
    }

    // --- 5. XÓA ---
    @Operation(summary = "Xóa nhà cung cấp", description = "Xóa nhà cung cấp khỏi hệ thống (Lưu ý: Cần kiểm tra ràng buộc dữ liệu trước khi xóa).")
    @DeleteMapping("/{id}")
    public ResponseEntity<MessageResponse> deleteSupplier(@PathVariable Long id) {
        supplierService.deleteSupplier(id);
        return ResponseEntity.ok(new MessageResponse("Đã xóa nhà cung cấp thành công!"));
    }
}