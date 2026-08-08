package com.zone.agri.controller;

import com.zone.agri.dto.response.admin.AttributeDTO;
import com.zone.agri.entity.enums.AttributeStatus;
import com.zone.agri.security.annotation.RequirePermission;
import com.zone.agri.service.AttributeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/attributes")
@RequiredArgsConstructor
@Tag(name = "Product Attributes", description = "Quản lý thuộc tính sản phẩm (Màu sắc, Kích thước, Đơn vị tính...)")
public class AttributeController {

    private final AttributeService service;

    @Operation(summary = "Danh sách thuộc tính", description = "Lấy toàn bộ danh sách thuộc tính có trong hệ thống.")
    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("ATTRIBUTE_VIEW")
    @GetMapping
    public ResponseEntity<List<AttributeDTO>> getAll(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) AttributeStatus status) {
        return ResponseEntity.ok(service.getAll(keyword, status));
    }

    @Operation(summary = "Chi tiết thuộc tính", description = "Lấy thông tin chi tiết và các giá trị con của một thuộc tính.")
    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("ATTRIBUTE_VIEW")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Thành công", content = @Content(schema = @Schema(implementation = AttributeDTO.class))),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy thuộc tính")
    })
    @GetMapping("/{id}")
    public ResponseEntity<AttributeDTO> getById(
            @Parameter(description = "ID thuộc tính", example = "1")
            @PathVariable Long id) {
        return ResponseEntity.ok(service.getById(id));
    }

    @Operation(summary = "Tạo thuộc tính mới", description = "Thêm mới một loại thuộc tính (Ví dụ: Màu sắc) và các giá trị của nó.")
    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("ATTRIBUTE_CREATE")
    @PostMapping
    public ResponseEntity<?> create(@Valid @RequestBody AttributeDTO dto) {
        return ResponseEntity.ok(service.create(dto));
    }

    @Operation(summary = "Cập nhật thuộc tính", description = "Sửa đổi tên hoặc danh sách giá trị của thuộc tính.")
    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("ATTRIBUTE_UPDATE")
    @PutMapping("/{id}")
    public ResponseEntity<?> update(
            @Parameter(description = "ID thuộc tính", example = "1")
            @PathVariable Long id,
            @Valid @RequestBody AttributeDTO dto) {
        return ResponseEntity.ok(service.update(id, dto));
    }

    @Operation(summary = "Xóa thuộc tính", description = "Xóa thuộc tính nếu chưa được gán cho sản phẩm nào.")
    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("ATTRIBUTE_DELETE")
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(
            @Parameter(description = "ID thuộc tính", example = "1")
            @PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.ok("Đã xóa thành công");
    }
}
