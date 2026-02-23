package com.zone.agri.controller;

import com.zone.agri.dto.admin.BranchDTO;
import com.zone.agri.service.BranchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chi-nhanh")
@RequiredArgsConstructor
@Tag(name = "Branch Management", description = "Quản lý chi nhánh, kho bãi và điểm giao dịch")
@CrossOrigin(origins = "http://localhost:3000")
public class BranchController {

    private final BranchService branchService;

    @Operation(summary = "Lấy danh sách chi nhánh", description = "Trả về toàn bộ danh sách chi nhánh và kho đang hoạt động.")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/danh-sach-chi-nhanh")
    public ResponseEntity<List<BranchDTO>> getAll() {
        return ResponseEntity.ok(branchService.getAll());
    }

    @Operation(summary = "Chi tiết chi nhánh", description = "Lấy thông tin chi tiết của một chi nhánh dựa trên ID.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tìm thấy chi nhánh", content = @Content(schema = @Schema(implementation = BranchDTO.class))),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy chi nhánh với ID cung cấp")
    })
    @GetMapping("/chi-tiet-danh-sach-/{id}")
    public ResponseEntity<BranchDTO> getById(
            @Parameter(description = "ID của chi nhánh cần tìm", example = "1", required = true)
            @PathVariable Long id) {
        return ResponseEntity.ok(branchService.getBranchById(id));
    }

    @Operation(summary = "Tạo mới chi nhánh", description = "Thêm một chi nhánh hoặc kho mới vào hệ thống.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tạo thành công", content = @Content(schema = @Schema(implementation = BranchDTO.class))),
            @ApiResponse(responseCode = "400", description = "Dữ liệu không hợp lệ hoặc mã chi nhánh đã tồn tại")
    })
    @PostMapping
    public ResponseEntity<BranchDTO> create(@RequestBody BranchDTO dto) {
        return ResponseEntity.ok(branchService.create(dto));
    }

    @Operation(summary = "Cập nhật chi nhánh", description = "Chỉnh sửa thông tin chi nhánh đã tồn tại.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cập nhật thành công"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy chi nhánh")
    })
    @PutMapping("/{id}")
    public ResponseEntity<BranchDTO> update(
            @Parameter(description = "ID của chi nhánh cần sửa", example = "1", required = true)
            @PathVariable Long id,
            @RequestBody BranchDTO dto) {
        return ResponseEntity.ok(branchService.update(id, dto));
    }

    @Operation(summary = "Xóa chi nhánh", description = "Xóa mềm (Soft Delete) hoặc vô hiệu hóa một chi nhánh.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Xóa thành công"),
            @ApiResponse(responseCode = "400", description = "Không thể xóa chi nhánh đang có dữ liệu ràng buộc")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @Parameter(description = "ID của chi nhánh cần xóa", example = "1", required = true)
            @PathVariable Long id) {
        branchService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
