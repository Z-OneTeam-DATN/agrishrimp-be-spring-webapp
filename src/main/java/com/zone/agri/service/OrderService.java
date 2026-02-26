package com.zone.agri.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zone.agri.dto.geo.DeliveryInfo;
import com.zone.agri.dto.order.*;
import com.zone.agri.dto.order.CheckoutItemRequest;
import com.zone.agri.dto.order.CheckoutRequest;
import com.zone.agri.dto.order.OrderItemResponse;
import com.zone.agri.dto.order.OrderResponse;
import com.zone.agri.entity.*;
import com.zone.agri.entity.enums.OrderStatus;
import com.zone.agri.entity.enums.PaymentMethod;
import com.zone.agri.entity.enums.PaymentStatus;
import com.zone.agri.exception.BadRequestException;
import com.zone.agri.exception.ConflictException;
import com.zone.agri.exception.NotFoundException;
import vn.payos.type.CheckoutResponseData;
import com.zone.agri.repository.*;
import com.zone.agri.service.BranchSearchService.BranchWithRealDistance;
import com.zone.agri.service.InventoryAllocationService.AllocationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final InventoryRepository inventoryRepository;
    private final BranchRepository branchRepository;
    private final ProductVariantRepository variantRepository;
    private final UserRepository userRepository;
    private final CartItemRepository cartItemRepository;
    private final SubOrderRepository subOrderRepository;
    private final SubOrderItemRepository subOrderItemRepository;

    private final BranchSearchService branchSearchService;
    private final InventoryAllocationService allocationService;
    private final ShippingService shippingService;
    private final PayOSService payOSService;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String PREPARE_KEY_PREFIX = "prepare:";
    private static final long PREPARE_TTL_MINUTES = 30;

    // ══════════════════════════════════════════════════════════════
    // LEGACY: checkout cũ (giữ để backward compatible)
    // ══════════════════════════════════════════════════════════════

    @Transactional
    public Order placeOrder(Long userId, CheckoutRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy thông tin người dùng"));

        if (req.getItems() == null || req.getItems().isEmpty()) {
            throw new BadRequestException("Giỏ hàng rỗng, không thể đặt hàng!");
        }

        Branch selectedBranch = findBranchWithEnoughStock(req.getItems());
        if (selectedBranch == null) {
            throw new BadRequestException("Hiện tại không có chi nhánh nào đủ toàn bộ hàng cho đơn của bạn.");
        }

        Order order = Order.builder()
                .code("ORD" + System.currentTimeMillis())
                .user(user)
                .branch(selectedBranch)
                .shippingAddress(req.getShippingAddress() + " - SĐT: " + req.getPhone() + " - Người nhận: " + req.getFullName())
                .status(OrderStatus.PENDING)
                .paymentMethod(PaymentMethod.COD)
                .paymentStatus(PaymentStatus.UNPAID)
                .createdAt(LocalDateTime.now())
                .totalAmount(BigDecimal.ZERO)
                .discountAmount(BigDecimal.ZERO)
                .finalAmount(BigDecimal.ZERO)
                .build();

        Order savedOrder = orderRepository.save(order);

        List<OrderItem> orderItems = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;

        for (CheckoutItemRequest itemReq : req.getItems()) {
            ProductVariant variant = variantRepository.findById(itemReq.getVariantId())
                    .orElseThrow(() -> new NotFoundException("Sản phẩm không tồn tại"));

            Inventory inventory = inventoryRepository
                    .findByBranchIdAndProductVariantId(selectedBranch.getId(), variant.getId())
                    .orElseThrow(() -> new NotFoundException("Không tìm thấy tồn kho"));
            inventory.setQuantity(inventory.getQuantity() - itemReq.getQuantity());
            inventoryRepository.save(inventory);

            OrderItem orderItem = OrderItem.builder()
                    .order(savedOrder)
                    .productVariant(variant)
                    .quantity(itemReq.getQuantity())
                    .price(variant.getPrice() != null ? variant.getPrice() : BigDecimal.ZERO)
                    .build();
            orderItems.add(orderItem);

            BigDecimal itemTotal = orderItem.getPrice().multiply(new BigDecimal(orderItem.getQuantity()));
            totalAmount = totalAmount.add(itemTotal);
        }

        orderItemRepository.saveAll(orderItems);

        savedOrder.setTotalAmount(totalAmount);
        BigDecimal shippingFee = new BigDecimal("15000");
        savedOrder.setFinalAmount(totalAmount.add(shippingFee).subtract(savedOrder.getDiscountAmount()));
        orderRepository.save(savedOrder);

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
    /**
     * Bước 1: Chuẩn bị đơn hàng — KHÔNG lưu DB.
     * Trả về bản nháp phân bổ + phí ship, lưu token vào Redis 30 phút.
     */
    public PrepareOrderResponse prepareOrder(Long userId, PrepareOrderRequest request) {
        // 1. Validate tất cả productVariantId tồn tại
        List<Long> variantIds = request.getCart().stream()
                .map(CartItemDto::getProductVariantId)
                .distinct()
                .toList();

        List<ProductVariant> variants = variantRepository.findAllById(variantIds);
        if (variants.size() != variantIds.size()) {
            throw new NotFoundException("Một hoặc nhiều sản phẩm không tồn tại trong hệ thống");
        }

        Map<Long, ProductVariant> variantMap = variants.stream()
                .collect(Collectors.toMap(ProductVariant::getId, Function.identity()));

        // 2. Resolve location (fallback nếu FE không gửi tọa độ)
        double userLat = request.getUserLat() != null ? request.getUserLat() : 10.0341;
        double userLng = request.getUserLng() != null ? request.getUserLng() : 105.7904; // Cần Thơ default

        // 3. Tìm chi nhánh gần nhất (bounding box → haversine → ORS, fallback toàn bộ ACTIVE)
        List<BranchWithRealDistance> nearestBranches = branchSearchService.findNearestBranches(userLat, userLng);

        if (nearestBranches.isEmpty()) {
            throw new NotFoundException("Hệ thống hiện không có chi nhánh nào đang hoạt động. Vui lòng liên hệ hotline.");
        }

        List<Long> branchIds = nearestBranches.stream()
                .map(bwr -> bwr.branch().getId())
                .toList();

        // 4. Build inventory matrix (1 DB query)
        Map<Long, Map<Long, Integer>> inventoryMatrix = allocationService.buildInventoryMatrix(branchIds, variantIds);

        // 5. Greedy allocation
        AllocationResult allocation = allocationService.allocate(
                request.getCart(), variantMap, nearestBranches, inventoryMatrix);

        // 6. Enrich với shipping fee (song song)
        DeliveryInfo deliveryInfo = DeliveryInfo.builder()
                .toDistrictId(request.getDeliveryDistrictId())
                .toWardCode(request.getDeliveryWardCode())
                .deliveryAddress(request.getDeliveryAddress())
                .userLat(userLat)
                .userLng(userLng)
                .build();

        List<SubOrderDraftDto> enrichedSubOrders = shippingService.enrichWithShippingFees(
                allocation.subOrders(), deliveryInfo, variantMap);

        // 7. Tính tổng
        BigDecimal totalSubtotal = enrichedSubOrders.stream()
                .map(s -> s.getSubtotal() != null ? s.getSubtotal() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalShippingFee = enrichedSubOrders.stream()
                .map(s -> s.getShippingFee() != null ? s.getShippingFee() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalAmount = totalSubtotal.add(totalShippingFee);
        boolean canFulfill = allocation.outOfStockItems().isEmpty();

        // 8. Lưu draft vào Redis
        String token = UUID.randomUUID().toString();
        PrepareOrderDraft draft = PrepareOrderDraft.builder()
                .prepareToken(token)
                .userId(userId)
                .userLat(userLat)
                .userLng(userLng)
                .deliveryAddress(request.getDeliveryAddress())
                .deliveryDistrictId(request.getDeliveryDistrictId())
                .deliveryProvinceId(request.getDeliveryProvinceId())
                .deliveryWardCode(request.getDeliveryWardCode())
                .subOrders(enrichedSubOrders)
                .outOfStockItems(allocation.outOfStockItems())
                .totalSubtotal(totalSubtotal)
                .totalShippingFee(totalShippingFee)
                .totalAmount(totalAmount)
                .build();

        saveDraftToRedis(token, draft);

        return PrepareOrderResponse.builder()
                .prepareToken(token)
                .canFulfill(canFulfill)
                .subOrders(enrichedSubOrders)
                .totalSubtotal(totalSubtotal)
                .totalShippingFee(totalShippingFee)
                .totalAmount(totalAmount)
                .outOfStockItems(allocation.outOfStockItems())
                .build();
    }

    // ══════════════════════════════════════════════════════════════
    // NEW: Confirm Order (lưu DB + trừ kho)
    // ══════════════════════════════════════════════════════════════

    /**
     * Bước 2: Xác nhận đơn — lưu DB, trừ kho (có pessimistic lock).
     */
    @Transactional
    public ConfirmOrderResponse confirmOrder(Long userId, ConfirmOrderRequest request) {
        // 1. Lấy draft từ Redis
        PrepareOrderDraft draft = getDraftFromRedis(request.getPrepareToken());
        if (draft == null) {
            throw new BadRequestException("Token đã hết hạn hoặc không hợp lệ. Vui lòng thực hiện lại bước chuẩn bị đơn.");
        }

        // 2. Validate userId khớp với token
        if (!userId.equals(draft.getUserId())) {
            throw new BadRequestException("Token không thuộc về tài khoản này.");
        }

        // 3. Double-check tồn kho (với pessimistic lock)
        for (SubOrderDraftDto subDraft : draft.getSubOrders()) {
            for (OrderItemDto item : subDraft.getItems()) {
                Optional<Inventory> invOpt = inventoryRepository.findForUpdate(
                        subDraft.getBranchId(), item.getProductVariantId());

                if (invOpt.isEmpty() || invOpt.get().getQuantity() < item.getQuantity()) {
                    int available = invOpt.map(Inventory::getQuantity).orElse(0);
                    throw new ConflictException(
                            "Hàng vừa hết hoặc không đủ số lượng. Vui lòng đặt hàng lại. "
                                    + "(Sản phẩm ID " + item.getProductVariantId()
                                    + " tại chi nhánh " + subDraft.getBranchName()
                                    + ": có " + available + ", cần " + item.getQuantity() + ")"
                    );
                }
            }
        }

        // 4. Tạo Order (đơn cha)
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Tài khoản không tồn tại"));

        PaymentMethod paymentMethod = request.getPaymentMethod() != null
                ? request.getPaymentMethod() : PaymentMethod.COD;

        Order order = Order.builder()
                .code("ORD" + System.currentTimeMillis())
                .user(user)
                .status(OrderStatus.PENDING)
                .paymentMethod(paymentMethod)
                .paymentStatus(PaymentStatus.UNPAID)
                .createdAt(LocalDateTime.now())
                .totalAmount(draft.getTotalSubtotal())
                .discountAmount(BigDecimal.ZERO)
                .finalAmount(draft.getTotalAmount())
                .totalShippingFee(draft.getTotalShippingFee())
                .userLat(draft.getUserLat())
                .userLng(draft.getUserLng())
                .deliveryAddress(draft.getDeliveryAddress())
                .shippingAddress(draft.getDeliveryAddress())
                .note(request.getNote())
                .build();

        Order savedOrder = orderRepository.save(order);

        // 5. Tạo SubOrder + SubOrderItem + trừ kho
        List<SubOrderSummaryDto> subOrderSummaries = new ArrayList<>();

        for (SubOrderDraftDto subDraft : draft.getSubOrders()) {
            Branch branch = branchRepository.findById(subDraft.getBranchId())
                    .orElseThrow(() -> new NotFoundException("Chi nhánh không tồn tại: " + subDraft.getBranchId()));

            SubOrder subOrder = SubOrder.builder()
                    .order(savedOrder)
                    .branch(branch)
                    .status(OrderStatus.PENDING)
                    .subtotal(subDraft.getSubtotal())
                    .shippingFee(subDraft.getShippingFee())
                    .estimatedDays(subDraft.getEstimatedDays())
                    .carrier(subDraft.getCarrier())
                    .build();

            SubOrder savedSubOrder = subOrderRepository.save(subOrder);

            for (OrderItemDto item : subDraft.getItems()) {
                ProductVariant variant = variantRepository.findById(item.getProductVariantId())
                        .orElseThrow(() -> new NotFoundException("Sản phẩm không tồn tại: " + item.getProductVariantId()));

                SubOrderItem subOrderItem = SubOrderItem.builder()
                        .subOrder(savedSubOrder)
                        .productVariant(variant)
                        .quantity(item.getQuantity())
                        .unitPrice(item.getUnitPrice())
                        .build();
                subOrderItemRepository.save(subOrderItem);

                // Trừ tồn kho (đã lock ở trên)
                Inventory inventory = inventoryRepository
                        .findForUpdate(subDraft.getBranchId(), item.getProductVariantId())
                        .orElseThrow(() -> new NotFoundException("Tồn kho không tồn tại"));
                inventory.setQuantity(inventory.getQuantity() - item.getQuantity());
                inventoryRepository.save(inventory);
            }

            subOrderSummaries.add(SubOrderSummaryDto.builder()
                    .subOrderId(savedSubOrder.getId())
                    .branchId(branch.getId())
                    .branchName(branch.getName())
                    .status(OrderStatus.PENDING.name())
                    .subtotal(subDraft.getSubtotal())
                    .shippingFee(subDraft.getShippingFee())
                    .estimatedDays(subDraft.getEstimatedDays())
                    .carrier(subDraft.getCarrier())
                    .build());
        }

        // 6. Tạo payOS payment link nếu chọn phương thức PAYOS
        String checkoutUrl = null;
        if (PaymentMethod.PAYOS.equals(paymentMethod)) {
            log.info("Order {} selected PAYOS payment method. Creating link...", savedOrder.getCode());
            try {
                CheckoutResponseData payosData = payOSService.createPaymentLink(savedOrder);
                savedOrder.setPayosPaymentLinkId(payosData.getPaymentLinkId());
                savedOrder.setPayosCheckoutUrl(payosData.getCheckoutUrl());
                orderRepository.save(savedOrder);
                checkoutUrl = payosData.getCheckoutUrl();
                log.info("PayOS link created for order {}: {}", savedOrder.getCode(), checkoutUrl);
            } catch (Exception e) {
                log.error("Failed to create payOS payment link for order {}: {}", savedOrder.getCode(), e.getMessage());
                throw new BadRequestException("Không thể tạo link thanh toán. Vui lòng thử lại. Lỗi: " + e.getMessage());
            }
        } else {
            log.info("Order {} selected {} payment method. No PayOS link needed.", savedOrder.getCode(), paymentMethod);
        }

        // 7. Xóa draft khỏi Redis
        redisTemplate.delete(PREPARE_KEY_PREFIX + request.getPrepareToken());

        // 8. Xóa giỏ hàng
        draft.getSubOrders().stream()
                .flatMap(s -> s.getItems().stream())
                .map(OrderItemDto::getProductVariantId)
                .distinct()
                .forEach(variantId -> cartItemRepository
                        .findByUserIdAndProductVariantId(userId, variantId)
                        .ifPresent(cartItemRepository::delete));

        return ConfirmOrderResponse.builder()
                .orderId(savedOrder.getId())
                .orderCode(savedOrder.getCode())
                .status(OrderStatus.PENDING.name())
                .subOrders(subOrderSummaries)
                .totalAmount(draft.getTotalAmount())
                .totalShippingFee(draft.getTotalShippingFee())
                .checkoutUrl(checkoutUrl)
                .build();
    }

    // ══════════════════════════════════════════════════════════════
    // Private helpers
    // ══════════════════════════════════════════════════════════════

    private void saveDraftToRedis(String token, PrepareOrderDraft draft) {
        try {
            String json = objectMapper.writeValueAsString(draft);
            redisTemplate.opsForValue().set(
                    PREPARE_KEY_PREFIX + token,
                    json,
                    PREPARE_TTL_MINUTES,
                    TimeUnit.MINUTES
            );
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize PrepareOrderDraft: {}", e.getMessage());
            throw new RuntimeException("Không thể lưu trữ dữ liệu đơn hàng tạm thời");
        }
    }

    private PrepareOrderDraft getDraftFromRedis(String token) {
        String json = redisTemplate.opsForValue().get(PREPARE_KEY_PREFIX + token);
        if (json == null) return null;
        try {
            return objectMapper.readValue(json, PrepareOrderDraft.class);
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize PrepareOrderDraft: {}", e.getMessage());
            return null;
        }
    }

    private Branch findBranchWithEnoughStock(List<CheckoutItemRequest> itemsToBuy) {
        List<Branch> allBranches = branchRepository.findAll();
        for (Branch branch : allBranches) {
            boolean isEnough = true;
            for (CheckoutItemRequest item : itemsToBuy) {
                Optional<Inventory> invOpt = inventoryRepository
                        .findByBranchIdAndProductVariantId(branch.getId(), item.getVariantId());
                if (invOpt.isEmpty() || invOpt.get().getQuantity() < item.getQuantity()) {
                    isEnough = false;
                    break;
                }
            }
            if (isEnough) return branch;
        }
        return null;
    }


    // ==========================================
    // LẤY DANH SÁCH ĐƠN HÀNG CHO ADMIN
    // ==========================================
    public List<OrderResponse> getAllAdminOrders() {
        return orderRepository.findAll().stream().map(order -> {
            String custName = order.getUser() != null ? order.getUser().getFullName() : "Khách hàng";
            String custPhone = order.getUser() != null ? order.getUser().getPhoneNumber() : "";

            // --- ĐOẠN CODE MỚI THÊM: Lấy danh sách sản phẩm ---
            List<OrderItemResponse> itemResponses = new ArrayList<>();
            if (order.getOrderItems() != null) {
                itemResponses = order.getOrderItems().stream().map(item -> {
                    String pName = "Sản phẩm không xác định";
                    String pSku = "N/A";
                    String pImg = null;

                    if (item.getProductVariant() != null) {
                        pSku = item.getProductVariant().getSku();
                        // Giả sử Entity Product của bạn có trường image hoặc thumbnail, bạn có thể lấy ra ở đây
                        if (item.getProductVariant().getProduct() != null) {
                            pName = item.getProductVariant().getProduct().getName();
                        }
                    }

                    BigDecimal itemTotal = item.getPrice() != null ?
                            item.getPrice().multiply(new BigDecimal(item.getQuantity())) : BigDecimal.ZERO;

                    return OrderItemResponse.builder()
                            .id(item.getId())
                            .productName(pName)
                            .sku(pSku)
                            .image(pImg)
                            .quantity(item.getQuantity())
                            .price(item.getPrice() != null ? item.getPrice() : BigDecimal.ZERO)
                            .totalPrice(itemTotal)
                            .build();
                }).toList();
            }
            // ----------------------------------------------------

            return OrderResponse.builder()
                    .id(order.getId())
                    .code(order.getCode())
                    .customerName(custName)
                    .customerPhone(custPhone)
                    .finalAmount(order.getFinalAmount() != null ? order.getFinalAmount() : BigDecimal.ZERO)
                    .paymentMethod(order.getPaymentMethod() != null ? order.getPaymentMethod().name() : "")
                    .paymentStatus(order.getPaymentStatus() != null ? order.getPaymentStatus().name() : "UNPAID")
                    .status(order.getStatus() != null ? order.getStatus().name() : "PENDING")
                    .branchName(order.getBranch() != null ? order.getBranch().getName() : "Chưa phân bổ")
                    .createdAt(order.getCreatedAt())
                    .shippingAddress(order.getShippingAddress())
                    .items(itemResponses) // <-- Nhét list sản phẩm vào đây
                    .build();
        }).toList();
    }
    // ==========================================
    // CẬP NHẬT TRẠNG THÁI ĐƠN HÀNG (MODULE 2)
    // ==========================================
    @Transactional
    public void updateOrderStatus(Long orderId, OrderStatus newStatus){
            Order order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy đơn hàng ID: " + orderId));

            // Tùy chọn: Thêm các rule chặn logic ở đây (VD: Đơn đã Hủy thì không được Xác nhận)
            if (order.getStatus() == OrderStatus.CANCELLED || order.getStatus() == OrderStatus.COMPLETED) {
                throw new RuntimeException("Không thể thay đổi trạng thái của đơn hàng đã đóng!");
            }

            order.setStatus(newStatus);
            orderRepository.save(order);
        }
}
