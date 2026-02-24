package com.zone.agri.service;

import com.zone.agri.dto.cart.CartItemResponse;
import com.zone.agri.entity.CartItem;
import com.zone.agri.entity.ProductVariant;
import com.zone.agri.entity.User;
import com.zone.agri.repository.CartItemRepository;
import com.zone.agri.repository.ProductVariantRepository;
import com.zone.agri.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CartService {

    private final CartItemRepository cartItemRepo;
    private final ProductVariantRepository variantRepo;
    private final UserRepository userRepo; // Nếu cần lấy User

    // 1. Lấy danh sách giỏ hàng của User
    public List<CartItemResponse> getMyCart(Long userId) {
        List<CartItem> items = cartItemRepo.findByUserId(userId); // Huy nhớ tạo hàm này trong CartItemRepository nhé

        return items.stream().map(item -> {
            ProductVariant variant = item.getProductVariant();
            return CartItemResponse.builder()
                    .id(item.getId())
                    .variantId(variant.getId())
                    .name(variant.getProduct() != null ? variant.getProduct().getName() : "Sản phẩm")
                    .variant(variant.getUnit() != null ? variant.getUnit() : "")
                    .price(variant.getPrice())
                    .quantity(item.getQuantity())
                    .stock(variant.getQuantity()) // Tồn kho hiện tại
                    .image(variant.getImageUrl())
                    .build();
        }).collect(Collectors.toList());
    }

    // 2. Thêm hoặc Cập nhật số lượng (+/-)
    @Transactional
    public void updateCartQuantity(Long userId, Long variantId, Integer delta) {
        User user = userRepo.findById(userId).orElseThrow(() -> new RuntimeException("Không tìm thấy User"));
        ProductVariant variant = variantRepo.findById(variantId).orElseThrow(() -> new RuntimeException("Không tìm thấy sản phẩm"));

        // Tìm xem sản phẩm đã có trong giỏ chưa
        CartItem cartItem = cartItemRepo.findByUserIdAndProductVariantId(userId, variantId)
                .orElseGet(() -> CartItem.builder()
                        .user(user)
                        .productVariant(variant)
                        .quantity(0) // Khởi tạo = 0 nếu chưa có
                        .build());

        int newQuantity = cartItem.getQuantity() + delta;

        if (newQuantity <= 0) {
            // Nếu giảm về 0 hoặc âm thì xóa luôn khỏi giỏ
            if (cartItem.getId() != null) {
                cartItemRepo.delete(cartItem);
            }
        } else {
            // Kiểm tra tồn kho
            if (newQuantity > variant.getQuantity()) {
                throw new RuntimeException("Số lượng yêu cầu vượt quá tồn kho hiện tại!");
            }
            cartItem.setQuantity(newQuantity);
            cartItemRepo.save(cartItem);
        }
    }

    // 3. Xóa hẳn một món khỏi giỏ
    @Transactional
    public void removeCartItem(Long userId, Long cartItemId) {
        CartItem item = cartItemRepo.findById(cartItemId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy sản phẩm trong giỏ"));

        // Đảm bảo chỉ được xóa đồ trong giỏ của chính mình
        if (!item.getUser().getId().equals(userId)) {
            throw new RuntimeException("Không có quyền thực hiện!");
        }

        cartItemRepo.delete(item);
    }
}