package com.zone.agri.controller;

import com.zone.agri.common.AuthUtils;
import com.zone.agri.dto.response.common.ApiResponse;
import com.zone.agri.dto.response.voucher.UserVoucherResponse;
import com.zone.agri.exception.BadRequestException;
import com.zone.agri.service.VoucherService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/vouchers/me")
@RequiredArgsConstructor
@Tag(name = "User Vouchers", description = "Vi voucher cua user")
@SecurityRequirement(name = "bearerAuth")
public class UserVoucherController {

    private final VoucherService voucherService;

    @Operation(summary = "Lay voucher kha dung cua toi")
    @GetMapping("/available")
    public ResponseEntity<ApiResponse<List<UserVoucherResponse>>> getMyAvailableVouchers(
            @RequestParam(required = false) BigDecimal orderSubtotal) {
        List<UserVoucherResponse> data = voucherService.getAvailableVouchersForUser(getCurrentUserId(), orderSubtotal);
        return ResponseEntity.ok(ApiResponse.success(data, "Lay danh sach voucher kha dung thanh cong"));
    }

    @Operation(summary = "Lay vi voucher cua toi")
    @GetMapping("/saved")
    public ResponseEntity<ApiResponse<List<UserVoucherResponse>>> getMySavedVouchers(
            @RequestParam(required = false) BigDecimal orderSubtotal) {
        List<UserVoucherResponse> data = voucherService.getSavedVouchersForUser(getCurrentUserId(), orderSubtotal);
        return ResponseEntity.ok(ApiResponse.success(data, "Lay vi voucher thanh cong"));
    }

    @Operation(summary = "Luu voucher vao vi")
    @PostMapping("/saved/{code}")
    public ResponseEntity<ApiResponse<UserVoucherResponse>> saveVoucherToWallet(@PathVariable String code) {
        UserVoucherResponse data = voucherService.saveVoucherForUser(getCurrentUserId(), code);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(data, "Luu voucher vao vi thanh cong"));
    }

    @Operation(summary = "Xoa voucher khoi vi")
    @DeleteMapping("/saved/{code}")
    public ResponseEntity<ApiResponse<Void>> removeVoucherFromWallet(@PathVariable String code) {
        voucherService.removeSavedVoucherForUser(getCurrentUserId(), code);
        return ResponseEntity.ok(ApiResponse.success(null, "Xoa voucher khoi vi thanh cong"));
    }

    private Long getCurrentUserId() {
        if (AuthUtils.getUserDetail() == null || AuthUtils.getUserDetail().getId() == null) {
            throw new BadRequestException("Vui long dang nhap de tiep tuc");
        }
        return AuthUtils.getUserDetail().getId();
    }
}
