package com.zone.agri.controller;

import com.zone.agri.dto.request.review.ReviewRequest;
import com.zone.agri.dto.response.review.ReviewResponse;
import com.zone.agri.entity.User;
import com.zone.agri.exception.SignInRequiredException;
import com.zone.agri.repository.UserRepository;
import com.zone.agri.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
@Tag(name = "Review Management", description = "Quản lý đánh giá sản phẩm")
@SecurityRequirement(name = "bearerAuth")
public class ReviewController {

    private final ReviewService reviewService;
    private final UserRepository userRepository;

    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new SignInRequiredException("Vui lòng đăng nhập để thực hiện đánh giá");
        }
        return userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new SignInRequiredException("Tài khoản không tồn tại"));
    }

    @Operation(summary = "Kiểm tra quyền đánh giá sản phẩm", description = "Kiểm tra xem người dùng hiện tại có thể đánh giá sản phẩm này không")
    @GetMapping("/can-review/{productId}")
    public ResponseEntity<Boolean> canReview(@PathVariable Long productId) {
        User user = getCurrentUser();
        return ResponseEntity.ok(reviewService.canUserReviewProduct(user.getId(), productId));
    }

    @Operation(summary = "Gửi đánh giá sản phẩm", description = "Người dùng đánh giá sản phẩm sau khi đơn hàng hoàn thành")
    @PostMapping
    public ResponseEntity<ReviewResponse> createReview(@Valid @RequestBody ReviewRequest request) {
        User user = getCurrentUser();
        return ResponseEntity.status(HttpStatus.CREATED).body(reviewService.createReview(user.getId(), request));
    }
}
