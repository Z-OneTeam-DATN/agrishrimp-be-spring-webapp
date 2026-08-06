package com.zone.agri.controller;

import com.zone.agri.dto.request.voucher.VoucherRequest;
import com.zone.agri.dto.response.common.ApiResponse;
import com.zone.agri.dto.response.voucher.VoucherResponse;
import com.zone.agri.entity.enums.VoucherStatus;
import com.zone.agri.security.annotation.RequirePermission;
import com.zone.agri.service.VoucherService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.Errors;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/vouchers")
@Tag(name = "Vouchers", description = "Quản lý mã giảm giá")
@RequiredArgsConstructor
public class VoucherController {

    private final VoucherService voucherService;

    // ==========================================
    // API CÔNG KHAI (KHÔNG YÊU CẦU ĐĂNG NHẬP)
    // ==========================================
    @Operation(summary = "Lấy tất cả voucher công khai", description = "Lấy danh sách các voucher đang kích hoạt (ACTIVE) cho người dùng.")
    @GetMapping("/public")
    public ResponseEntity<ApiResponse<List<VoucherResponse>>> getPublicVouchers() {
        List<VoucherResponse> data = voucherService.getPublicVouchers();
        return ResponseEntity.ok(ApiResponse.success(data, "Lấy danh sách voucher công khai thành công"));
    }

    @Operation(summary = "Lay voucher cong khai theo ma", description = "Lay thong tin chi tiet cua voucher cong khai theo ma.")
    @GetMapping("/public/code/{code}")
    public ResponseEntity<ApiResponse<VoucherResponse>> getPublicVoucherByCode(@PathVariable String code) {
        VoucherResponse data = voucherService.getVoucherByCode(code);
        return ResponseEntity.ok(ApiResponse.success(data, "Lay thong tin voucher thanh cong"));
    }

    // ==========================================
    // API QUẢN TRỊ (YÊU CẦU QUYỀN ADMIN)
    // ==========================================
    @Operation(summary = "Lấy tất cả voucher", description = "Hỗ trợ tìm kiếm theo mã và lọc theo trạng thái")
    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("VOUCHER_VIEW")
    @GetMapping
    public ResponseEntity<ApiResponse<List<VoucherResponse>>> getAll(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) VoucherStatus status) {

        List<VoucherResponse> data = voucherService.getAllVouchers(keyword, status);
        return ResponseEntity.ok(ApiResponse.success(data, "Lấy danh sách voucher thành công"));
    }

    @Operation(summary = "Xem chi tiết voucher", description = "Lấy thông tin chi tiết của một mã giảm giá.")
    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("VOUCHER_VIEW")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<VoucherResponse>> getById(
            @Parameter(description = "ID voucher cần xem", example = "1")
            @PathVariable Long id) {

        VoucherResponse data = voucherService.getVoucherById(id);
        return ResponseEntity.ok(ApiResponse.success(data, "Lấy thông tin voucher thành công"));
    }

    @Operation(summary = "Tạo voucher mới", description = "Thêm mới một mã giảm giá vào hệ thống.")
    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("VOUCHER_CREATE")
    @PostMapping
    public ResponseEntity<ApiResponse<VoucherResponse>> create(
            @Valid @RequestBody VoucherRequest voucherRequest, Errors errors) {

        if (errors.hasErrors()) {
            String error = errors.getAllErrors().get(0).getDefaultMessage();
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(error));
        }

        try {
            VoucherResponse data = voucherService.createVoucher(voucherRequest);
            return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(data, "Tạo voucher thành công"));
        } catch (com.zone.agri.exception.ConflictException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(e.getMessage()));
        }
    }

    @Operation(summary = "Cập nhật voucher", description = "Chỉnh sửa thông tin mã giảm giá.")
    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("VOUCHER_UPDATE")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<VoucherResponse>> update(
            @Parameter(description = "ID voucher cần sửa", example = "1")
            @PathVariable Long id,
            @Valid @RequestBody VoucherRequest voucherRequest, Errors errors) {

        if (errors.hasErrors()) {
            String error = errors.getAllErrors().get(0).getDefaultMessage();
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(error));
        }

        try {
            VoucherResponse data = voucherService.updateVoucher(id, voucherRequest);
            return ResponseEntity.ok(ApiResponse.success(data, "Cập nhật voucher thành công"));
        } catch (com.zone.agri.exception.NotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (com.zone.agri.exception.ConflictException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(e.getMessage()));
        }
    }

    @Operation(summary = "Xóa voucher", description = "Xóa vĩnh viễn một mã giảm giá.")
    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("VOUCHER_DELETE")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @Parameter(description = "ID voucher cần xóa", example = "1")
            @PathVariable Long id) {

        try {
            voucherService.deleteVoucher(id);
            return ResponseEntity.ok(ApiResponse.success(null, "Xóa voucher thành công"));
        } catch (com.zone.agri.exception.NotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        }
    }
}
