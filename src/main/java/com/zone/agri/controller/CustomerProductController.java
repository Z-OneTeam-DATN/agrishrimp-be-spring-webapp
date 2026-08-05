package com.zone.agri.controller;

import com.zone.agri.common.AuthUtils;
import com.zone.agri.dto.response.product.ProductResponse;
import com.zone.agri.dto.response.user.UserDetail;
import com.zone.agri.exception.SignInRequiredException;
import com.zone.agri.service.ProductRecommendationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Endpoint san pham danh cho khach hang DA DANG NHAP — co chu dich KHONG dat trong
 * PublicProductController (base "/api/public/**" duoc permitAll o SecurityConfig, va FE tu dong
 * thao Authorization header cho moi request "public"), de dam bao chac chan co duoc danh tinh user
 * qua AuthUtils.getUserDetail().
 */
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@Tag(name = "Customer Product APIs", description = "Các API sản phẩm yêu cầu đăng nhập")
public class CustomerProductController {

    private final ProductRecommendationService productRecommendationService;

    @Operation(summary = "Gợi ý sản phẩm dành cho tài khoản đang đăng nhập",
               description = "Dựa trên các sản phẩm đã mua (đơn COMPLETED) và dữ liệu market-basket đã tính sẵn.")
    @GetMapping("/recommended-for-me")
    public ResponseEntity<List<ProductResponse>> getRecommendedForMe(
            @RequestParam(required = false) Integer limit) {
        UserDetail user = AuthUtils.getUserDetail();
        if (user == null) {
            throw new SignInRequiredException("Vui lòng đăng nhập để xem gợi ý dành cho bạn");
        }
        return ResponseEntity.ok(productRecommendationService.getPersonalizedRecommendations(user.getId(), limit));
    }
}
