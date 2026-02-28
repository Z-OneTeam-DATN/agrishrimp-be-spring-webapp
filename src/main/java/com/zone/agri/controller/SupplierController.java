package com.zone.agri.controller;

import com.zone.agri.dto.common.MessageResponse;
import com.zone.agri.dto.supplier.SupplierRequest;
import com.zone.agri.dto.supplier.SupplierResponse;
import com.zone.agri.service.SupplierService;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/suppliers")
@RequiredArgsConstructor
@Tag(name = "Supplier Management", description = "Quản lý đối tác cung cấp, nhà phân phối và lịch sử nhập hàng")
@CrossOrigin(origins = "http://localhost:3000")
public class SupplierController {

    private final SupplierService supplierService;

    @Operation(summary = "Thêm nhà cung cấp mới", description = "Tạo hồ sơ nhà cung cấp với đầy đủ thông tin pháp lý và liên hệ.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tạo thành công", content = @Content(schema = @Schema(implementation = SupplierResponse.class))),
            @ApiResponse(responseCode = "400", description = "Mã số thuế hoặc Email đã tồn tại")
    })
    @PostMapping
    public ResponseEntity<SupplierResponse> createSupplier(@Valid @RequestBody SupplierRequest request) {
        return ResponseEntity.ok(supplierService.createSupplier(request));
    }

    @Operation(summary = "Danh sách nhà cung cấp", description = "Tìm kiếm, lọc theo trạng thái và phân trang danh sách nhà cung cấp.")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping
    public ResponseEntity<Page<SupplierResponse>> getAllSuppliers(
            @Parameter(description = "Từ khóa tìm kiếm (Tên, MST, SĐT)", example = "Công ty TNHH A")
            @RequestParam(required = false) String keyword,

            @Parameter(description = "Trạng thái hoạt động", example = "ACTIVE")
            @RequestParam(required = false) String status,

            @Parameter(hidden = true)
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        // Đã xóa tham số category ở đây
        return ResponseEntity.ok(supplierService.getAllSuppliers(keyword, status, pageable));
    }

    @Operation(summary = "Chi tiết nhà cung cấp", description = "Xem hồ sơ chi tiết của một đối tác.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tìm thấy hồ sơ", content = @Content(schema = @Schema(implementation = SupplierResponse.class))),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy ID nhà cung cấp")
    })
    @GetMapping("/{id}")
    public ResponseEntity<SupplierResponse> getSupplierById(
            @Parameter(description = "ID nhà cung cấp", example = "1", required = true)
            @PathVariable Long id) {
        return ResponseEntity.ok(supplierService.getSupplierById(id));
    }

    @Operation(summary = "Cập nhật thông tin", description = "Chỉnh sửa thông tin liên hệ, người đại diện hoặc trạng thái hợp tác.")
    @SecurityRequirement(name = "bearerAuth")
    @PutMapping("/{id}")
    public ResponseEntity<SupplierResponse> updateSupplier(
            @Parameter(description = "ID nhà cung cấp cần sửa", example = "1", required = true)
            @PathVariable Long id,
            @Valid @RequestBody SupplierRequest request
    ) {
        return ResponseEntity.ok(supplierService.updateSupplier(id, request));
    }

    @Operation(summary = "Xóa nhà cung cấp", description = "Xóa hoặc vô hiệu hóa nhà cung cấp khỏi danh sách đối tác.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Xóa thành công"),
            @ApiResponse(responseCode = "400", description = "Không thể xóa do có đơn nhập hàng liên quan")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<MessageResponse> deleteSupplier(
            @Parameter(description = "ID nhà cung cấp cần xóa", example = "1", required = true)
            @PathVariable Long id) {
        supplierService.deleteSupplier(id);
        return ResponseEntity.ok(new MessageResponse("Đã xóa nhà cung cấp thành công!"));
    }

    @Operation(summary = "Lịch sử nhập hàng", description = "Lấy danh sách các phiếu nhập hàng từ nhà cung cấp này.")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/{id}/imports")
    public ResponseEntity<?> getSupplierImports(@PathVariable Long id) {
        return ResponseEntity.ok(supplierService.getImportHistory(id));
    }
}