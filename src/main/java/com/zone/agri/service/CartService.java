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
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CartService {

    private final CartItemRepository cartItemRepo;
    private final ProductVariantRepository variantRepo;
    private final UserRepository userRepo;
    private final InventoryRepository inventoryRepository;
    private final SettingService settingService;

    // 1. Lấy danh sách giỏ hàng của User
    public List<CartItemResponse> getMyCart(Long userId) {
        List<CartItem> items = cartItemRepo.findByUserId(userId);

        // 👉 TỐI ƯU: Lấy hệ số lợi nhuận 1 lần duy nhất ở ngoài vòng lặp
        BigDecimal profitMultiplier = settingService.getProfitMultiplier();

        return items.stream().map(item -> {
            ProductVariant variant = item.getProductVariant();
            Product product = variant.getProduct();

            // Tạo tên phân loại đầy đủ từ các thuộc tính (VD: Màu sắc: Đỏ, Kích thước: L)
            String variantName = (variant.getAttributeValues() != null && !variant.getAttributeValues().isEmpty())
                    ? variant.getAttributeValues().stream()
                    .map(sav -> sav.getAttribute().getName() + ": " + sav.getAttributeValue().getValue())
                    .collect(Collectors.joining(", "))
                    : variant.getSku(); // Fallback về SKU nếu không có thuộc tính

            // Lấy các lô hàng đang còn tồn
            List<Inventory> batches = inventoryRepository.findByProductVariantId(variant.getId());

            int totalStock = batches.stream()
                    .filter(inv -> inv.getQuantity() != null && inv.getQuantity() > 0)
                    .mapToInt(Inventory::getQuantity)
                    .sum();

            // 👉 SỬA LỖI LỆCH GIÁ: Tìm giá nhập cao nhất giống hệt bên ProductService
            BigDecimal maxImportPrice = batches.stream()
                    .filter(inv -> inv.getQuantity() != null && inv.getQuantity() > 0)
                    .map(inv -> inv.getImportPrice() != null ? inv.getImportPrice() : BigDecimal.ZERO)
                    .max(BigDecimal::compareTo)
                    .orElse(BigDecimal.ZERO);

            // 👉 Tính giá bán động
            BigDecimal sellingPrice = maxImportPrice.multiply(profitMultiplier);

            return CartItemResponse.builder()
                    .id(item.getId())
                    .variantId(variant.getId())
                    .name(product != null ? product.getName() : "Sản phẩm")
                    .variant(variantName)
                    .variantName(variantName)
                    .categoryName(product != null && product.getCategory() != null ? product.getCategory().getName() : "")
                    .brandName(product != null && product.getBrand() != null ? product.getBrand().getName() : "")
                    .productForm("")
                    .price(sellingPrice) // 👉 Giá bán động đã đồng bộ 100% với Trang chủ
                    .quantity(item.getQuantity())
                    .stock(totalStock)
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