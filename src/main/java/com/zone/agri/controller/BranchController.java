package com.zone.agri.controller;

import com.zone.agri.dto.admin.BranchDTO;
import com.zone.agri.service.BranchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chi-nhanh")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:3000")
public class BranchController {

    private final BranchService branchService;

    // 1. Lấy danh sách tất cả chi nhánh
    @GetMapping("/danh-sach-chi-nhanh")
    public ResponseEntity<List<BranchDTO>> getAll() {
        return ResponseEntity.ok(branchService.getAll());
    }

    // 2. Lấy chi tiết một chi nhánh theo ID
    @GetMapping("/chi-tiet-danh-sach-/{id}")
    public ResponseEntity<BranchDTO> getById(@PathVariable Long id) {
        return ResponseEntity.ok(branchService.getBranchById(id));
    }

    // 3. Tạo mới chi nhánh
    // SỬA: Đổi kiểu trả về từ ResponseEntity<Branch> thành ResponseEntity<BranchDTO>
    @PostMapping
    public ResponseEntity<BranchDTO> create(@RequestBody BranchDTO dto) {
        return ResponseEntity.ok(branchService.create(dto));
    }

    // 4. Cập nhật thông tin chi nhánh
    // SỬA: Đổi kiểu trả về từ ResponseEntity<Branch> thành ResponseEntity<BranchDTO>
    @PutMapping("/{id}")
    public ResponseEntity<BranchDTO> update(@PathVariable Long id, @RequestBody BranchDTO dto) {
        return ResponseEntity.ok(branchService.update(id, dto));
    }

    // 5. Xóa chi nhánh
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        branchService.delete(id);
        return ResponseEntity.noContent().build();
    }
}