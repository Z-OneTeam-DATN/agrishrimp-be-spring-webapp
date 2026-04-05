package com.zone.agri.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zone.agri.dto.response.geo.CoordinateDto;
import com.zone.agri.dto.response.geo.DeliveryInfo;
import com.zone.agri.dto.request.order.*;
import com.zone.agri.dto.response.order.*;
import com.zone.agri.entity.*;
import com.zone.agri.entity.enums.OrderStatus;
import com.zone.agri.entity.enums.PaymentMethod;
import com.zone.agri.entity.enums.PaymentStatus;
import com.zone.agri.entity.enums.TransactionType;
import com.zone.agri.entity.enums.VoucherStatus;
import com.zone.agri.entity.enums.VoucherDiscountType; // Thêm Enum này để kiểm tra loại Voucher
import com.zone.agri.exception.BadRequestException;
import com.zone.agri.exception.ConflictException;
import com.zone.agri.exception.NotFoundException;
import com.zone.agri.repository.*;
import com.zone.agri.service.BranchSearchService.BranchWithRealDistance;
import com.zone.agri.service.InventoryAllocationService.AllocationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.annotation.Lazy;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    private final InventoryTransactionRepository transactionRepository;

    private final UserAddressRepository userAddressRepository;
    private final ReviewRepository reviewRepository;

    private final VoucherRepository voucherRepository;
    private final UserVoucherRepository userVoucherRepository;

    private final BranchSearchService branchSearchService;
    private final InventoryAllocationService allocationService;
    private final ShippingService shippingService;
    private final PayOSService payOSService;
    private final SettingService settingService;
    private final GeocodingService geocodingService;

    @Lazy
    private final CustomerService customerService;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String PREPARE_KEY_PREFIX = "prepare:";
    private static final String PREPARE_CONFIRM_LOCK_PREFIX = "prepare:confirm:lock:";
    private static final String PREPARE_CONFIRM_RESULT_PREFIX = "prepare:confirm:result:";
    private static final long PREPARE_TTL_MINUTES = 30;
    private static final long PREPARE_CONFIRM_LOCK_TTL_SECONDS = 120;

    private record PreparedQuote(
            List<CartItemDto> cartItems,
            List<SubOrderDraftDto> subOrders,
            List<OutOfStockItemDto> outOfStockItems,
            BigDecimal totalSubtotal,
            BigDecimal discountAmount,
            BigDecimal totalShippingFee,
            BigDecimal totalAmount
    ) {}

    private record VoucherValidation(
            Voucher voucher,
            UserVoucher userVoucher,
            BigDecimal discountAmount
    ) {}

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
        syncPayOSPaymentStatuses(orders);
        return orders.stream().map(this::mapToOrderResponse).collect(Collectors.toList());
    }

    public OrderResponse getMyOrderDetail(Long userId, Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy đơn hàng ID: " + orderId));

        if (!order.getUser().getId().equals(userId)) {
            throw new BadRequestException("Bạn không có quyền xem đơn hàng này!");
        }

        if (PaymentMethod.PAYOS.equals(order.getPaymentMethod()) && PaymentStatus.UNPAID.equals(order.getPaymentStatus())) {
            if (payOSService.checkPaymentStatus(order)) {
                payOSService.markOrderPaid(order);
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

        syncPayOSPaymentStatuses(orders);
        return orders.stream().map(this::mapToOrderResponse).collect(Collectors.toList());
    }

    public OrderResponse getAdminOrderDetail(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy đơn hàng ID: " + orderId));


        if (PaymentMethod.PAYOS.equals(order.getPaymentMethod()) && PaymentStatus.UNPAID.equals(order.getPaymentStatus())) {
            if (payOSService.checkPaymentStatus(order)) {
                payOSService.markOrderPaid(order);
            }
        }


        return mapToOrderResponse(order);
    }

    @Transactional
    public void cancelMyOrder(Long userId, Long orderId, String cancelReason) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Khong tim thay don hang ID: " + orderId));

        if (!order.getUser().getId().equals(userId)) {
            throw new BadRequestException("Ban khong co quyen huy don hang nay");
        }
        if (order.getStatus() == OrderStatus.CANCELLED || order.getStatus() == OrderStatus.COMPLETED || order.getStatus() == OrderStatus.RETURNED) {
            throw new BadRequestException("Don hang da dong, khong the huy");
        }
        if (order.getStatus() == OrderStatus.SHIPPING || order.getStatus() == OrderStatus.READY_FOR_PICKUP) {
            throw new BadRequestException("Don hang dang giao hoac cho lay hang, khong the huy");
        }

        releaseAllocatedInventoryForOrder(order);
        order.setStatus(OrderStatus.CANCELLED);
        if (order.getSubOrders() != null) {
            order.getSubOrders().forEach(subOrder -> subOrder.setStatus(OrderStatus.CANCELLED));
        }
        if (cancelReason != null && !cancelReason.isBlank()) {
            String previousNote = order.getNote() != null ? order.getNote() + " | " : "";
            order.setNote(previousNote + "Cancel reason: " + cancelReason.trim());
        }
        orderRepository.save(order);
        customerService.evaluateAndHandleCustomerReputation(order.getUser().getId());
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
        if (newStatus == OrderStatus.CANCELLED) {
            releaseAllocatedInventoryForOrder(order);
            if (order.getSubOrders() != null) {
                order.getSubOrders().stream()
                        .filter(subOrder -> subOrder.getStatus() != OrderStatus.CANCELLED)
                        .forEach(subOrder -> subOrder.setStatus(OrderStatus.CANCELLED));
            }
        }

        order.setStatus(newStatus);
        orderRepository.save(order);

        if (newStatus == OrderStatus.COMPLETED || newStatus == OrderStatus.CANCELLED || newStatus == OrderStatus.RETURNED) {
            customerService.evaluateAndHandleCustomerReputation(order.getUser().getId());
        }
    }

    private void validateStatusTransition(OrderStatus current, OrderStatus next) {
        if (next == OrderStatus.CANCELLED) return;
        switch (current) {
            case PENDING:
                if (next != OrderStatus.CONFIRMED && next != OrderStatus.AWAITING_PAYMENT)
                    throw new BadRequestException("Đơn hàng mới cần được 'Xác nhận' hoặc 'Chờ thanh toán'.");
                break;
            case AWAITING_PAYMENT:
                if (next != OrderStatus.CONFIRMED)
                    throw new BadRequestException("Đơn hàng chờ thanh toán cần được 'Xác nhận' sau khi thanh toán xong.");
                break;
            case AWAITING_REPLENISHMENT:
                if (next != OrderStatus.CONFIRMED)
                    throw new BadRequestException("ÄÆ¡n hĂ ng thiáº¿u hĂ ng cáº§n Ä‘Æ°á»£c xĂ¡c nháº­n láº¡i sau khi Ä‘Ă£ bá»• sung.");
                break;
            case CONFIRMED:
                if (next != OrderStatus.PROCESSING)
                    throw new BadRequestException("Đơn hàng đã xác nhận phải chuyển sang 'Đang đóng gói'.");
                break;
            case PROCESSING:
                if (next != OrderStatus.READY_FOR_PICKUP && next != OrderStatus.SHIPPING)
                    throw new BadRequestException("Đơn hàng đang đóng gói phải chuyển sang 'Chờ lấy hàng' hoặc 'Đang giao'.");
                break;
            case READY_FOR_PICKUP:
                if (next != OrderStatus.SHIPPING)
                    throw new BadRequestException("Đơn hàng chờ lấy hàng phải chuyển sang 'Đang giao'.");
                break;
            case SHIPPING:
                if (next != OrderStatus.COMPLETED && next != OrderStatus.RETURNED)
                    throw new BadRequestException("Đơn đang giao chỉ có thể chuyển sang 'Hoàn thành' hoặc 'Trả hàng'.");
                break;
        }
    }

    public List<Order> getOrdersByUserId(Long userId) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    // ══════════════════════════════════════════════════════════════
    // QUẢN LÝ ĐƠN HÀNG CHO CHI NHÁNH / KHO
    // ══════════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public List<BranchOrderResponse> getBranchOrders(Long branchId, OrderStatus status, String search) {
        List<SubOrder> subOrders = (status != null)
                ? subOrderRepository.findByBranchIdAndStatusOrderByCreatedAtDesc(branchId, status)
                : subOrderRepository.findByBranchIdOrderByCreatedAtDesc(branchId);

        List<SubOrder> filteredSubOrders = subOrders;
        if (search != null && !search.isBlank()) {
            String lc = search.toLowerCase();
            filteredSubOrders = subOrders.stream().filter(s -> {
                Order o = s.getOrder();
                return (o.getCode() != null && o.getCode().toLowerCase().contains(lc))
                        || (o.getUser() != null && o.getUser().getFullName() != null
                        && o.getUser().getFullName().toLowerCase().contains(lc));
            }).collect(Collectors.toList());
        }

        syncPayOSPaymentStatuses(
                filteredSubOrders.stream()
                        .map(SubOrder::getOrder)
                        .filter(Objects::nonNull)
                        .distinct()
                        .collect(Collectors.toList())
        );

        return filteredSubOrders.stream().map(this::mapSubOrderToBranchOrderResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public BranchOrderResponse getBranchOrderDetail(Long branchId, Long orderId) {
        SubOrder subOrder = subOrderRepository.findByOrderIdAndBranchId(orderId, branchId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy đơn hàng cho chi nhánh này"));
        syncPayOSPaymentStatus(subOrder.getOrder());
        return mapSubOrderToBranchOrderResponse(subOrder);
    }

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

        if (newStatus == OrderStatus.CANCELLED) {
            releaseAllocatedInventoryForSubOrder(subOrder);
        }

        subOrder.setStatus(newStatus);
        subOrderRepository.saveAndFlush(subOrder);

        syncMasterOrderStatus(subOrder.getOrder().getId());
    }

    private void syncMasterOrderStatus(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy đơn hàng tổng"));

        List<SubOrder> allSubs = subOrderRepository.findByOrderId(orderId);
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
            newMasterStatus = activeSubs.stream()
                    .map(SubOrder::getStatus)
                    .min(Comparator.comparingInt(this::statusWeight))
                    .orElse(OrderStatus.PENDING);
        }

        order.setStatus(newMasterStatus);
        orderRepository.saveAndFlush(order);

        if (newMasterStatus == OrderStatus.COMPLETED || newMasterStatus == OrderStatus.CANCELLED || newMasterStatus == OrderStatus.RETURNED) {
            customerService.evaluateAndHandleCustomerReputation(order.getUser().getId());
        }
    }

    private int statusWeight(OrderStatus s) {
        return switch (s) {
            case PENDING          -> 0;
            case AWAITING_PAYMENT -> 1;
            case AWAITING_REPLENISHMENT -> 2;
            case CONFIRMED        -> 3;
            case PROCESSING       -> 4;
            case READY_FOR_PICKUP -> 5;
            case SHIPPING         -> 6;
            case COMPLETED        -> 7;
            default               -> 8;
        };
    }

    private void syncPayOSPaymentStatuses(Collection<Order> orders) {
        if (orders == null || orders.isEmpty()) {
            return;
        }

        orders.forEach(this::syncPayOSPaymentStatus);
    }

    private void syncPayOSPaymentStatus(Order order) {
        if (order == null
                || !PaymentMethod.PAYOS.equals(order.getPaymentMethod())
                || !PaymentStatus.UNPAID.equals(order.getPaymentStatus())) {
            return;
        }

        if (payOSService.checkPaymentStatus(order)) {
            order.setPaymentStatus(PaymentStatus.PAID);
            orderRepository.save(order);
        }
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
        List<SubOrderSummaryDto> subOrderSummaries = order.getSubOrders() != null
                ? order.getSubOrders().stream()
                .map(this::mapSubOrderToSummary)
                .collect(Collectors.toList())
                : Collections.emptyList();

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
        String branchPhone = null;
        String branchAddress = null;

        if (order.getBranch() != null) {
            branchName   = order.getBranch().getName();
            branchPhone  = order.getBranch().getPhone();
            branchAddress = order.getBranch().getAddressDetail();
        } else if (order.getSubOrders() != null && order.getSubOrders().size() == 1) {
            Branch singleBranch = order.getSubOrders().get(0).getBranch();
            branchName    = singleBranch != null ? singleBranch.getName()          : "Nhiều chi nhánh";
            branchPhone   = singleBranch != null ? singleBranch.getPhone()         : null;
            branchAddress = singleBranch != null ? singleBranch.getAddressDetail() : null;
        } else if (order.getSubOrders() != null && order.getSubOrders().size() > 1) {
            branchName = "Nhiều chi nhánh";
        } else {
            branchName = "Không xác định";
        }

        return OrderResponse.builder()
                .id(order.getId())
                .code(order.getCode())
                .orderCode(order.getCode())
                .customerName(order.getUser() != null ? order.getUser().getFullName() : "")
                .customerPhone(order.getUser() != null ? order.getUser().getPhoneNumber() : "")
                .receiverName(order.getReceiverName())
                .receiverPhone(order.getReceiverPhone())
                .totalAmount(order.getTotalAmount() != null ? order.getTotalAmount() : BigDecimal.ZERO)
                .shippingFee(order.getTotalShippingFee() != null ? order.getTotalShippingFee() : BigDecimal.ZERO)
                .totalShippingFee(order.getTotalShippingFee() != null ? order.getTotalShippingFee() : BigDecimal.ZERO)
                .finalAmount(order.getFinalAmount() != null ? order.getFinalAmount() : BigDecimal.ZERO)
                .paymentMethod(order.getPaymentMethod() != null ? order.getPaymentMethod().name() : "")
                .paymentStatus(order.getPaymentStatus() != null ? order.getPaymentStatus().name() : "")
                .status(order.getStatus() != null ? order.getStatus().name() : "")
                .branchName(branchName)
                .branchPhone(branchPhone)
                .branchAddress(branchAddress)
                .createdAt(order.getCreatedAt())
                .shippingAddress(order.getShippingAddress())
                .note(order.getNote())
                .checkoutUrl(order.getPayosCheckoutUrl())
                .items(itemResponses)
                .subOrders(subOrderSummaries)
                .build();
    }

    private SubOrderSummaryDto mapSubOrderToSummary(SubOrder subOrder) {
        return SubOrderSummaryDto.builder()
                .subOrderId(subOrder.getId())
                .branchId(subOrder.getBranch() != null ? subOrder.getBranch().getId() : null)
                .branchName(subOrder.getBranch() != null ? subOrder.getBranch().getName() : null)
                .status(subOrder.getStatus() != null ? subOrder.getStatus().name() : null)
                .subtotal(subOrder.getSubtotal() != null ? subOrder.getSubtotal() : BigDecimal.ZERO)
                .shippingFee(subOrder.getShippingFee() != null ? subOrder.getShippingFee() : BigDecimal.ZERO)
                .estimatedDays(subOrder.getEstimatedDays())
                .carrier(subOrder.getCarrier())
                .carrierOrderId(subOrder.getCarrierOrderId())
                .build();
    }

    private OrderItemResponse mapItemToResponse(OrderItem item) {
        String pName = "Sản phẩm không xác định";
        String pSku = "N/A";
        String pImg = null;
        Long productId = null;

        if (item.getProductVariant() != null) {
            pSku = item.getProductVariant().getSku();
            if (item.getProductVariant().getProduct() != null) {
                productId = item.getProductVariant().getProduct().getId();
                pName = item.getProductVariant().getProduct().getName();
                if (item.getProductVariant().getProduct().getProductImages() != null && !item.getProductVariant().getProduct().getProductImages().isEmpty()) {
                    pImg = item.getProductVariant().getProduct().getProductImages().iterator().next().getImageUrl();
                }
            }
        }

        boolean canReview = false;
        if (item.getOrder() != null && item.getOrder().getStatus() == OrderStatus.COMPLETED && productId != null) {
            Long userId = item.getOrder().getUser().getId();
            canReview = !reviewRepository.existsByOrderIdAndProductIdAndUserId(item.getOrder().getId(), productId, userId);
        }

        return OrderItemResponse.builder()
                .id(item.getId())
                .productId(productId)
                .productName(pName)
                .sku(pSku)
                .image(pImg)
                .quantity(item.getQuantity())
                .allocatedQuantity(item.getQuantity())
                .missingQuantity(0)
                .price(item.getPrice() != null ? item.getPrice() : BigDecimal.ZERO)
                .totalPrice(item.getPrice() != null ? item.getPrice().multiply(new BigDecimal(item.getQuantity())) : BigDecimal.ZERO)
                .canReview(canReview)
                .build();
    }

    private OrderItemResponse mapSubItemToResponse(SubOrderItem item) {
        String pName = "Sản phẩm không xác định";
        String pSku = "N/A";
        String pImg = null;
        Long productId = null;

        if (item.getProductVariant() != null) {
            pSku = item.getProductVariant().getSku();
            if (item.getProductVariant().getProduct() != null) {
                productId = item.getProductVariant().getProduct().getId();
                pName = item.getProductVariant().getProduct().getName();
                if (item.getProductVariant().getProduct().getProductImages() != null && !item.getProductVariant().getProduct().getProductImages().isEmpty()) {
                    pImg = item.getProductVariant().getProduct().getProductImages().iterator().next().getImageUrl();
                }
            }
        }

        boolean canReview = false;
        if (item.getSubOrder() != null && item.getSubOrder().getStatus() == OrderStatus.COMPLETED && productId != null) {
            Long userId = item.getSubOrder().getOrder().getUser().getId();
            canReview = !reviewRepository.existsByOrderIdAndProductIdAndUserId(item.getSubOrder().getOrder().getId(), productId, userId);
        }

        return OrderItemResponse.builder()
                .id(item.getId())
                .productId(productId)
                .productName(pName)
                .sku(pSku)
                .image(pImg)
                .quantity(item.getQuantity())
                .allocatedQuantity(item.getAllocatedQuantity() != null ? item.getAllocatedQuantity() : item.getQuantity())
                .missingQuantity(item.getMissingQuantity() != null ? item.getMissingQuantity() : 0)
                .price(item.getUnitPrice() != null ? item.getUnitPrice() : BigDecimal.ZERO)
                .totalPrice(item.getUnitPrice() != null ? item.getUnitPrice().multiply(new BigDecimal(item.getQuantity())) : BigDecimal.ZERO)
                .canReview(canReview)
                .build();
    }

    // ══════════════════════════════════════════════════════════════
    // PREPARE & CONFIRM LOGIC (THUẬT TOÁN FIFO + LÔ HÀNG)
    // ══════════════════════════════════════════════════════════════

    public PrepareOrderResponse prepareOrder(Long userId, PrepareOrderRequest request) {
        com.zone.agri.entity.UserAddress addr = userAddressRepository
                .findByIdAndUserId(request.getUserAddressId(), userId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy địa chỉ trong sổ địa chỉ của bạn. ID: " + request.getUserAddressId()));

        String receiverName = addr.getReceiverName();
        String receiverPhone = addr.getReceiverPhone();
        String deliveryAddress = addr.getAddressDetail();
        Integer deliveryDistrictId = addr.getDistrictId() != null ? parseIntSafe(addr.getDistrictId()) : null;
        Integer deliveryProvinceId = addr.getProvinceId() != null ? parseIntSafe(addr.getProvinceId()) : null;
        String deliveryWardCode = addr.getWardId();

        double userLat;
        double userLng;
        if (request.getUserLat() != null && request.getUserLng() != null) {
            userLat = request.getUserLat();
            userLng = request.getUserLng();
        } else {
            try {
                CoordinateDto coord = geocodingService.geocode(deliveryAddress);
                userLat = coord.getLat();
                userLng = coord.getLng();
            } catch (Exception e) {
                log.warn("Geocode thất bại cho địa chỉ '{}', dùng tọa độ mặc định: {}", deliveryAddress, e.getMessage());
                userLat = 10.0341;
                userLng = 105.7904;
            }
        }

        List<CartItemDto> finalCart = request.getCart();
        if (finalCart == null || finalCart.isEmpty()) {
            List<com.zone.agri.entity.CartItem> dbItems = cartItemRepository.findByUserId(userId);
            if (dbItems.isEmpty()) throw new BadRequestException("Giỏ hàng của bạn đang trống");

            finalCart = dbItems.stream()
                    .map(item -> new CartItemDto(item.getProductVariant().getId(),
                            item.getQuantity()))
                    .collect(Collectors.toList());
        }

        finalCart = normalizeCartItems(finalCart);
        String voucherCode = normalizeVoucherCode(request.getVoucherCode());

        List<Long> variantIds = finalCart.stream().map(CartItemDto::getProductVariantId).distinct().toList();
        List<ProductVariant> variants = variantRepository.findAllById(variantIds);
        if (variants.size() != variantIds.size()) throw new NotFoundException("Một hoặc nhiều sản phẩm không tồn tại");

        Map<Long, ProductVariant> variantMap = variants.stream().collect(Collectors.toMap(ProductVariant::getId, Function.identity()));

        List<BranchWithRealDistance> nearestBranches = branchSearchService.findNearestBranches(userLat, userLng);
        if (nearestBranches.isEmpty()) throw new NotFoundException("Không có chi nhánh hoạt động");

        List<Long> branchIds = nearestBranches.stream().map(bwr -> bwr.branch().getId()).toList();

        Map<Long, Map<Long, List<Inventory>>> inventoryMatrix = allocationService.buildInventoryMatrix(branchIds, variantIds);
        AllocationResult allocation = allocationService.allocate(finalCart, variantMap, nearestBranches, inventoryMatrix);

        DeliveryInfo deliveryInfo = DeliveryInfo.builder()
                .toDistrictId(deliveryDistrictId).toWardCode(deliveryWardCode)
                .deliveryAddress(deliveryAddress).userLat(userLat).userLng(userLng).build();

        List<SubOrderDraftDto> enrichedSubOrders = shippingService.enrichWithShippingFees(allocation.subOrders(), deliveryInfo, variantMap);

        BigDecimal totalSubtotal = enrichedSubOrders.stream().map(s -> s.getSubtotal() != null ? s.getSubtotal() : BigDecimal.ZERO).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalShippingFee = enrichedSubOrders.stream().map(s -> s.getShippingFee() != null ? s.getShippingFee() : BigDecimal.ZERO).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal discountAmount = BigDecimal.ZERO;
        if (voucherCode != null) {
            User previewUser = userRepository.findById(userId)
                    .orElseThrow(() -> new NotFoundException("User khong ton tai"));
            discountAmount = validateVoucher(previewUser, voucherCode, totalSubtotal, false, false).discountAmount();
        }

        BigDecimal totalAmount = totalSubtotal.add(totalShippingFee).subtract(discountAmount);
        if (totalAmount.compareTo(BigDecimal.ZERO) < 0) {
            totalAmount = BigDecimal.ZERO;
        }

        String token = UUID.randomUUID().toString();
        List<OrderItemDto> allFinalItems = enrichedSubOrders.stream().flatMap(s -> s.getItems().stream()).collect(Collectors.toList());
        Long mainBranchId = enrichedSubOrders.size() == 1 ? enrichedSubOrders.get(0).getBranchId() : null;

        PrepareOrderDraft draft = PrepareOrderDraft.builder()
                .prepareToken(token).userId(userId).voucherCode(voucherCode).branchId(mainBranchId).finalItems(allFinalItems).cartItems(finalCart)
                .receiverName(receiverName).receiverPhone(receiverPhone)
                .userLat(userLat).userLng(userLng).deliveryAddress(deliveryAddress)
                .deliveryDistrictId(deliveryDistrictId).deliveryProvinceId(deliveryProvinceId)
                .deliveryWardCode(deliveryWardCode).subOrders(enrichedSubOrders)
                .outOfStockItems(allocation.outOfStockItems()).totalSubtotal(totalSubtotal).discountAmount(discountAmount)
                .totalShippingFee(totalShippingFee).totalAmount(totalAmount).build();

        saveDraftToRedis(token, draft);

        return PrepareOrderResponse.builder().prepareToken(token).canFulfill(allocation.outOfStockItems().isEmpty()).voucherCode(voucherCode)
                .subOrders(enrichedSubOrders).totalSubtotal(totalSubtotal).discountAmount(discountAmount).totalShippingFee(totalShippingFee)
                .totalAmount(totalAmount).outOfStockItems(allocation.outOfStockItems()).build();
    }

    private Integer parseIntSafe(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Transactional
    public ConfirmOrderResponse confirmOrder(Long userId, ConfirmOrderRequest request) {
        ConfirmOrderResponse cachedResult = getConfirmResultFromRedis(request.getPrepareToken());
        if (cachedResult != null) {
            return cachedResult;
        }

        String confirmLockKey = PREPARE_CONFIRM_LOCK_PREFIX + request.getPrepareToken();
        Boolean lockAcquired = redisTemplate.opsForValue().setIfAbsent(
                confirmLockKey,
                String.valueOf(userId),
                PREPARE_CONFIRM_LOCK_TTL_SECONDS,
                TimeUnit.SECONDS
        );
        if (Boolean.FALSE.equals(lockAcquired)) {
            ConfirmOrderResponse lockedResult = getConfirmResultFromRedis(request.getPrepareToken());
            if (lockedResult != null) {
                return lockedResult;
            }
            throw new ConflictException("Don hang dang duoc xu ly, vui long doi trong giay lat");
        }

        try {
        PrepareOrderDraft draft = getDraftFromRedis(request.getPrepareToken());
        if (draft == null) throw new BadRequestException("Token hết hạn");
        if (!userId.equals(draft.getUserId())) throw new BadRequestException("Token không hợp lệ");

        User user = userRepository.findById(userId).orElseThrow(() -> new NotFoundException("User không tồn tại"));
        List<CartItemDto> cartSnapshot = normalizeCartItems(draft.getCartItems());
        PreparedQuote liveQuote = buildPreparedQuote(
                userId,
                cartSnapshot,
                draft.getUserLat(),
                draft.getUserLng(),
                draft.getDeliveryAddress(),
                draft.getDeliveryDistrictId(),
                draft.getDeliveryWardCode(),
                draft.getVoucherCode()
        );
        ensurePreparedQuoteStillValid(draft, liveQuote);
        VoucherValidation committedVoucher = validateVoucher(
                user,
                draft.getVoucherCode(),
                liveQuote.totalSubtotal(),
                true,
                true
        );
        PaymentMethod paymentMethod = request.getPaymentMethod() != null ? request.getPaymentMethod() : PaymentMethod.COD;
        boolean hasMissingItems = liveQuote.subOrders().stream()
                .flatMap(subOrder -> subOrder.getItems().stream())
                .anyMatch(item -> Objects.requireNonNullElse(item.getMissingQuantity(), 0) > 0);
        OrderStatus initialStatus = hasMissingItems
                ? OrderStatus.AWAITING_REPLENISHMENT
                : (PaymentMethod.PAYOS.equals(paymentMethod) ? OrderStatus.AWAITING_PAYMENT : OrderStatus.CONFIRMED);

        Order order = Order.builder().code("ORD" + System.currentTimeMillis()).user(user).status(initialStatus)
                .paymentMethod(paymentMethod).paymentStatus(PaymentStatus.UNPAID).createdAt(LocalDateTime.now())
                .totalAmount(liveQuote.totalSubtotal()).discountAmount(committedVoucher.discountAmount()).finalAmount(liveQuote.totalAmount())
                .totalShippingFee(liveQuote.totalShippingFee()).userLat(draft.getUserLat()).userLng(draft.getUserLng())
                .deliveryAddress(draft.getDeliveryAddress()).shippingAddress(draft.getDeliveryAddress())
                .receiverName(draft.getReceiverName()).receiverPhone(draft.getReceiverPhone())
                .voucher(committedVoucher.voucher()).note(request.getNote()).build();
        Order savedOrder = orderRepository.save(order);

        List<SubOrderSummaryDto> subOrderSummaries = new ArrayList<>();

        for (SubOrderDraftDto subDraft : liveQuote.subOrders()) {
            Branch branch = branchRepository.findById(subDraft.getBranchId()).orElseThrow(() -> new NotFoundException("Branch không tồn tại"));
            boolean subOrderHasMissingItems = subDraft.getItems().stream()
                    .anyMatch(item -> Objects.requireNonNullElse(item.getMissingQuantity(), 0) > 0);
            OrderStatus subOrderStatus = subOrderHasMissingItems
                    ? OrderStatus.AWAITING_REPLENISHMENT
                    : (PaymentMethod.PAYOS.equals(paymentMethod) ? OrderStatus.AWAITING_PAYMENT : OrderStatus.CONFIRMED);
            SubOrder subOrder = SubOrder.builder().order(savedOrder).branch(branch).status(subOrderStatus)
                    .subtotal(subDraft.getSubtotal()).shippingFee(subDraft.getShippingFee())
                    .estimatedDays(subDraft.getEstimatedDays()).carrier(subDraft.getCarrier()).build();
            SubOrder savedSubOrder = subOrderRepository.save(subOrder);

            for (OrderItemDto item : subDraft.getItems()) {
                ProductVariant variant = variantRepository.findById(item.getProductVariantId())
                        .orElseThrow(() -> new NotFoundException("Sản phẩm không tồn tại"));

                subOrderItemRepository.save(SubOrderItem.builder()
                        .subOrder(savedSubOrder)
                        .productVariant(variant)
                        .quantity(item.getQuantity())
                        .allocatedQuantity(item.getAllocatedQuantity() != null ? item.getAllocatedQuantity() : item.getQuantity())
                        .missingQuantity(item.getMissingQuantity() != null ? item.getMissingQuantity() : 0)
                        .unitPrice(item.getUnitPrice())
                        .build());

                int remainingToDeduct = Objects.requireNonNullElse(item.getAllocatedQuantity(), item.getQuantity());
                List<Inventory> batches = inventoryRepository.findForUpdateFIFO(subDraft.getBranchId(), item.getProductVariantId());

                for (Inventory batch : batches) {
                    if (remainingToDeduct <= 0) break;
                    int available = Objects.requireNonNullElse(batch.getQuantity(), 0);
                    if (available <= 0) continue;

                    int deductAmount = Math.min(available, remainingToDeduct);
                    int newQty = available - deductAmount;
                    batch.setQuantity(newQty);
                    inventoryRepository.save(batch);

                    transactionRepository.save(InventoryTransaction.builder()
                            .type(TransactionType.SALE)
                            .quantityChange(-deductAmount)
                            .newBalance(newQty)
                            .referenceCode(buildSubOrderReferenceCode(savedSubOrder))
                            .reason("Bán hàng (Đơn: " + order.getCode() + ")")
                            .createdAt(LocalDateTime.now())
                            .inventory(batch)
                            .build());

                    remainingToDeduct -= deductAmount;
                }

                if (remainingToDeduct > 0) {
                    throw new ConflictException("Lỗi đồng bộ: Kho chi nhánh không đủ số lượng cho sản phẩm " + item.getVariantName());
                }
            }

            subOrderSummaries.add(SubOrderSummaryDto.builder()
                    .subOrderId(savedSubOrder.getId()).branchId(branch.getId()).branchName(branch.getName())
                    .status(subOrderStatus.name()).subtotal(subDraft.getSubtotal()).shippingFee(subDraft.getShippingFee())
                    .estimatedDays(subDraft.getEstimatedDays()).carrier(subDraft.getCarrier()).build());
        }

        String checkoutUrl = null;
        if (PaymentMethod.PAYOS.equals(paymentMethod)) {
            try {
                com.zone.agri.dto.response.payment.PayOSApiResponse.PayOSLinkData payosData = payOSService.createPaymentLink(savedOrder);
                savedOrder.setPayosPaymentLinkId(payosData.getPaymentLinkId());
                savedOrder.setPayosCheckoutUrl(payosData.getCheckoutUrl());
                orderRepository.save(savedOrder);
                checkoutUrl = payosData.getCheckoutUrl();
            } catch (Exception e) {
                throw new BadRequestException("Lỗi tạo PayOS link: " + e.getMessage());
            }
        }

        redisTemplate.delete(PREPARE_KEY_PREFIX + request.getPrepareToken());
        cartSnapshot.stream().map(CartItemDto::getProductVariantId).distinct()
                .forEach(vId -> cartItemRepository.findByUserIdAndProductVariantId(userId, vId).ifPresent(cartItemRepository::delete));

        ConfirmOrderResponse response = ConfirmOrderResponse.builder().orderId(savedOrder.getId()).orderCode(savedOrder.getCode())
                .status(initialStatus.name()).voucherCode(draft.getVoucherCode()).subOrders(subOrderSummaries).totalAmount(liveQuote.totalAmount())
                .discountAmount(committedVoucher.discountAmount()).totalShippingFee(liveQuote.totalShippingFee()).checkoutUrl(checkoutUrl).build();
        saveConfirmResultToRedis(request.getPrepareToken(), response);
        return response;
        } finally {
            redisTemplate.delete(confirmLockKey);
        }
    }

    // ══════════════════════════════════════════════════════════════
    // HELPERS & REDIS
    // ══════════════════════════════════════════════════════════════

    private List<CartItemDto> normalizeCartItems(List<CartItemDto> cartItems) {
        if (cartItems == null || cartItems.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, Integer> quantityByVariant = new LinkedHashMap<>();
        for (CartItemDto item : cartItems) {
            if (item == null || item.getProductVariantId() == null || item.getQuantity() == null || item.getQuantity() <= 0) {
                continue;
            }
            quantityByVariant.merge(item.getProductVariantId(), item.getQuantity(), Integer::sum);
        }

        return quantityByVariant.entrySet().stream()
                .map(entry -> new CartItemDto(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());
    }

    private PreparedQuote buildPreparedQuote(
            Long userId,
            List<CartItemDto> cartItems,
            Double userLat,
            Double userLng,
            String deliveryAddress,
            Integer deliveryDistrictId,
            String deliveryWardCode,
            String voucherCode
    ) {
        List<CartItemDto> normalizedCart = normalizeCartItems(cartItems);
        if (normalizedCart.isEmpty()) {
            throw new BadRequestException("Gio hang cua ban dang trong");
        }

        List<Long> variantIds = normalizedCart.stream().map(CartItemDto::getProductVariantId).distinct().toList();
        List<ProductVariant> variants = variantRepository.findAllById(variantIds);
        if (variants.size() != variantIds.size()) {
            throw new NotFoundException("Mot hoac nhieu san pham khong ton tai");
        }

        Map<Long, ProductVariant> variantMap = variants.stream()
                .collect(Collectors.toMap(ProductVariant::getId, Function.identity()));

        double finalUserLat = userLat != null ? userLat : 10.0341;
        double finalUserLng = userLng != null ? userLng : 105.7904;

        List<BranchWithRealDistance> nearestBranches = branchSearchService.findNearestBranches(finalUserLat, finalUserLng);
        if (nearestBranches.isEmpty()) {
            throw new NotFoundException("Khong co chi nhanh hoat dong");
        }

        List<Long> branchIds = nearestBranches.stream().map(bwr -> bwr.branch().getId()).toList();
        Map<Long, Map<Long, List<Inventory>>> inventoryMatrix = allocationService.buildInventoryMatrix(branchIds, variantIds);
        AllocationResult allocation = allocationService.allocate(normalizedCart, variantMap, nearestBranches, inventoryMatrix);

        List<SubOrderDraftDto> draftSubOrders = mergeRestockNeedsIntoSubOrders(
                allocation.subOrders(),
                allocation.outOfStockItems(),
                nearestBranches,
                variantMap
        );

        DeliveryInfo deliveryInfo = DeliveryInfo.builder()
                .toDistrictId(deliveryDistrictId)
                .toWardCode(deliveryWardCode)
                .deliveryAddress(deliveryAddress)
                .userLat(finalUserLat)
                .userLng(finalUserLng)
                .build();

        List<SubOrderDraftDto> enrichedSubOrders = shippingService.enrichWithShippingFees(
                draftSubOrders,
                deliveryInfo,
                variantMap
        );

        BigDecimal totalSubtotal = enrichedSubOrders.stream()
                .map(s -> s.getSubtotal() != null ? s.getSubtotal() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalShippingFee = enrichedSubOrders.stream()
                .map(s -> s.getShippingFee() != null ? s.getShippingFee() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal discountAmount = BigDecimal.ZERO;
        String normalizedVoucherCode = normalizeVoucherCode(voucherCode);
        if (normalizedVoucherCode != null) {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new NotFoundException("User khong ton tai"));
            discountAmount = validateVoucher(user, normalizedVoucherCode, totalSubtotal, false, false).discountAmount();
        }

        BigDecimal totalAmount = totalSubtotal.add(totalShippingFee).subtract(discountAmount);
        if (totalAmount.compareTo(BigDecimal.ZERO) < 0) {
            totalAmount = BigDecimal.ZERO;
        }

        return new PreparedQuote(
                normalizedCart,
                enrichedSubOrders,
                allocation.outOfStockItems(),
                totalSubtotal,
                discountAmount,
                totalShippingFee,
                totalAmount
        );
    }

    private List<SubOrderDraftDto> mergeRestockNeedsIntoSubOrders(
            List<SubOrderDraftDto> allocatedSubOrders,
            List<OutOfStockItemDto> outOfStockItems,
            List<BranchWithRealDistance> nearestBranches,
            Map<Long, ProductVariant> variantMap
    ) {
        List<SubOrderDraftDto> mergedSubOrders = allocatedSubOrders == null
                ? new ArrayList<>()
                : allocatedSubOrders.stream().map(SubOrderDraftDto::toBuilder).map(builder -> builder.build()).collect(Collectors.toCollection(ArrayList::new));

        if (outOfStockItems == null || outOfStockItems.isEmpty()) {
            return mergedSubOrders;
        }

        BigDecimal profitMultiplier = settingService.getProfitMultiplier();

        for (OutOfStockItemDto outOfStockItem : outOfStockItems) {
            int missingQty = Objects.requireNonNullElse(outOfStockItem.getRequestedQty(), 0);
            if (missingQty <= 0) {
                continue;
            }

            Long targetBranchId = resolveRestockBranchId(mergedSubOrders, nearestBranches, outOfStockItem.getProductVariantId());
            if (targetBranchId == null) {
                continue;
            }

            OrderItemDto restockItem = OrderItemDto.builder()
                    .productVariantId(outOfStockItem.getProductVariantId())
                    .variantName(outOfStockItem.getVariantName())
                    .variantSku(outOfStockItem.getVariantSku())
                    .quantity(missingQty)
                    .allocatedQuantity(0)
                    .missingQuantity(missingQty)
                    .unitPrice(resolveFallbackUnitPrice(outOfStockItem.getProductVariantId(), mergedSubOrders, profitMultiplier))
                    .build();
            restockItem.setSubtotal(restockItem.getUnitPrice().multiply(BigDecimal.valueOf(missingQty)));

            int existingIndex = findSubOrderIndexByBranchId(mergedSubOrders, targetBranchId);
            if (existingIndex >= 0) {
                SubOrderDraftDto existing = mergedSubOrders.get(existingIndex);
                List<OrderItemDto> updatedItems = new ArrayList<>(Objects.requireNonNullElse(existing.getItems(), Collections.emptyList()));
                updatedItems.add(restockItem);
                mergedSubOrders.set(existingIndex, existing.toBuilder()
                        .items(updatedItems)
                        .subtotal(sumItemSubtotals(updatedItems))
                        .build());
            } else {
                Branch targetBranch = nearestBranches.stream()
                        .map(BranchWithRealDistance::branch)
                        .filter(branch -> branch.getId().equals(targetBranchId))
                        .findFirst()
                        .orElse(null);
                if (targetBranch == null) {
                    continue;
                }

                mergedSubOrders.add(SubOrderDraftDto.builder()
                        .branchId(targetBranch.getId())
                        .branchName(targetBranch.getName())
                        .branchAddress(targetBranch.getAddressDetail())
                        .fromDistrictId(targetBranch.getDistrictId())
                        .distanceKm(0)
                        .durationMinutes(0)
                        .items(new ArrayList<>(List.of(restockItem)))
                        .subtotal(restockItem.getSubtotal())
                        .shippingFee(BigDecimal.ZERO)
                        .build());
            }
        }

        return mergedSubOrders;
    }

    private int findSubOrderIndexByBranchId(List<SubOrderDraftDto> subOrders, Long branchId) {
        for (int index = 0; index < subOrders.size(); index++) {
            if (Objects.equals(subOrders.get(index).getBranchId(), branchId)) {
                return index;
            }
        }
        return -1;
    }

    private Long resolveRestockBranchId(
            List<SubOrderDraftDto> subOrders,
            List<BranchWithRealDistance> nearestBranches,
            Long productVariantId
    ) {
        for (SubOrderDraftDto subOrder : subOrders) {
            if (subOrder.getItems() != null && subOrder.getItems().stream()
                    .anyMatch(item -> Objects.equals(item.getProductVariantId(), productVariantId))) {
                return subOrder.getBranchId();
            }
        }

        if (!subOrders.isEmpty()) {
            return subOrders.get(0).getBranchId();
        }

        return nearestBranches.isEmpty() ? null : nearestBranches.get(0).branch().getId();
    }

    private BigDecimal resolveFallbackUnitPrice(Long productVariantId, List<SubOrderDraftDto> subOrders, BigDecimal profitMultiplier) {
        for (SubOrderDraftDto subOrder : subOrders) {
            if (subOrder.getItems() == null) {
                continue;
            }
            for (OrderItemDto item : subOrder.getItems()) {
                if (Objects.equals(item.getProductVariantId(), productVariantId) && item.getUnitPrice() != null) {
                    return item.getUnitPrice();
                }
            }
        }

        return inventoryRepository.findByProductVariantId(productVariantId).stream()
                .filter(inventory -> inventory.getImportPrice() != null)
                .map(inventory -> inventory.getImportPrice().multiply(profitMultiplier))
                .findFirst()
                .orElse(BigDecimal.ZERO);
    }

    private BigDecimal sumItemSubtotals(List<OrderItemDto> items) {
        return items.stream()
                .map(item -> item.getSubtotal() != null ? item.getSubtotal() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void ensurePreparedQuoteStillValid(PrepareOrderDraft draft, PreparedQuote liveQuote) {
        boolean totalsChanged = !hasSameMoney(draft.getTotalSubtotal(), liveQuote.totalSubtotal())
                || !hasSameMoney(draft.getDiscountAmount(), liveQuote.discountAmount())
                || !hasSameMoney(draft.getTotalShippingFee(), liveQuote.totalShippingFee())
                || !hasSameMoney(draft.getTotalAmount(), liveQuote.totalAmount());

        if (totalsChanged || !Objects.equals(buildSubOrderSignature(draft.getSubOrders()), buildSubOrderSignature(liveQuote.subOrders()))) {
            throw new ConflictException("Don hang vua thay doi, vui long prepare lai truoc khi dat");
        }
    }

    private boolean hasSameMoney(BigDecimal left, BigDecimal right) {
        return Objects.requireNonNullElse(left, BigDecimal.ZERO)
                .compareTo(Objects.requireNonNullElse(right, BigDecimal.ZERO)) == 0;
    }

    private String buildSubOrderSignature(List<SubOrderDraftDto> subOrders) {
        if (subOrders == null || subOrders.isEmpty()) {
            return "";
        }

        return subOrders.stream()
                .sorted(Comparator.comparing(SubOrderDraftDto::getBranchId, Comparator.nullsLast(Long::compareTo)))
                .map(subOrder -> {
                    String itemSignature = subOrder.getItems() == null ? "" : subOrder.getItems().stream()
                            .sorted(Comparator
                                    .comparing(OrderItemDto::getProductVariantId, Comparator.nullsLast(Long::compareTo))
                                    .thenComparing(item -> safeBigDecimal(item.getUnitPrice()))
                                    .thenComparing(item -> Objects.requireNonNullElse(item.getQuantity(), 0)))
                            .map(item -> String.join(":",
                                    String.valueOf(item.getProductVariantId()),
                                    String.valueOf(Objects.requireNonNullElse(item.getQuantity(), 0)),
                                    String.valueOf(Objects.requireNonNullElse(item.getAllocatedQuantity(), 0)),
                                    String.valueOf(Objects.requireNonNullElse(item.getMissingQuantity(), 0)),
                                    safeBigDecimal(item.getUnitPrice()),
                                    safeBigDecimal(item.getSubtotal())))
                            .collect(Collectors.joining(","));

                    return String.join("|",
                            String.valueOf(subOrder.getBranchId()),
                            safeBigDecimal(subOrder.getSubtotal()),
                            safeBigDecimal(subOrder.getShippingFee()),
                            Objects.toString(subOrder.getEstimatedDays(), ""),
                            Objects.toString(subOrder.getCarrier(), ""),
                            itemSignature);
                })
                .collect(Collectors.joining("||"));
    }

    private String safeBigDecimal(BigDecimal value) {
        BigDecimal normalized = Objects.requireNonNullElse(value, BigDecimal.ZERO);
        return normalized.stripTrailingZeros().toPlainString();
    }

    private String normalizeVoucherCode(String voucherCode) {
        if (voucherCode == null || voucherCode.isBlank()) {
            return null;
        }
        return voucherCode.trim().toUpperCase();
    }

    private VoucherValidation validateVoucher(
            User user,
            String voucherCode,
            BigDecimal orderSubtotal,
            boolean consume,
            boolean conflictOnUnavailable
    ) {
        String normalizedVoucherCode = normalizeVoucherCode(voucherCode);
        if (normalizedVoucherCode == null) {
            return new VoucherValidation(null, null, BigDecimal.ZERO);
        }

        Voucher voucher = (consume ? voucherRepository.findByCodeForUpdate(normalizedVoucherCode) : voucherRepository.findByCode(normalizedVoucherCode))
                .orElseThrow(() -> voucherValidationException(conflictOnUnavailable, "Ma voucher khong ton tai"));

        LocalDateTime now = LocalDateTime.now();
        if (voucher.getStatus() != VoucherStatus.ACTIVE
                || voucher.getStartDate() == null
                || voucher.getEndDate() == null
                || now.isBefore(voucher.getStartDate())
                || now.isAfter(voucher.getEndDate())) {
            throw voucherValidationException(conflictOnUnavailable, "Voucher khong hop le hoac da het han");
        }

        BigDecimal minOrderValue = voucher.getMinOrderValue() != null ? voucher.getMinOrderValue() : BigDecimal.ZERO;
        if (orderSubtotal.compareTo(minOrderValue) < 0) {
            throw voucherValidationException(conflictOnUnavailable, "Don hang chua dat gia tri toi thieu de su dung voucher nay");
        }

        if (Objects.requireNonNullElse(voucher.getQuantity(), 0) <= 0) {
            throw voucherValidationException(conflictOnUnavailable, "Voucher nay da het luot su dung tren he thong");
        }

        UserVoucher userVoucher = userVoucherRepository.findByUserAndVoucher(user, voucher)
                .orElse(new UserVoucher(user, voucher, 0, false));
        int maxUsagePerUser = voucher.getMaxUsagePerUser() != null ? voucher.getMaxUsagePerUser() : 1;
        if (Objects.requireNonNullElse(userVoucher.getUsageCount(), 0) >= maxUsagePerUser) {
            throw voucherValidationException(conflictOnUnavailable, "Ban da su dung toi da so luot cho phep cua voucher nay");
        }

        BigDecimal discountAmount;
        if (VoucherDiscountType.PERCENT.equals(voucher.getDiscountType())) {
            BigDecimal percentValue = voucher.getValue() != null ? voucher.getValue() : BigDecimal.ZERO;
            BigDecimal calculatedDiscount = orderSubtotal.multiply(percentValue).divide(BigDecimal.valueOf(100));

            if (voucher.getMaxDiscount() != null && voucher.getMaxDiscount().compareTo(BigDecimal.ZERO) > 0) {
                discountAmount = calculatedDiscount.min(voucher.getMaxDiscount());
            } else {
                discountAmount = calculatedDiscount;
            }
        } else {
            discountAmount = voucher.getValue() != null ? voucher.getValue() : BigDecimal.ZERO;
        }

        if (discountAmount.compareTo(orderSubtotal) > 0) {
            discountAmount = orderSubtotal;
        }

        if (consume) {
            voucher.setQuantity(Objects.requireNonNullElse(voucher.getQuantity(), 0) - 1);
            userVoucher.setUsageCount(Objects.requireNonNullElse(userVoucher.getUsageCount(), 0) + 1);
            voucherRepository.save(voucher);
            userVoucherRepository.save(userVoucher);
        }

        return new VoucherValidation(voucher, userVoucher, discountAmount);
    }

    private RuntimeException voucherValidationException(boolean conflictOnUnavailable, String message) {
        return conflictOnUnavailable ? new ConflictException(message) : new BadRequestException(message);
    }

    private String buildSubOrderReferenceCode(SubOrder subOrder) {
        return subOrder.getOrder().getCode() + "-SUB-" + subOrder.getId();
    }

    private void releaseAllocatedInventoryForOrder(Order order) {
        if (order.getSubOrders() != null && !order.getSubOrders().isEmpty()) {
            order.getSubOrders().stream()
                    .filter(subOrder -> subOrder.getStatus() != OrderStatus.CANCELLED)
                    .forEach(this::releaseAllocatedInventoryForSubOrder);
            return;
        }

        List<InventoryTransaction> legacyTransactions = transactionRepository.findByReferenceCodeAndType(order.getCode(), TransactionType.SALE);
        releaseInventoryTransactions(order.getCode(), legacyTransactions, "Hoan kho do huy don hang");
    }

    private void releaseAllocatedInventoryForSubOrder(SubOrder subOrder) {
        List<InventoryTransaction> saleTransactions = transactionRepository.findByReferenceCodeAndType(
                buildSubOrderReferenceCode(subOrder),
                TransactionType.SALE
        );
        releaseInventoryTransactions(buildSubOrderReferenceCode(subOrder), saleTransactions, "Hoan kho do huy sub-order");
    }

    private void releaseInventoryTransactions(String referenceCode, List<InventoryTransaction> saleTransactions, String reason) {
        for (InventoryTransaction saleTransaction : saleTransactions) {
            int quantityToRelease = Math.abs(Objects.requireNonNullElse(saleTransaction.getQuantityChange(), 0));
            if (quantityToRelease <= 0 || saleTransaction.getInventory() == null) {
                continue;
            }

            Inventory inventory = saleTransaction.getInventory();
            int currentQty = Objects.requireNonNullElse(inventory.getQuantity(), 0);
            int newQty = currentQty + quantityToRelease;
            inventory.setQuantity(newQty);
            inventoryRepository.save(inventory);

            transactionRepository.save(InventoryTransaction.builder()
                    .type(TransactionType.CANCEL_RELEASE)
                    .quantityChange(quantityToRelease)
                    .newBalance(newQty)
                    .referenceCode(referenceCode)
                    .reason(reason)
                    .createdAt(LocalDateTime.now())
                    .inventory(inventory)
                    .build());
        }
    }

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

    private void saveConfirmResultToRedis(String token, ConfirmOrderResponse response) {
        try {
            redisTemplate.opsForValue().set(
                    PREPARE_CONFIRM_RESULT_PREFIX + token,
                    objectMapper.writeValueAsString(response),
                    PREPARE_TTL_MINUTES,
                    TimeUnit.MINUTES
            );
        } catch (JsonProcessingException e) {
            log.warn("Khong the luu ket qua confirm vao Redis cho token {}: {}", token, e.getMessage());
        }
    }

    private ConfirmOrderResponse getConfirmResultFromRedis(String token) {
        String json = redisTemplate.opsForValue().get(PREPARE_CONFIRM_RESULT_PREFIX + token);
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, ConfirmOrderResponse.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    // ══════════════════════════════════════════════════════════════
    // CHECKOUT API TRỰC TIẾP (CÓ ÁP DỤNG VOUCHER & PAYOS)
    // ══════════════════════════════════════════════════════════════

    @Transactional
    public Order placeOrder(Long userId, CheckoutRequest req) {
        User user = userRepository.findById(userId).orElseThrow(() -> new NotFoundException("User không tìm thấy"));

        // Lấy chi nhánh từ request (gửi từ FE) hoặc tìm tự động
        Branch selectedBranch;
        if (req.getBranchId() != null) {
            selectedBranch = branchRepository.findById(req.getBranchId())
                    .orElseThrow(() -> new NotFoundException("Chi nhánh không tồn tại"));
        } else {
            selectedBranch = findBranchWithEnoughStock(req.getItems());
            if (selectedBranch == null) throw new BadRequestException("Không chi nhánh nào đủ hàng");
        }

        PaymentMethod paymentMethod = req.getPaymentMethod() != null ? req.getPaymentMethod() : PaymentMethod.COD;
        OrderStatus legacyInitialStatus = PaymentMethod.PAYOS.equals(paymentMethod)
                ? OrderStatus.AWAITING_PAYMENT
                : OrderStatus.CONFIRMED;

        Order order = Order.builder()
                .code("ORD" + System.currentTimeMillis())
                .user(user)
                .branch(selectedBranch)
                .shippingAddress(req.getShippingAddress())
                .receiverName(req.getFullName())
                .receiverPhone(req.getPhone())
                .note(req.getNote())
                .status(legacyInitialStatus)
                .paymentMethod(paymentMethod)
                .paymentStatus(PaymentStatus.UNPAID)
                .createdAt(LocalDateTime.now())
                .totalAmount(BigDecimal.ZERO)
                .discountAmount(BigDecimal.ZERO)
                .totalShippingFee(BigDecimal.ZERO)
                .finalAmount(BigDecimal.ZERO)
                .build();
        Order savedOrder = orderRepository.save(order);

        BigDecimal subTotal = BigDecimal.ZERO;

        for (CheckoutItemRequest itemReq : req.getItems()) {
            ProductVariant variant = variantRepository.findById(itemReq.getVariantId())
                    .orElseThrow(() -> new NotFoundException("Sản phẩm không tồn tại"));

            // 👉 LOGIC TRỪ KHO THEO LÔ (FIFO)
            int remainingToDeduct = itemReq.getQuantity();
            List<Inventory> batches = inventoryRepository.findForUpdateFIFO(selectedBranch.getId(), variant.getId());

            for (Inventory batch : batches) {
                if (remainingToDeduct <= 0) break;
                int available = Objects.requireNonNullElse(batch.getQuantity(), 0);
                if (available <= 0) continue;

                int deduct = Math.min(available, remainingToDeduct);
                int newQty = available - deduct;
                batch.setQuantity(newQty);
                inventoryRepository.save(batch);

                transactionRepository.save(InventoryTransaction.builder()
                        .type(TransactionType.SALE)
                        .quantityChange(-deduct)
                        .newBalance(newQty)
                        .referenceCode(savedOrder.getCode())
                        .reason("Bán hàng trực tiếp (Đơn: " + savedOrder.getCode() + ")")
                        .createdAt(LocalDateTime.now())
                        .inventory(batch)
                        .build());

                remainingToDeduct -= deduct;

                BigDecimal importPrice = batch.getImportPrice() != null ? batch.getImportPrice() : BigDecimal.ZERO;
                BigDecimal sellingPrice = importPrice.multiply(settingService.getProfitMultiplier());

                orderItemRepository.save(OrderItem.builder()
                        .order(savedOrder)
                        .productVariant(variant)
                        .quantity(deduct)
                        .price(sellingPrice)
                        .build());

                subTotal = subTotal.add(sellingPrice.multiply(new BigDecimal(deduct)));
            }

            if (remainingToDeduct > 0) {
                throw new ConflictException("Hết hàng trong lúc thanh toán cho sản phẩm: " + variant.getSku());
            }
        }

        BigDecimal discountAmount = BigDecimal.ZERO;

        // 👉 VOUCHER LOGIC
        if (req.getVoucherCode() != null && !req.getVoucherCode().trim().isEmpty()) {
            Voucher voucher = voucherRepository.findByCode(req.getVoucherCode().trim().toUpperCase())
                    .orElseThrow(() -> new BadRequestException("Mã voucher không tồn tại"));

            LocalDateTime now = LocalDateTime.now();

            if (voucher.getStatus() != VoucherStatus.ACTIVE || now.isBefore(voucher.getStartDate()) || now.isAfter(voucher.getEndDate())) {
                throw new BadRequestException("Voucher không hợp lệ hoặc đã hết hạn");
            }

            if (subTotal.compareTo(voucher.getMinOrderValue() != null ? voucher.getMinOrderValue() : BigDecimal.ZERO) < 0) {
                throw new BadRequestException("Đơn hàng chưa đạt giá trị tối thiểu để sử dụng voucher này");
            }

            if (voucher.getQuantity() <= 0) {
                throw new BadRequestException("Voucher này đã hết lượt sử dụng trên hệ thống");
            }

            UserVoucher userVoucher = userVoucherRepository.findByUserAndVoucher(user, voucher)
                    .orElse(new UserVoucher(user, voucher, 0, false));

            if (userVoucher.getUsageCount() >= (voucher.getMaxUsagePerUser() != null ? voucher.getMaxUsagePerUser() : 1)) {
                throw new BadRequestException("Bạn đã sử dụng tối đa số lượt cho phép của voucher này");
            }

            // ĐÃ CHỈNH SỬA: SỬA LỖI KIỂU DỮ LIỆU CỦA VALUE VÀ MAX_DISCOUNT
            if (VoucherDiscountType.PERCENT.equals(voucher.getDiscountType())) {
                BigDecimal percentValue = voucher.getValue() != null ? voucher.getValue() : BigDecimal.ZERO;
                BigDecimal calculatedDiscount = subTotal.multiply(percentValue).divide(BigDecimal.valueOf(100));

                if (voucher.getMaxDiscount() != null && voucher.getMaxDiscount().compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal maxDiscountDecimal = voucher.getMaxDiscount();
                    discountAmount = calculatedDiscount.compareTo(maxDiscountDecimal) > 0 ? maxDiscountDecimal : calculatedDiscount;
                } else {
                    discountAmount = calculatedDiscount;
                }
            } else {
                discountAmount = voucher.getValue() != null ? voucher.getValue() : BigDecimal.ZERO;
            }

            if (discountAmount.compareTo(subTotal) > 0) {
                discountAmount = subTotal;
            }

            voucher.setQuantity(voucher.getQuantity() - 1);
            userVoucher.setUsageCount(userVoucher.getUsageCount() + 1);

            voucherRepository.save(voucher);
            userVoucherRepository.save(userVoucher);
        }

        // 👉 PHÍ VẬN CHUYỂN
        BigDecimal shippingFee = new BigDecimal("15000");

        // 👉 CHỐT TỔNG TIỀN VÀ CẬP NHẬT ĐƠN HÀNG
        BigDecimal finalAmount = subTotal.add(shippingFee).subtract(discountAmount);
        if (finalAmount.compareTo(BigDecimal.ZERO) < 0) {
            finalAmount = BigDecimal.ZERO;
        }

        savedOrder.setTotalAmount(subTotal);
        savedOrder.setTotalShippingFee(shippingFee);
        savedOrder.setDiscountAmount(discountAmount);
        savedOrder.setFinalAmount(finalAmount);

        // 👉 PAYOS LOGIC
        if (PaymentMethod.PAYOS.equals(paymentMethod)) {
            try {
                com.zone.agri.dto.response.payment.PayOSApiResponse.PayOSLinkData payosData = payOSService.createPaymentLink(savedOrder);
                savedOrder.setPayosPaymentLinkId(payosData.getPaymentLinkId());
                savedOrder.setPayosCheckoutUrl(payosData.getCheckoutUrl());
            } catch (Exception e) {
                throw new BadRequestException("Lỗi tạo PayOS link: " + e.getMessage());
            }
        }

        orderRepository.save(savedOrder);

        // Xóa sản phẩm khỏi giỏ hàng
        req.getItems().stream().map(CheckoutItemRequest::getVariantId).distinct()
                .forEach(vId -> cartItemRepository.findByUserIdAndProductVariantId(userId, vId).ifPresent(cartItemRepository::delete));

        return savedOrder;
    }

    private Branch findBranchWithEnoughStock(List<CheckoutItemRequest> itemsToBuy) {
        return branchRepository.findAll().stream().filter(branch -> itemsToBuy.stream().allMatch(item -> {
            int totalStock = inventoryRepository.findByProductVariantId(item.getVariantId()).stream()
                    .filter(inv -> inv.getBranch().getId().equals(branch.getId()))
                    .mapToInt(Inventory::getQuantity).sum();
            return totalStock >= item.getQuantity();
        })).findFirst().orElse(null);
    }
}
