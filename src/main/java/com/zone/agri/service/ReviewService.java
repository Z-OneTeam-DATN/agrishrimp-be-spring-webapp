package com.zone.agri.service;

import com.zone.agri.dto.request.review.ReviewRequest;
import com.zone.agri.dto.response.review.ReviewResponse;
import com.zone.agri.entity.*;
import com.zone.agri.entity.enums.ReviewStatus;
import com.zone.agri.exception.BadRequestException;
import com.zone.agri.exception.NotFoundException;
import com.zone.agri.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final ReviewImageRepository reviewImageRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;

    @Transactional
    public ReviewResponse createReview(Long userId, ReviewRequest request) {
        // 1. Kiểm tra sản phẩm
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new NotFoundException("Sản phẩm không tồn tại"));

        // 2. Kiểm tra User
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Người dùng không tồn tại"));

        // 3. Kiểm tra điều kiện: Đã mua + Đơn hàng COMPLETED
        boolean hasBought = orderRepository.existsCompletedOrderWithProduct(request.getOrderId(), userId, request.getProductId());
        if (!hasBought) {
            throw new BadRequestException("Bạn chỉ có thể đánh giá sản phẩm đã mua và đơn hàng đã hoàn thành.");
        }

        // 4. Kiểm tra xem đã đánh giá chưa (Tránh spam)
        boolean alreadyReviewed = reviewRepository.existsByOrderIdAndProductIdAndUserId(request.getOrderId(), request.getProductId(), userId);
        if (alreadyReviewed) {
            throw new BadRequestException("Bạn đã đánh giá sản phẩm này cho đơn hàng hiện tại rồi.");
        }

        // 5. Tạo đánh giá
        Order order = orderRepository.findById(request.getOrderId()).orElseThrow();

        Review review = Review.builder()
                .rating(request.getRating())
                .comment(request.getComment())
                .status(ReviewStatus.VISIBLE)
                .createdAt(LocalDateTime.now())
                .user(user)
                .product(product)
                .order(order)
                .build();

        Review savedReview = reviewRepository.save(review);

        // 6. Lưu hình ảnh nếu có
        if (request.getImageUrls() != null && !request.getImageUrls().isEmpty()) {
            List<ReviewImage> images = request.getImageUrls().stream()
                    .map(url -> ReviewImage.builder()
                            .mediaUrl(url)
                            .mediaType(com.zone.agri.entity.enums.ReviewMediaType.IMAGE)
                            .review(savedReview)
                            .build())
                    .collect(Collectors.toList());
            reviewImageRepository.saveAll(images);
            savedReview.setReviewImages(images);
        }

        // 7. Cập nhật rating trung bình và số lượng đánh giá của sản phẩm
        updateProductRating(product);

        return mapToResponse(savedReview);
    }

    @Transactional(readOnly = true)
    public List<ReviewResponse> getReviewsByProductId(Long productId) {
        return reviewRepository.findByProductIdAndStatusOrderByCreatedAtDesc(productId, ReviewStatus.VISIBLE)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ReviewResponse> getReviewsByProductSlug(String slug) {
        Product product = productRepository.findBySlug(slug)
                .orElseThrow(() -> new NotFoundException("Sản phẩm không tồn tại với slug: " + slug));
        
        return getReviewsByProductId(product.getId());
    }

    @Transactional(readOnly = true)
    public boolean canUserReviewProduct(Long userId, Long productId) {
        // Tìm xem có đơn hàng nào COMPLETED chứa sản phẩm này không
        // Lưu ý: existsCompletedOrderWithProduct yêu cầu orderId, 
        // ở đây ta cần một query rộng hơn cho toàn bộ lịch sử mua hàng của user.
        return reviewRepository.canUserReviewProduct(userId, productId);
    }

    private void updateProductRating(Product product) {
        long reviewCount = reviewRepository.countByProductIdAndStatus(product.getId(), ReviewStatus.VISIBLE);
        Double averageRating = reviewRepository.averageRatingByProductId(product.getId());

        product.setReviewCount((int) reviewCount);
        product.setRatingAverage(averageRating != null ? averageRating.floatValue() : 0.0f);
        productRepository.save(product);
    }

    private ReviewResponse mapToResponse(Review review) {
        String pName = "N/A";
        Long pId = null;
        if (review.getProduct() != null) {
            pId = review.getProduct().getId();
            pName = review.getProduct().getName();
        }

        String uName = "Người dùng AgriShrimp";
        if (review.getUser() != null) {
            uName = review.getUser().getFullName() != null ? review.getUser().getFullName() : "Khách hàng";
        }

        return ReviewResponse.builder()
                .id(review.getId())
                .productId(pId)
                .productName(pName)
                .rating(review.getRating())
                .comment(review.getComment())
                .imageUrls(review.getReviewImages() != null ? 
                    review.getReviewImages().stream()
                        .map(img -> img.getMediaUrl() != null ? img.getMediaUrl() : "")
                        .collect(Collectors.toList()) : List.of())
                .createdAt(review.getCreatedAt())
                .userName(uName)
                .build();
    }
}
