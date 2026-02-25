package com.zone.agri.service;

import com.zone.agri.dto.order.CheckoutItemRequest;
import com.zone.agri.dto.order.CheckoutRequest;
import com.zone.agri.entity.*;
import com.zone.agri.entity.enums.OrderStatus;
import com.zone.agri.entity.enums.PaymentMethod;
import com.zone.agri.entity.enums.PaymentStatus;
import com.zone.agri.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final InventoryRepository inventoryRepository;
    private final BranchRepository branchRepository;
    private final ProductVariantRepository variantRepository;
    private final UserRepository userRepository;
    private final CartItemRepository cartItemRepository;

    @Transactional
    public Order placeOrder(Long userId, CheckoutRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy thông tin người dùng"));

        if (req.getItems() == null || req.getItems().isEmpty()) {
            throw new RuntimeException("Giỏ hàng rỗng, không thể đặt hàng!");
        }

        // 1. TÌM CHI NHÁNH CÓ ĐỦ HÀNG ĐỂ XUẤT
        Branch selectedBranch = findBranchWithEnoughStock(req.getItems());

        if (selectedBranch == null) {
            throw new RuntimeException("Rất tiếc! Hiện tại không có chi nhánh nào đủ toàn bộ hàng cho đơn của bạn.");
        }

        // ==========================================
        // 2. TẠO ĐƠN HÀNG MỚI (ORDER)
        // ==========================================
        Order order = Order.builder()
                .code("ORD" + System.currentTimeMillis()) // Random mã đơn
                .user(user)
                .branch(selectedBranch)
                .shippingAddress(req.getShippingAddress() + " - SĐT: " + req.getPhone() + " - Người nhận: " + req.getFullName())
                .status(OrderStatus.PENDING) // Trạng thái chờ xử lý
                .paymentMethod(PaymentMethod.COD) // Hiện tại chỉ hỗ trợ COD
                .paymentStatus(PaymentStatus.UNPAID)
                .createdAt(LocalDateTime.now())
                .totalAmount(BigDecimal.ZERO)
                .discountAmount(BigDecimal.ZERO) // Giả lập chưa có Logic check Voucher ở đây
                .finalAmount(BigDecimal.ZERO)
                .build();

        Order savedOrder = orderRepository.save(order);

        // 3. TẠO CHI TIẾT ĐƠN HÀNG & TRỪ TỒN KHO
        List<OrderItem> orderItems = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;

        for (CheckoutItemRequest itemReq : req.getItems()) {
            ProductVariant variant = variantRepository.findById(itemReq.getVariantId())
                    .orElseThrow(() -> new RuntimeException("Sản phẩm không tồn tại"));

            // Trừ tồn kho tại Chi nhánh đã chọn
            Inventory inventory = inventoryRepository.findByBranchIdAndProductVariantId(selectedBranch.getId(), variant.getId())
                    .orElseThrow();
            inventory.setQuantity(inventory.getQuantity() - itemReq.getQuantity());
            inventoryRepository.save(inventory);

            // Tạo OrderItem
            OrderItem orderItem = OrderItem.builder()
                    .order(savedOrder)
                    .productVariant(variant)
                    .quantity(itemReq.getQuantity())
                    .price(variant.getPrice() != null ? variant.getPrice() : BigDecimal.ZERO)
                    .build();

            orderItems.add(orderItem);

            // Tính tiền
            BigDecimal itemTotal = orderItem.getPrice().multiply(new BigDecimal(orderItem.getQuantity()));
            totalAmount = totalAmount.add(itemTotal);
        }

        orderItemRepository.saveAll(orderItems);

        // Cập nhật lại tổng tiền cho đơn hàng
        savedOrder.setTotalAmount(totalAmount);
        // Tạm fix cứng ship 15k, sau này làm thật sẽ tính theo API GHN/GHTK
        BigDecimal shippingFee = new BigDecimal("15000");
        savedOrder.setFinalAmount(totalAmount.add(shippingFee).subtract(savedOrder.getDiscountAmount()));
        orderRepository.save(savedOrder);

        // 4. XÓA SẢN PHẨM KHỎI GIỎ HÀNG
        for (CheckoutItemRequest itemReq : req.getItems()) {
            Optional<CartItem> cartItem = cartItemRepository.findByUserIdAndProductVariantId(userId, itemReq.getVariantId());
            cartItem.ifPresent(cartItemRepository::delete);
        }

        return savedOrder;
    }

    // Thêm hàm này để trả về dữ liệu cho Frontend
    public List<Order> getOrdersByUserId(Long userId) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    // --- HÀM TÌM CHI NHÁNH ĐỦ HÀNG ---
    private Branch findBranchWithEnoughStock(List<CheckoutItemRequest> itemsToBuy) {
        List<Branch> allBranches = branchRepository.findAll();

        for (Branch branch : allBranches) {
            boolean isEnough = true;

            // Quét qua từng món khách mua xem Chi nhánh này có đủ không
            for (CheckoutItemRequest item : itemsToBuy) {
                Optional<Inventory> invOpt = inventoryRepository.findByBranchIdAndProductVariantId(branch.getId(), item.getVariantId());

                if (invOpt.isEmpty() || invOpt.get().getQuantity() < item.getQuantity()) {
                    isEnough = false; // Thiếu 1 món thôi là loại luôn chi nhánh này
                    break;
                }
            }

            if (isEnough) {
                return branch; // Trả về Chi nhánh đầu tiên thỏa mãn
            }
        }
        return null; // Không có chi nhánh nào đủ hàng
    }
}