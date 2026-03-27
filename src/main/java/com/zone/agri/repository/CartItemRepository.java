package com.zone.agri.repository;

import com.zone.agri.entity.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    // 1. Lấy toàn bộ giỏ hàng của 1 user cụ thể
    @org.springframework.data.jpa.repository.Query("SELECT ci FROM CartItem ci " +
            "JOIN FETCH ci.productVariant pv " +
            "JOIN FETCH pv.product p " +
            "LEFT JOIN FETCH p.category " +
            "LEFT JOIN FETCH p.brand " +
            "WHERE ci.user.id = :userId")
    List<CartItem> findByUserIdWithDetails(@org.springframework.data.repository.query.Param("userId") Long userId);

    List<CartItem> findByUserId(Long userId);

    // 2. Tìm xem 1 sản phẩm (variant) cụ thể đã có trong giỏ hàng của user chưa (Dùng lúc thêm/bớt số lượng)
    Optional<CartItem> findByUserIdAndProductVariantId(Long userId, Long variantId);

    // 3. (Bonus) Xóa toàn bộ giỏ hàng của user (Hàm này rất cần thiết dùng cho lúc Huy làm chức năng "Đặt hàng thành công")
    void deleteByUserId(Long userId);
}