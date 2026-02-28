package com.zone.agri.service;

import com.zone.agri.dto.cart.CartItemResponse;
import com.zone.agri.entity.CartItem;
import com.zone.agri.entity.Inventory;
import com.zone.agri.entity.Product;
import com.zone.agri.entity.ProductVariant;
import com.zone.agri.entity.User;
import com.zone.agri.exception.BadRequestException;
import com.zone.agri.repository.CartItemRepository;
import com.zone.agri.repository.InventoryRepository;
import com.zone.agri.repository.ProductVariantRepository;
import com.zone.agri.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CartService {

    private final CartItemRepository cartItemRepo;
    private final ProductVariantRepository variantRepo;
    private final UserRepository userRepo;
    private final InventoryRepository inventoryRepository;
    private final SettingService settingService; // 👉 Bổ sung SettingService

    // 1. Lấy danh sách giỏ hàng của User
    public List<CartItemResponse> getMyCart(Long userId) {
        List<CartItem> items = cartItemRepo.findByUserId(userId);

        return items.stream().map(item -> {
            ProductVariant variant = item.getProductVariant();
            Product product = variant.getProduct();

            // Tạo tên phân loại đầy đủ từ các thuộc tính (VD: Màu sắc: Đỏ, Kích thước: L)
            String variantName = (variant.getAttributeValues() != null && !variant.getAttributeValues().isEmpty())
                    ? variant.getAttributeValues().stream()
                    .map(sav -> sav.getAttribute().getName() + ": " + sav.getAttributeValue().getValue())
                    .collect(Collectors.joining(", "))
                    : variant.getSku(); // Fallback về SKU nếu không có thuộc tính

            // 👉 LOGIC LÔ HÀNG ĐỘNG: Tính tổng tồn và giá bán
            List<Inventory> batches = inventoryRepository.findByProductVariantId(variant.getId());

            int totalStock = batches.stream()
                    .filter(inv -> inv.getQuantity() != null && inv.getQuantity() > 0)
                    .mapToInt(Inventory::getQuantity)
                    .sum();

            BigDecimal sellingPrice = BigDecimal.ZERO;
            Optional<Inventory> oldestBatch = batches.stream()
                    .filter(inv -> inv.getQuantity() != null && inv.getQuantity() > 0)
                    .min(Comparator.comparing(Inventory::getId)); // FIFO

            if (oldestBatch.isPresent()) {
                BigDecimal importPrice = oldestBatch.get().getImportPrice() != null
                        ? oldestBatch.get().getImportPrice()
                        : BigDecimal.ZERO;
                // 👉 TÍNH GIÁ ĐỘNG TỪ CẤU HÌNH HỆ THỐNG
                sellingPrice = importPrice.multiply(settingService.getProfitMultiplier());
            }

            return CartItemResponse.builder()
                    .id(item.getId())
                    .variantId(variant.getId())
                    .name(product != null ? product.getName() : "Sản phẩm")
                    .variant(variantName)
                    .variantName(variantName)
                    .categoryName(product != null && product.getCategory() != null ? product.getCategory().getName() : "")
                    .brandName(product != null && product.getBrand() != null ? product.getBrand().getName() : "")
                    .productForm("")
                    .price(sellingPrice) // 👉 Dùng giá bán động
                    .quantity(item.getQuantity())
                    .stock(totalStock)   // 👉 Dùng tổng tồn kho động
                    .image(variant.getImageUrl())
                    .build();
        }).collect(Collectors.toList());
    }

    // 2. Thêm hoặc Cập nhật số lượng (+/-)
    @Transactional
    public void updateCartQuantity(Long userId, Long variantId, Integer delta) {
        User user = userRepo.findById(userId).orElseThrow(() -> new BadRequestException("Không tìm thấy User"));
        ProductVariant variant = variantRepo.findById(variantId).orElseThrow(() -> new BadRequestException("Không tìm thấy sản phẩm"));

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
            // 👉 KIỂM TRA TỒN KHO TRƯỚC KHI CHO THÊM VÀO GIỎ
            int totalStock = inventoryRepository.findByProductVariantId(variantId).stream()
                    .filter(inv -> inv.getQuantity() != null && inv.getQuantity() > 0)
                    .mapToInt(Inventory::getQuantity)
                    .sum();

            if (newQuantity > totalStock) {
                throw new BadRequestException("Số lượng yêu cầu vượt quá tồn kho hiện tại (" + totalStock + " sản phẩm).");
            }

            cartItem.setQuantity(newQuantity);
            cartItemRepo.save(cartItem);
        }
    }

    // 3. Xóa hẳn một món khỏi giỏ
    @Transactional
    public void removeCartItem(Long userId, Long cartItemId) {
        CartItem item = cartItemRepo.findById(cartItemId)
                .orElseThrow(() -> new BadRequestException("Không tìm thấy sản phẩm trong giỏ"));

        // Đảm bảo chỉ được xóa đồ trong giỏ của chính mình
        if (!item.getUser().getId().equals(userId)) {
            throw new BadRequestException("Không có quyền thực hiện!");
        }

        cartItemRepo.delete(item);
    }
}