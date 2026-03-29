package com.zone.agri.repository;

import com.zone.agri.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {
    boolean existsByOrderIdAndProductIdAndUserId(Long orderId, Long productId, Long userId);

    long countByProductIdAndStatus(Long productId, com.zone.agri.entity.enums.ReviewStatus status);

    @org.springframework.data.jpa.repository.Query("SELECT AVG(r.rating) FROM Review r WHERE r.product.id = :productId AND r.status = 'VISIBLE'")
    Double averageRatingByProductId(@org.springframework.data.repository.query.Param("productId") Long productId);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"reviewImages", "user", "product"})
    List<Review> findByProductIdAndStatusOrderByCreatedAtDesc(Long productId, com.zone.agri.entity.enums.ReviewStatus status);

    @org.springframework.data.jpa.repository.Query("SELECT COUNT(o) > 0 FROM Order o " +
            "LEFT JOIN o.orderItems item " +
            "LEFT JOIN o.subOrders sub " +
            "LEFT JOIN sub.items subItem " +
            "WHERE o.user.id = :userId " +
            "AND (" +
            "  (o.status = 'COMPLETED' AND item.productVariant.product.id = :productId) " +
            "  OR (sub.status = 'COMPLETED' AND subItem.productVariant.product.id = :productId)" +
            ") " +
            "AND NOT EXISTS (SELECT 1 FROM Review r WHERE r.user.id = :userId AND r.product.id = :productId AND r.order.id = o.id)")
    boolean canUserReviewProduct(@org.springframework.data.repository.query.Param("userId") Long userId, @org.springframework.data.repository.query.Param("productId") Long productId);
}
