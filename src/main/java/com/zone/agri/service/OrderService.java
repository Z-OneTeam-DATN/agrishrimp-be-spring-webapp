package com.zone.agri.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zone.agri.dto.geo.DeliveryInfo;
import com.zone.agri.dto.order.*;
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
import java.util.stream.Stream;
import java.util.Comparator;

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
    // QUẢN LÝ ĐƠN HÀNG CHO USER
    // ══════════════════════════════════════════════════════════════

    public List<OrderResponse> getMyOrders(Long userId, OrderStatus status) {
        List<Order> orders;
        if (status != null) {
            orders = orderRepository.findByUserIdAndStatusOrderByCreatedAtDesc(userId, status);
        } else {
            orders = orderRepository.findByUserIdOrderByCreatedAtDesc(userId);
        }
        return orders.stream().map(this::mapToOrderResponse).collect(Collectors.toList());
    }

    public OrderResponse getMyOrderDetail(Long userId, Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy đơn hàng ID: " + orderId));

        if (!order.getUser().getId().equals(userId)) {
            throw new BadRequestException("Bạn không có quyền xem đơn hàng này!");
        }

        // Đồng bộ trạng thái từ PayOS nếu vẫn là UNPAID
        if (PaymentMethod.PAYOS.equals(order.getPaymentMethod()) && PaymentStatus.UNPAID.equals(order.getPaymentStatus())) {
            if (payOSService.checkPaymentStatus(order)) {
                order.setPaymentStatus(PaymentStatus.PAID);
                orderRepository.save(order);
            }
        }

        return mapToOrderResponse(order);
    }

    // ══════════════════════════════════════════════════════════════
    // QUẢN LÝ ĐƠN HÀNG CHO ADMIN
    // ══════════════════════════════════════════════════════════════

    public List<OrderResponse> getAdminOrders(OrderStatus status, String search) {
        List<Order> orders;
        if (status != null) {
            orders = orderRepository.findByStatusOrderByCreatedAtDesc(status);
        } else {
            orders = orderRepository.findAllByOrderByCreatedAtDesc();
        }

        if (search != null && !search.isEmpty()) {
            String searchLower = search.toLowerCase();
            orders = orders.stream()
                    .filter(o -> (o.getCode() != null && o.getCode().toLowerCase().contains(searchLower)) ||
                                 (o.getUser() != null && o.getUser().getFullName() != null && o.getUser().getFullName().toLowerCase().contains(searchLower)))
                    .collect(Collectors.toList());
        }

        return orders.stream().map(this::mapToOrderResponse).collect(Collectors.toList());
    }

    public OrderResponse getAdminOrderDetail(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy đơn hàng ID: " + orderId));
        return mapToOrderResponse(order);
    }

    @Transactional
    public void updateOrderStatus(Long orderId, OrderStatus newStatus) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy đơn hàng ID: " + orderId));

        OrderStatus currentStatus = order.getStatus();
        if (currentStatus == OrderStatus.CANCELLED || currentStatus == OrderStatus.COMPLETED || currentStatus == OrderStatus.RETURNED) {
            throw new BadRequestException("Không thể thay đổi trạng thái của đơn hàng đã đóng!");
        }

        validateStatusTransition(currentStatus, newStatus);

        if (newStatus == OrderStatus.COMPLETED) {
            order.setPaymentStatus(PaymentStatus.PAID);
        }

        order.setStatus(newStatus);
        orderRepository.save(order);
    }

    private void validateStatusTransition(OrderStatus current, OrderStatus next) {
        if (next == OrderStatus.CANCELLED) return;
        switch (current) {
            case PENDING:
                if (next != OrderStatus.CONFIRMED) throw new BadRequestException("Đơn hàng mới cần được 'Xác nhận' trước.");
                break;
            case CONFIRMED:
                if (next != OrderStatus.PROCESSING) throw new BadRequestException("Đơn hàng đã xác nhận phải chuyển sang 'Đang xử lý'.");
                break;
            case PROCESSING:
                if (next != OrderStatus.SHIPPING) throw new BadRequestException("Đơn hàng đang xử lý phải chuyển sang 'Đang giao'.");
                break;
            case SHIPPING:
                if (next != OrderStatus.COMPLETED && next != OrderStatus.RETURNED) throw new BadRequestException("Đơn đang giao chỉ có thể chuyển sang 'Hoàn thành' hoặc 'Trả hàng'.");
                break;
        }
    }

    public List<Order> getOrdersByUserId(Long userId) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    // ══════════════════════════════════════════════════════════════
    // QUẢN LÝ ĐƠN HÀNG CHO CHI NHÁNH / KHO
    // ══════════════════════════════════════════════════════════════

    /**
     * Danh sách phần đơn được phân bổ về chi nhánh.
     * Mỗi bản ghi = 1 SubOrder thuộc chi nhánh + thông tin đơn tổng.
     */
    @Transactional(readOnly = true)
    public List<BranchOrderResponse> getBranchOrders(Long branchId, OrderStatus status, String search) {
        List<SubOrder> subOrders = (status != null)
                ? subOrderRepository.findByBranchIdAndStatusOrderByCreatedAtDesc(branchId, status)
                : subOrderRepository.findByBranchIdOrderByCreatedAtDesc(branchId);

        Stream<SubOrder> stream = subOrders.stream();
        if (search != null && !search.isBlank()) {
            String lc = search.toLowerCase();
            stream = stream.filter(s -> {
                Order o = s.getOrder();
                return (o.getCode() != null && o.getCode().toLowerCase().contains(lc))
                        || (o.getUser() != null && o.getUser().getFullName() != null
                                && o.getUser().getFullName().toLowerCase().contains(lc));
            });
        }

        return stream.map(this::mapSubOrderToBranchOrderResponse).collect(Collectors.toList());
    }

    /** Chi tiết phần đơn của chi nhánh theo orderId. */
    @Transactional(readOnly = true)
    public BranchOrderResponse getBranchOrderDetail(Long branchId, Long orderId) {
        SubOrder subOrder = subOrderRepository.findByOrderIdAndBranchId(orderId, branchId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy đơn hàng cho chi nhánh này"));
        return mapSubOrderToBranchOrderResponse(subOrder);
    }

    /**
     * Cập nhật trạng thái phần đơn của chi nhánh (SubOrder.status).
     * Sau đó tự động đồng bộ trạng thái tổng (Order.status).
     */
    @Transactional
    public void updateSubOrderStatus(Long branchId, Long orderId, OrderStatus newStatus) {
        SubOrder subOrder = subOrderRepository.findByOrderIdAndBranchId(orderId, branchId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy phần đơn cho chi nhánh này"));

        OrderStatus currentStatus = subOrder.getStatus();
        if (currentStatus == OrderStatus.CANCELLED || currentStatus == OrderStatus.COMPLETED
                || currentStatus == OrderStatus.RETURNED) {
            throw new BadRequestException("Không thể thay đổi trạng thái của đơn đã đóng!");
        }
        validateStatusTransition(currentStatus, newStatus);

        subOrder.setStatus(newStatus);
        subOrderRepository.save(subOrder);

        // Đồng bộ trạng thái tổng dựa trên tất cả SubOrders
        syncMasterOrderStatus(subOrder.getOrder());
    }

    /**
     * Đồng bộ Order.status từ trạng thái của tất cả SubOrders.
     * Quy tắc: Order lấy trạng thái chậm nhất (bottleneck) trong các SubOrder còn hoạt động.
     */
    private void syncMasterOrderStatus(Order order) {
        List<SubOrder> allSubs = subOrderRepository.findByOrderId(order.getId());
        if (allSubs.isEmpty()) return;

        List<SubOrder> activeSubs = allSubs.stream()
                .filter(s -> s.getStatus() != OrderStatus.CANCELLED && s.getStatus() != OrderStatus.RETURNED)
                .collect(Collectors.toList());

        OrderStatus newMasterStatus;
        if (activeSubs.isEmpty()) {
            newMasterStatus = OrderStatus.CANCELLED;
        } else if (activeSubs.stream().allMatch(s -> s.getStatus() == OrderStatus.COMPLETED)) {
            newMasterStatus = OrderStatus.COMPLETED;
            order.setPaymentStatus(PaymentStatus.PAID);
        } else {
            // Lấy trạng thái chậm nhất trong chuỗi xử lý
            newMasterStatus = activeSubs.stream()
                    .map(SubOrder::getStatus)
                    .min(Comparator.comparingInt(this::statusWeight))
                    .orElse(OrderStatus.PENDING);
        }

        order.setStatus(newMasterStatus);
        orderRepository.save(order);
    }

    /** Trọng số thứ tự trạng thái trong quy trình xử lý đơn. */
    private int statusWeight(OrderStatus s) {
        return switch (s) {
            case PENDING    -> 0;
            case CONFIRMED  -> 1;
            case PROCESSING -> 2;
            case SHIPPING   -> 3;
            case COMPLETED  -> 4;
            default         -> 5;
        };
    }

    private BranchOrderResponse mapSubOrderToBranchOrderResponse(SubOrder subOrder) {
        Order order = subOrder.getOrder();

        List<OrderItemResponse> items = subOrder.getItems() != null
                ? subOrder.getItems().stream().map(this::mapSubItemToResponse).collect(Collectors.toList())
                : Collections.emptyList();

        return BranchOrderResponse.builder()
                .orderId(order.getId())
                .orderCode(order.getCode())
                .customerName(order.getUser() != null ? order.getUser().getFullName() : "")
                .customerPhone(order.getUser() != null ? order.getUser().getPhoneNumber() : "")
                .shippingAddress(order.getShippingAddress() != null ? order.getShippingAddress() : order.getDeliveryAddress())
                .createdAt(order.getCreatedAt())
                .paymentMethod(order.getPaymentMethod() != null ? order.getPaymentMethod().name() : "")
                .paymentStatus(order.getPaymentStatus() != null ? order.getPaymentStatus().name() : "")
                .orderStatus(order.getStatus() != null ? order.getStatus().name() : "")
                .subOrderId(subOrder.getId())
                .subOrderStatus(subOrder.getStatus() != null ? subOrder.getStatus().name() : "")
                .subtotal(subOrder.getSubtotal() != null ? subOrder.getSubtotal() : BigDecimal.ZERO)
                .shippingFee(subOrder.getShippingFee() != null ? subOrder.getShippingFee() : BigDecimal.ZERO)
                .estimatedDays(subOrder.getEstimatedDays())
                .carrier(subOrder.getCarrier())
                .items(items)
                .build();
    }

    // ══════════════════════════════════════════════════════════════
    // MAPPING LOGIC
    // ══════════════════════════════════════════════════════════════

    private OrderResponse mapToOrderResponse(Order order) {
        List<OrderItemResponse> itemResponses = new ArrayList<>();
        
        if (order.getOrderItems() != null && !order.getOrderItems().isEmpty()) {
            itemResponses = order.getOrderItems().stream().map(this::mapItemToResponse).collect(Collectors.toList());
        } 
        else if (order.getSubOrders() != null && !order.getSubOrders().isEmpty()) {
            itemResponses = order.getSubOrders().stream()
                    .filter(sub -> sub.getItems() != null)
                    .flatMap(sub -> sub.getItems().stream())
                    .map(this::mapSubItemToResponse)
                    .collect(Collectors.toList());
        }

        String branchName;
        if (order.getBranch() != null) {
            branchName = order.getBranch().getName();
        } else if (order.getSubOrders() != null && order.getSubOrders().size() == 1) {
            Branch singleBranch = order.getSubOrders().get(0).getBranch();
            branchName = singleBranch != null ? singleBranch.getName() : "Nhiều chi nhánh";
        } else if (order.getSubOrders() != null && order.getSubOrders().size() > 1) {
            branchName = "Nhiều chi nhánh";
        } else {
            branchName = "Không xác định";
        }

        return OrderResponse.builder()
                .id(order.getId())
                .code(order.getCode())
                .customerName(order.getUser() != null ? order.getUser().getFullName() : "")
                .customerPhone(order.getUser() != null ? order.getUser().getPhoneNumber() : "")
                .finalAmount(order.getFinalAmount() != null ? order.getFinalAmount() : BigDecimal.ZERO)
                .paymentMethod(order.getPaymentMethod() != null ? order.getPaymentMethod().name() : "")
                .paymentStatus(order.getPaymentStatus() != null ? order.getPaymentStatus().name() : "")
                .status(order.getStatus() != null ? order.getStatus().name() : "")
                .branchName(branchName)
                .createdAt(order.getCreatedAt())
                .shippingAddress(order.getShippingAddress())
                .checkoutUrl(order.getPayosCheckoutUrl())
                .items(itemResponses)
                .build();
    }

    private OrderItemResponse mapItemToResponse(OrderItem item) {
        String pName = "Sản phẩm không xác định";
        String pSku = "N/A";
        String pImg = null;

        if (item.getProductVariant() != null) {
            pSku = item.getProductVariant().getSku();
            if (item.getProductVariant().getProduct() != null) {
                pName = item.getProductVariant().getProduct().getName();
                if (item.getProductVariant().getProduct().getProductImages() != null && !item.getProductVariant().getProduct().getProductImages().isEmpty()) {
                    pImg = item.getProductVariant().getProduct().getProductImages().iterator().next().getImageUrl();
                }
            }
        }

        return OrderItemResponse.builder()
                .id(item.getId())
                .productName(pName)
                .sku(pSku)
                .image(pImg)
                .quantity(item.getQuantity())
                .price(item.getPrice() != null ? item.getPrice() : BigDecimal.ZERO)
                .totalPrice(item.getPrice() != null ? item.getPrice().multiply(new BigDecimal(item.getQuantity())) : BigDecimal.ZERO)
                .build();
    }

    private OrderItemResponse mapSubItemToResponse(SubOrderItem item) {
        String pName = "Sản phẩm không xác định";
        String pSku = "N/A";
        String pImg = null;

        if (item.getProductVariant() != null) {
            pSku = item.getProductVariant().getSku();
            if (item.getProductVariant().getProduct() != null) {
                pName = item.getProductVariant().getProduct().getName();
                if (item.getProductVariant().getProduct().getProductImages() != null && !item.getProductVariant().getProduct().getProductImages().isEmpty()) {
                    pImg = item.getProductVariant().getProduct().getProductImages().iterator().next().getImageUrl();
                }
            }
        }

        return OrderItemResponse.builder()
                .id(item.getId())
                .productName(pName)
                .sku(pSku)
                .image(pImg)
                .quantity(item.getQuantity())
                .price(item.getUnitPrice() != null ? item.getUnitPrice() : BigDecimal.ZERO)
                .totalPrice(item.getUnitPrice() != null ? item.getUnitPrice().multiply(new BigDecimal(item.getQuantity())) : BigDecimal.ZERO)
                .build();
    }

    // ══════════════════════════════════════════════════════════════
    // PREPARE & CONFIRM LOGIC
    // ══════════════════════════════════════════════════════════════

    public PrepareOrderResponse prepareOrder(Long userId, PrepareOrderRequest request) {
        List<Long> variantIds = request.getCart().stream().map(CartItemDto::getProductVariantId).distinct().toList();
        List<ProductVariant> variants = variantRepository.findAllById(variantIds);
        if (variants.size() != variantIds.size()) throw new NotFoundException("Một hoặc nhiều sản phẩm không tồn tại");

        Map<Long, ProductVariant> variantMap = variants.stream().collect(Collectors.toMap(ProductVariant::getId, Function.identity()));
        double userLat = request.getUserLat() != null ? request.getUserLat() : 10.0341;
        double userLng = request.getUserLng() != null ? request.getUserLng() : 105.7904;

        List<BranchWithRealDistance> nearestBranches = branchSearchService.findNearestBranches(userLat, userLng);
        if (nearestBranches.isEmpty()) throw new NotFoundException("Không có chi nhánh hoạt động");

        List<Long> branchIds = nearestBranches.stream().map(bwr -> bwr.branch().getId()).toList();
        Map<Long, Map<Long, Integer>> inventoryMatrix = allocationService.buildInventoryMatrix(branchIds, variantIds);
        AllocationResult allocation = allocationService.allocate(request.getCart(), variantMap, nearestBranches, inventoryMatrix);

        DeliveryInfo deliveryInfo = DeliveryInfo.builder()
                .toDistrictId(request.getDeliveryDistrictId()).toWardCode(request.getDeliveryWardCode())
                .deliveryAddress(request.getDeliveryAddress()).userLat(userLat).userLng(userLng).build();

        List<SubOrderDraftDto> enrichedSubOrders = shippingService.enrichWithShippingFees(allocation.subOrders(), deliveryInfo, variantMap);

        BigDecimal totalSubtotal = enrichedSubOrders.stream().map(s -> s.getSubtotal() != null ? s.getSubtotal() : BigDecimal.ZERO).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalShippingFee = enrichedSubOrders.stream().map(s -> s.getShippingFee() != null ? s.getShippingFee() : BigDecimal.ZERO).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalAmount = totalSubtotal.add(totalShippingFee);

        String token = UUID.randomUUID().toString();
        List<OrderItemDto> allFinalItems = enrichedSubOrders.stream().flatMap(s -> s.getItems().stream()).collect(Collectors.toList());
        Long mainBranchId = enrichedSubOrders.size() == 1 ? enrichedSubOrders.get(0).getBranchId() : null;

        PrepareOrderDraft draft = PrepareOrderDraft.builder()
                .prepareToken(token).userId(userId).branchId(mainBranchId).finalItems(allFinalItems)
                .userLat(userLat).userLng(userLng).deliveryAddress(request.getDeliveryAddress())
                .deliveryDistrictId(request.getDeliveryDistrictId()).deliveryProvinceId(request.getDeliveryProvinceId())
                .deliveryWardCode(request.getDeliveryWardCode()).subOrders(enrichedSubOrders)
                .outOfStockItems(allocation.outOfStockItems()).totalSubtotal(totalSubtotal)
                .totalShippingFee(totalShippingFee).totalAmount(totalAmount).build();

        saveDraftToRedis(token, draft);

        return PrepareOrderResponse.builder().prepareToken(token).canFulfill(allocation.outOfStockItems().isEmpty())
                .subOrders(enrichedSubOrders).totalSubtotal(totalSubtotal).totalShippingFee(totalShippingFee)
                .totalAmount(totalAmount).outOfStockItems(allocation.outOfStockItems()).build();
    }

    @Transactional
    public ConfirmOrderResponse confirmOrder(Long userId, ConfirmOrderRequest request) {
        PrepareOrderDraft draft = getDraftFromRedis(request.getPrepareToken());
        if (draft == null) throw new BadRequestException("Token hết hạn");
        if (!userId.equals(draft.getUserId())) throw new BadRequestException("Token không hợp lệ");

        for (SubOrderDraftDto subDraft : draft.getSubOrders()) {
            for (OrderItemDto item : subDraft.getItems()) {
                Inventory inv = inventoryRepository.findForUpdate(subDraft.getBranchId(), item.getProductVariantId())
                        .orElseThrow(() -> new ConflictException("Hết hàng"));
                if (inv.getQuantity() < item.getQuantity()) throw new ConflictException("Không đủ hàng");
            }
        }

        User user = userRepository.findById(userId).orElseThrow(() -> new NotFoundException("User không tồn tại"));
        PaymentMethod paymentMethod = request.getPaymentMethod() != null ? request.getPaymentMethod() : PaymentMethod.COD;

        Order order = Order.builder().code("ORD" + System.currentTimeMillis()).user(user).status(OrderStatus.PENDING)
                .paymentMethod(paymentMethod).paymentStatus(PaymentStatus.UNPAID).createdAt(LocalDateTime.now())
                .totalAmount(draft.getTotalSubtotal()).discountAmount(BigDecimal.ZERO).finalAmount(draft.getTotalAmount())
                .totalShippingFee(draft.getTotalShippingFee()).userLat(draft.getUserLat()).userLng(draft.getUserLng())
                .deliveryAddress(draft.getDeliveryAddress()).shippingAddress(draft.getDeliveryAddress()).note(request.getNote()).build();
        Order savedOrder = orderRepository.save(order);

        List<SubOrderSummaryDto> subOrderSummaries = new ArrayList<>();
        for (SubOrderDraftDto subDraft : draft.getSubOrders()) {
            Branch branch = branchRepository.findById(subDraft.getBranchId()).orElseThrow(() -> new NotFoundException("Branch không tồn tại"));
            SubOrder subOrder = SubOrder.builder().order(savedOrder).branch(branch).status(OrderStatus.PENDING)
                    .subtotal(subDraft.getSubtotal()).shippingFee(subDraft.getShippingFee())
                    .estimatedDays(subDraft.getEstimatedDays()).carrier(subDraft.getCarrier()).build();
            SubOrder savedSubOrder = subOrderRepository.save(subOrder);

            for (OrderItemDto item : subDraft.getItems()) {
                ProductVariant variant = variantRepository.findById(item.getProductVariantId()).orElseThrow(() -> new NotFoundException("Sản phẩm không tồn tại"));
                subOrderItemRepository.save(SubOrderItem.builder().subOrder(savedSubOrder).productVariant(variant).quantity(item.getQuantity()).unitPrice(item.getUnitPrice()).build());
                Inventory inventory = inventoryRepository.findForUpdate(subDraft.getBranchId(), item.getProductVariantId()).get();
                inventory.setQuantity(inventory.getQuantity() - item.getQuantity());
                inventoryRepository.save(inventory);
            }
            subOrderSummaries.add(SubOrderSummaryDto.builder().subOrderId(savedSubOrder.getId()).branchId(branch.getId()).branchName(branch.getName()).status(OrderStatus.PENDING.name()).subtotal(subDraft.getSubtotal()).shippingFee(subDraft.getShippingFee()).estimatedDays(subDraft.getEstimatedDays()).carrier(subDraft.getCarrier()).build());
        }

        String checkoutUrl = null;
        if (PaymentMethod.PAYOS.equals(paymentMethod)) {
            try {
                com.zone.agri.dto.payment.PayOSApiResponse.PayOSLinkData payosData = payOSService.createPaymentLink(savedOrder);
                savedOrder.setPayosPaymentLinkId(payosData.getPaymentLinkId());
                savedOrder.setPayosCheckoutUrl(payosData.getCheckoutUrl());
                orderRepository.save(savedOrder);
                checkoutUrl = payosData.getCheckoutUrl();
            } catch (Exception e) {
                throw new BadRequestException("Lỗi tạo PayOS link: " + e.getMessage());
            }
        }

        redisTemplate.delete(PREPARE_KEY_PREFIX + request.getPrepareToken());
        draft.getSubOrders().stream().flatMap(s -> s.getItems().stream()).map(OrderItemDto::getProductVariantId).distinct()
                .forEach(vId -> cartItemRepository.findByUserIdAndProductVariantId(userId, vId).ifPresent(cartItemRepository::delete));

        return ConfirmOrderResponse.builder().orderId(savedOrder.getId()).orderCode(savedOrder.getCode()).status(OrderStatus.PENDING.name()).subOrders(subOrderSummaries).totalAmount(draft.getTotalAmount()).totalShippingFee(draft.getTotalShippingFee()).checkoutUrl(checkoutUrl).build();
    }

    // ══════════════════════════════════════════════════════════════
    // HELPERS & REDIS
    // ══════════════════════════════════════════════════════════════

    private void saveDraftToRedis(String token, PrepareOrderDraft draft) {
        try {
            redisTemplate.opsForValue().set(PREPARE_KEY_PREFIX + token, objectMapper.writeValueAsString(draft), PREPARE_TTL_MINUTES, TimeUnit.MINUTES);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Lỗi lưu Redis");
        }
    }

    private PrepareOrderDraft getDraftFromRedis(String token) {
        String json = redisTemplate.opsForValue().get(PREPARE_KEY_PREFIX + token);
        if (json == null) return null;
        try {
            return objectMapper.readValue(json, PrepareOrderDraft.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    @Transactional
    public Order placeOrder(Long userId, CheckoutRequest req) {
        User user = userRepository.findById(userId).orElseThrow(() -> new NotFoundException("User không tìm thấy"));
        Branch selectedBranch = findBranchWithEnoughStock(req.getItems());
        if (selectedBranch == null) throw new BadRequestException("Không chi nhánh nào đủ hàng");

        Order order = Order.builder().code("ORD" + System.currentTimeMillis()).user(user).branch(selectedBranch)
                .shippingAddress(req.getShippingAddress()).status(OrderStatus.PENDING).paymentMethod(PaymentMethod.COD)
                .paymentStatus(PaymentStatus.UNPAID).createdAt(LocalDateTime.now()).totalAmount(BigDecimal.ZERO).discountAmount(BigDecimal.ZERO).finalAmount(BigDecimal.ZERO).build();
        Order savedOrder = orderRepository.save(order);

        BigDecimal totalAmount = BigDecimal.ZERO;
        for (CheckoutItemRequest itemReq : req.getItems()) {
            ProductVariant variant = variantRepository.findById(itemReq.getVariantId()).orElseThrow(() -> new NotFoundException("Sản phẩm không tồn tại"));
            Inventory inventory = inventoryRepository.findByBranchIdAndProductVariantId(selectedBranch.getId(), variant.getId()).get();
            inventory.setQuantity(inventory.getQuantity() - itemReq.getQuantity());
            inventoryRepository.save(inventory);
            orderItemRepository.save(OrderItem.builder().order(savedOrder).productVariant(variant).quantity(itemReq.getQuantity()).price(variant.getPrice()).build());
            totalAmount = totalAmount.add(variant.getPrice().multiply(new BigDecimal(itemReq.getQuantity())));
        }
        savedOrder.setTotalAmount(totalAmount);
        savedOrder.setFinalAmount(totalAmount.add(new BigDecimal("15000")));
        orderRepository.save(savedOrder);
        return savedOrder;
    }

    private Branch findBranchWithEnoughStock(List<CheckoutItemRequest> itemsToBuy) {
        return branchRepository.findAll().stream().filter(branch -> itemsToBuy.stream().allMatch(item -> inventoryRepository.findByBranchIdAndProductVariantId(branch.getId(), item.getVariantId()).map(inv -> inv.getQuantity() >= item.getQuantity()).orElse(false))).findFirst().orElse(null);
    }
}
