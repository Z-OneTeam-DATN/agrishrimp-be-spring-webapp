package com.zone.agri.controller;

import com.zone.agri.dto.customer.CustomerRequest;
import com.zone.agri.entity.Customer;
import com.zone.agri.service.CustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
@Tag(name = "2. Customer Management", description = "API quản lý khách hàng: Thêm, sửa, xóa, tìm kiếm")
public class CustomerController {

    private final CustomerService customerService;

    // --- 1. LẤY DANH SÁCH ---
    @Operation(summary = "Lấy danh sách khách hàng", description = "Hỗ trợ tìm kiếm theo tên/SĐT và lọc theo trạng thái (active/locked). Phân trang mặc định.")
    @GetMapping
    public ResponseEntity<Page<Customer>> getAll(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        // Sắp xếp mặc định: Mới nhất lên đầu
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(customerService.getCustomers(keyword, status, pageable));
    }

    // --- 2. LẤY CHI TIẾT ---
    @Operation(summary = "Lấy chi tiết khách hàng", description = "Trả về thông tin đầy đủ của một khách hàng theo ID.")
    @GetMapping("/{id}")
    public ResponseEntity<Customer> getById(@PathVariable Long id) {
        return ResponseEntity.ok(customerService.getCustomerById(id));
    }

    // --- 3. TẠO MỚI ---
    @Operation(summary = "Tạo mới khách hàng", description = "Tạo hồ sơ khách hàng, tự động tạo tài khoản User và gửi email thông báo mật khẩu.")
    @PostMapping
    public ResponseEntity<Customer> create(@Valid @RequestBody CustomerRequest request) {
        return ResponseEntity.ok(customerService.createCustomer(request));
    }

    // --- 4. CẬP NHẬT ---
    @Operation(summary = "Cập nhật thông tin", description = "Cập nhật thông tin hành chính, trạng thái hoặc ghi chú của khách hàng.")
    @PutMapping("/{id}")
    public ResponseEntity<Customer> update(
            @PathVariable Long id,
            @Valid @RequestBody CustomerRequest request
    ) {
        return ResponseEntity.ok(customerService.updateCustomer(id, request));
    }
}