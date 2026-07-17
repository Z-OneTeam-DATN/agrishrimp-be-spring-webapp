package com.zone.agri.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zone.agri.dto.response.geo.DeliveryInfo;
import com.zone.agri.dto.request.order.*;
import com.zone.agri.dto.response.order.*;
import com.zone.agri.entity.*;
import com.zone.agri.entity.enums.FulfillmentStatus;
import com.zone.agri.entity.enums.InventoryTransferStatus;
import com.zone.agri.entity.enums.OrderStatus;
import com.zone.agri.entity.enums.PaymentMethod;
import com.zone.agri.entity.enums.PaymentStatus;
import com.zone.agri.entity.enums.StockStatus;
import com.zone.agri.entity.enums.TransactionType;
import com.zone.agri.entity.enums.VoucherStatus;
import com.zone.agri.entity.enums.VoucherDiscountType; // ThĂªm Enum nĂ y Ä‘á»ƒ kiá»ƒm tra loáº¡i Voucher
import com.zone.agri.exception.BadRequestException;
import com.zone.agri.exception.ConflictException;
import com.zone.agri.exception.Forbidden;
import com.zone.agri.exception.NotFoundException;
import com.zone.agri.repository.*;
import com.zone.agri.service.BranchSearchService.BranchWithRealDistance;
import com.zone.agri.service.InventoryAllocationService.AllocationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.annotation.Lazy;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
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
    private final InventoryCheckGuardService inventoryCheckGuardService;

    private final UserAddressRepository userAddressRepository;
    private final ReviewRepository reviewRepository;

    private final VoucherRepository voucherRepository;
    private final UserVoucherRepository userVoucherRepository;

    private final BranchSearchService branchSearchService;
    private final InventoryAllocationService allocationService;
    private final ShippingService shippingService;
    private final PayOSService payOSService;
    private final SettingService settingService;
    private final InventoryTransferRepository inventoryTransferRepository;
    private final InventoryTransferService inventoryTransferService;
    private final BackorderService backorderService;
    private final ImmediateReplenishmentService immediateReplenishmentService;
    private final VoucherService voucherService;
    private final OrderStatusSyncService orderStatusSyncService;
    private final OrderInventoryReservationService orderInventoryReservationService;

    @Lazy
    private final CustomerService customerService;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private static final String PREPARE_KEY_PREFIX = "prepare:";
    private static final String PREPARE_CONFIRM_LOCK_PREFIX = "prepare:confirm:lock:";
    private static final String PREPARE_CONFIRM_RESULT_PREFIX = "prepare:confirm:result:";
    private static final String PREPARE_CONFIRM_IDEMPOTENCY_PREFIX = "prepare:confirm:idempotency:";
    private static final long PREPARE_TTL_MINUTES = 30;
    private static final long PREPARE_CONFIRM_LOCK_TTL_SECONDS = 120;
    private static final long SHIPPING_RECEIVED_CONFIRM_AFTER_DAYS = 7;

    @Value("${order.payment-expiry-minutes:15}")
    private long paymentExpiryMinutes;

    @Value("${order.auto-approve-minutes:5}")
    private long autoApproveMinutes;

    private record PreparedQuote(
            List<CartItemDto> cartItems,
            List<SubOrderDraftDto> subOrders,
            List<OutOfStockItemDto> outOfStockItems,
            BigDecimal totalSubtotal,
            BigDecimal discountAmount,
            BigDecimal totalShippingFee,
            BigDecimal totalAmount) {
    }

    private record VoucherValidation(
            Voucher voucher,
            UserVoucher userVoucher,
            BigDecimal discountAmount) {
    }

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    // QUáº¢N LĂ ÄÆ N HĂ€NG CHO USER
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

    @Transactional
    public List<OrderResponse> getMyOrders(Long userId, OrderStatus status) {
        List<Order> orders;
        if (status != null) {
            orders = orderRepository.findByUserIdAndStatusOrderByCreatedAtDesc(userId, status);
        } else {
            orders = orderRepository.findByUserIdOrderByCreatedAtDesc(userId);
        }
        syncPayOSPaymentStatuses(orders);
        return orders.stream().map(o -> mapToOrderResponse(o, true)).collect(Collectors.toList());
    }

    @Transactional
    public OrderResponse getMyOrderDetail(Long userId, Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("KhĂ´ng tĂ¬m tháº¥y Ä‘Æ¡n hĂ ng ID: " + orderId));

        if (!order.getUser().getId().equals(userId)) {
            throw new BadRequestException("Báº¡n khĂ´ng cĂ³ quyá»n xem Ä‘Æ¡n hĂ ng nĂ y!");
        }

        if (PaymentMethod.PAYOS.equals(order.getPaymentMethod())
                && isPendingPayosPayment(order)) {
            if (payOSService.checkPaymentStatus(order)) {
                payOSService.markOrderPaid(order);
            }
        }

        return mapToOrderResponse(order, true);
    }

    @Transactional
    public void confirmReceivedByCustomer(Long userId, Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("KhĂ´ng tĂ¬m tháº¥y Ä‘Æ¡n hĂ ng ID: " + orderId));

        if (order.getUser() == null || !order.getUser().getId().equals(userId)) {
            throw new Forbidden("Báº¡n khĂ´ng cĂ³ quyá»n thao tĂ¡c trĂªn Ä‘Æ¡n hĂ ng nĂ y");
        }

        if (order.getStatus() != OrderStatus.SHIPPING) {
            throw new BadRequestException("Chá»‰ cĂ³ thá»ƒ xĂ¡c nháº­n khi Ä‘Æ¡n hĂ ng Ä‘ang giao.");
        }

        markOrderAsReceived(order, true);
    }

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    // QUáº¢N LĂ ÄÆ N HĂ€NG CHO ADMIN
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

    @Transactional
    public Page<OrderResponse> getAdminOrders(String status, String search, Pageable pageable) {
        List<Order> orders = orderRepository.findAdminOrdersWithFilter(null, search, Pageable.unpaged()).getContent();
        syncPayOSPaymentStatuses(orders);

        List<OrderResponse> filtered = orders.stream()
                .filter(order -> matchesWorkflowStatusFilter(order, status))
                .map(order -> mapToOrderResponse(order, false))
                .toList();

        if (pageable.isUnpaged()) {
            return new PageImpl<>(filtered);
        }

        int start = Math.min((int) pageable.getOffset(), filtered.size());
        int end = Math.min(start + pageable.getPageSize(), filtered.size());
        return new PageImpl<>(filtered.subList(start, end), pageable, filtered.size());
    }

    @Transactional
    public OrderResponse getAdminOrderDetail(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("KhĂ´ng tĂ¬m tháº¥y Ä‘Æ¡n hĂ ng ID: " + orderId));

        if (PaymentMethod.PAYOS.equals(order.getPaymentMethod())
                && isPendingPayosPayment(order)) {
            if (payOSService.checkPaymentStatus(order)) {
                payOSService.markOrderPaid(order);
            }
        }

        return mapToOrderResponse(order, false);
    }

    @Transactional
    public void cancelMyOrder(Long userId, Long orderId, String cancelReason) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("KhĂ´ng tĂ¬m tháº¥y Ä‘Æ¡n hĂ ng ID: " + orderId));

        if (!order.getUser().getId().equals(userId)) {
            throw new BadRequestException("Báº¡n khĂ´ng cĂ³ quyá»n há»§y Ä‘Æ¡n hĂ ng nĂ y");
        }
        OrderStatus currentStatus = order.getStatus();
        if (currentStatus == OrderStatus.CANCELLED || currentStatus == OrderStatus.COMPLETED
                || currentStatus == OrderStatus.RETURNED) {
            throw new BadRequestException("ÄÆ¡n hĂ ng Ä‘Ă£ Ä‘Ă³ng, khĂ´ng thá»ƒ há»§y");
        }
        if (currentStatus == OrderStatus.SHIPPING) {
            throw new BadRequestException("ÄÆ¡n hĂ ng Ä‘ang giao, khĂ´ng thá»ƒ há»§y");
        }

        if (!canCustomerCancelOrder(currentStatus)) {
            throw new BadRequestException("Đơn hàng đã được xác nhận hoặc đang xử lý, không thể hủy");
        }
        releaseAllocatedInventoryForOrder(order);
        voucherService.restoreVoucherForOrder(order);
        LocalDateTime cancelledAt = LocalDateTime.now();
        applyOrderStatus(order, OrderStatus.CANCELLED, cancelledAt);
        if (order.getSubOrders() != null) {
            List<SubOrder> cancelledSubOrders = order.getSubOrders().stream()
                    .peek(subOrder -> applySubOrderStatus(subOrder, OrderStatus.CANCELLED, cancelledAt))
                    .toList();
            if (!cancelledSubOrders.isEmpty()) {
                subOrderRepository.saveAll(cancelledSubOrders);
            }
        }
        if (PaymentMethod.PAYOS.equals(order.getPaymentMethod())
                && isPendingPayosPayment(order)) {
            payOSService.cancelPaymentLink(order);
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
                .orElseThrow(() -> new NotFoundException("KhĂ´ng tĂ¬m tháº¥y Ä‘Æ¡n hĂ ng ID: " + orderId));

        OrderStatus currentStatus = order.getStatus();
        if (currentStatus == OrderStatus.CANCELLED || currentStatus == OrderStatus.COMPLETED
                || currentStatus == OrderStatus.RETURNED) {
            throw new BadRequestException("KhĂ´ng thá»ƒ thay Ä‘á»•i tráº¡ng thĂ¡i cá»§a Ä‘Æ¡n hĂ ng Ä‘Ă£ Ä‘Ă³ng!");
        }

        if (currentStatus == OrderStatus.SHIPPING && newStatus == OrderStatus.COMPLETED) {
            throw new BadRequestException("ÄÆ¡n hĂ ng Ä‘ang giao chá»‰ Ä‘Æ°á»£c hoĂ n táº¥t khi khĂ¡ch hĂ ng xĂ¡c nháº­n Ä‘Ă£ nháº­n.");
        }

        if (currentStatus == OrderStatus.PENDING
                && newStatus == OrderStatus.CONFIRMED
                && orderHasMissingItems(order)) {
            throw new BadRequestException(
                    "Don hang dang thieu hang. Vui long xin lenh dieu chuyen truoc khi duyet sang buoc tiep theo.");
        }

        validateStatusTransition(currentStatus, newStatus);

        if (newStatus == OrderStatus.RECEIVED) {
            markOrderAsReceived(order, true);
            return;
        }
        if (newStatus == OrderStatus.COMPLETED) {
            completeReceivedOrder(order);
            customerService.evaluateAndHandleCustomerReputation(order.getUser().getId());
            return;
        }
        LocalDateTime statusChangedAt = LocalDateTime.now();
        if (newStatus == OrderStatus.CANCELLED) {
            releaseAllocatedInventoryForOrder(order);
            voucherService.restoreVoucherForOrder(order);
            if (order.getSubOrders() != null) {
                List<SubOrder> cancelledSubOrders = order.getSubOrders().stream()
                        .filter(subOrder -> subOrder.getStatus() != OrderStatus.CANCELLED)
                        .peek(subOrder -> applySubOrderStatus(subOrder, OrderStatus.CANCELLED, statusChangedAt))
                        .toList();
                if (!cancelledSubOrders.isEmpty()) {
                    subOrderRepository.saveAll(cancelledSubOrders);
                }
            }
            if (PaymentMethod.PAYOS.equals(order.getPaymentMethod())
                    && isPendingPayosPayment(order)) {
                payOSService.cancelPaymentLink(order);
            }
        }
        syncActiveSubOrdersForStatusChange(order, newStatus, statusChangedAt);
        if (newStatus == OrderStatus.SHIPPING
                && (order.getSubOrders() == null || order.getSubOrders().isEmpty())) {
            orderInventoryReservationService.shipReservedInventory(
                    order.getCode(),
                    "Xuat kho don hang " + order.getCode() + " khi chuyen sang giao hang");
        }

        applyOrderStatus(order, newStatus, statusChangedAt);
        orderRepository.save(order);

        if (newStatus == OrderStatus.COMPLETED || newStatus == OrderStatus.CANCELLED
                || newStatus == OrderStatus.RETURNED) {
            customerService.evaluateAndHandleCustomerReputation(order.getUser().getId());
        }
    }

    private void validateStatusTransition(OrderStatus current, OrderStatus next) {
        if (next == OrderStatus.CANCELLED)
            return;
        if (current == OrderStatus.PENDING) {
            if (next != OrderStatus.CONFIRMED) {
                throw new BadRequestException(
                        "ÄÆ¡n hĂ ng chá» xĂ¡c nháº­n chá»‰ cĂ³ thá»ƒ chuyá»ƒn sang 'ÄĂ£ xĂ¡c nháº­n'.");
            }
            return;
        }
        if (current == OrderStatus.AWAITING_PAYMENT) {
            if (next != OrderStatus.CONFIRMED && next != OrderStatus.AWAITING_REPLENISHMENT
                    && next != OrderStatus.PROCESSING) {
                throw new BadRequestException(
                        "ÄÆ¡n hĂ ng chá» thanh toĂ¡n chá»‰ cĂ³ thá»ƒ chuyá»ƒn sang 'Äang xá»­ lĂ½' hoáº·c 'Chá» bá»• sung' sau khi thanh toĂ¡n xong.");
            }
            return;
        }
        if (current == OrderStatus.AWAITING_REPLENISHMENT) {
            if (next != OrderStatus.CONFIRMED && next != OrderStatus.PROCESSING) {
                throw new BadRequestException(
                        "ÄÆ¡n hĂ ng thiáº¿u hĂ ng chá»‰ cĂ³ thá»ƒ chuyá»ƒn sang 'Äang xá»­ lĂ½' sau khi nháº­p Ä‘á»§.");
            }
            return;
        }
        if (current == OrderStatus.CONFIRMED) {
            if (next != OrderStatus.PROCESSING) {
                throw new BadRequestException("ÄÆ¡n hĂ ng Ä‘Ă£ duyá»‡t chá»‰ cĂ³ thá»ƒ chuyá»ƒn sang 'Äang xá»­ lĂ½'.");
            }
            return;
        }
        if (current == OrderStatus.PROCESSING) {
            if (next != OrderStatus.READY_FOR_PICKUP) {
                throw new BadRequestException(
                        "ÄÆ¡n hĂ ng á»Ÿ tráº¡ng thĂ¡i 'Äang xá»­ lĂ½' chá»‰ cĂ³ thá»ƒ chuyá»ƒn sang 'Sáºµn sĂ ng bĂ n giao'.");
            }
            return;
        }
        if (current == OrderStatus.READY_FOR_PICKUP) {
            if (next != OrderStatus.SHIPPING) {
                throw new BadRequestException("ÄÆ¡n hĂ ng chá» bĂ n giao chá»‰ cĂ³ thá»ƒ chuyá»ƒn sang 'Äang giao'.");
            }
            return;
        }
        if (current == OrderStatus.SHIPPING) {
            if (next != OrderStatus.RECEIVED && next != OrderStatus.RETURNED) {
                throw new BadRequestException(
                        "ÄÆ¡n hĂ ng Ä‘ang giao chá»‰ cĂ³ thá»ƒ chuyá»ƒn sang 'ÄĂ£ nháº­n hĂ ng' hoáº·c 'ÄĂ£ tráº£ hĂ ng'.");
            }
            return;
        }
        if (current == OrderStatus.RECEIVED && next != OrderStatus.COMPLETED) {
            throw new BadRequestException("Ă„ÂĂ†Â¡n hĂƒÂ ng Ă„â€˜ĂƒÂ£ nhĂ¡ÂºÂ­n chĂ¡Â»â€° cĂƒÂ³ thĂ¡Â»Æ’ chuyĂ¡Â»Æ’n sang 'HoĂƒÂ n thĂƒÂ nh'.");
        }
    }

    private void syncActiveSubOrdersForStatusChange(
            Order order,
            OrderStatus targetStatus,
            LocalDateTime changedAt) {
        if (order == null || order.getSubOrders() == null || order.getSubOrders().isEmpty()) {
            return;
        }

        if (targetStatus != OrderStatus.CONFIRMED
                && targetStatus != OrderStatus.PROCESSING
                && targetStatus != OrderStatus.READY_FOR_PICKUP
                && targetStatus != OrderStatus.SHIPPING) {
            return;
        }

        List<SubOrder> subOrdersToUpdate = new ArrayList<>();

        for (SubOrder subOrder : order.getSubOrders()) {
            if (subOrder == null
                    || subOrder.getStatus() == null
                    || subOrder.getStatus() == OrderStatus.CANCELLED
                    || subOrder.getStatus() == OrderStatus.COMPLETED
                    || subOrder.getStatus() == OrderStatus.RETURNED
                    || subOrder.getStatus() == targetStatus) {
                continue;
            }

            if (statusWeight(subOrder.getStatus()) > statusWeight(targetStatus)) {
                continue;
            }

            validateStatusTransition(subOrder.getStatus(), targetStatus);

            if (targetStatus == OrderStatus.SHIPPING) {
                orderInventoryReservationService.shipReservedInventory(
                        buildSubOrderReferenceCode(subOrder),
                        "Xuat kho cho phan don " + order.getCode() + " khi chuyen sang giao hang");
            }

            applySubOrderStatus(subOrder, targetStatus, changedAt);
            subOrdersToUpdate.add(subOrder);
        }

        if (!subOrdersToUpdate.isEmpty()) {
            subOrderRepository.saveAll(subOrdersToUpdate);
        }
    }

    public List<Order> getOrdersByUserId(Long userId) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    // QUáº¢N LĂ ÄÆ N HĂ€NG CHO CHI NHĂNH / KHO
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

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
                        .collect(Collectors.toList()));

        return filteredSubOrders.stream().map(this::mapSubOrderToBranchOrderResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public BranchOrderResponse getBranchOrderDetail(Long branchId, Long orderId) {
        SubOrder subOrder = subOrderRepository.findByOrderIdAndBranchId(orderId, branchId)
                .orElseThrow(() -> new NotFoundException("KhĂ´ng tĂ¬m tháº¥y Ä‘Æ¡n hĂ ng cho chi nhĂ¡nh nĂ y"));
        syncPayOSPaymentStatus(subOrder.getOrder());
        return mapSubOrderToBranchOrderResponse(subOrder);
    }

    @Transactional(readOnly = true)
    public List<MissingItemReportDto> getBackorderReport() {
        return subOrderItemRepository.getBackorderReport().stream()
                .map(row -> MissingItemReportDto.builder()
                        .productVariantId(row.getProductVariantId())
                        .sku(row.getSku())
                        .productName(row.getProductName())
                        .variantName(row.getVariantName())
                        .totalMissingQuantity(
                                row.getTotalMissingQuantity() != null ? row.getTotalMissingQuantity().intValue() : 0)
                        .affectedSubOrders(row.getAffectedSubOrders())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional
    public List<String> requestReplenishmentForAdmin(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("KhĂ´ng tĂ¬m tháº¥y Ä‘Æ¡n hĂ ng ID: " + orderId));

        List<SubOrder> awaitingSubOrders = order.getSubOrders() == null
                ? Collections.emptyList()
                : order.getSubOrders().stream()
                        .filter(this::canRequestReplenishment)
                        .toList();

        if (awaitingSubOrders.isEmpty()) {
            throw new BadRequestException("ÄÆ¡n hĂ ng nĂ y khĂ´ng á»Ÿ tráº¡ng thĂ¡i chá» Ä‘iá»u chuyá»ƒn bá»• sung.");
        }

        return awaitingSubOrders.stream()
                .flatMap(
                        subOrder -> inventoryTransferService.createReplenishmentTransfersForSubOrder(subOrder).stream())
                .map(InventoryTransfer::getTransferCode)
                .toList();
    }

    @Transactional
    public List<String> requestReplenishmentForBranch(Long branchId, Long orderId) {
        SubOrder subOrder = subOrderRepository.findByOrderIdAndBranchId(orderId, branchId)
                .orElseThrow(() -> new NotFoundException("KhĂ´ng tĂ¬m tháº¥y pháº§n Ä‘Æ¡n cho chi nhĂ¡nh nĂ y"));

        if (!canRequestReplenishment(subOrder)) {
            throw new BadRequestException("Pháº§n Ä‘Æ¡n nĂ y khĂ´ng á»Ÿ tráº¡ng thĂ¡i chá» Ä‘iá»u chuyá»ƒn bá»• sung.");
        }

        return inventoryTransferService.createReplenishmentTransfersForSubOrder(subOrder).stream()
                .map(InventoryTransfer::getTransferCode)
                .toList();
    }

    @Transactional
    public void updateSubOrderStatus(Long branchId, Long orderId, OrderStatus newStatus) {
        SubOrder subOrder = subOrderRepository.findByOrderIdAndBranchId(orderId, branchId)
                .orElseThrow(() -> new NotFoundException("KhĂ´ng tĂ¬m tháº¥y pháº§n Ä‘Æ¡n cho chi nhĂ¡nh nĂ y"));

        OrderStatus currentStatus = subOrder.getStatus();
        if (currentStatus == OrderStatus.CANCELLED || currentStatus == OrderStatus.COMPLETED
                || currentStatus == OrderStatus.RETURNED) {
            throw new BadRequestException("KhĂ´ng thá»ƒ thay Ä‘á»•i tráº¡ng thĂ¡i cá»§a Ä‘Æ¡n Ä‘Ă£ Ä‘Ă³ng!");
        }
        if (currentStatus == OrderStatus.SHIPPING && newStatus == OrderStatus.COMPLETED) {
            throw new BadRequestException("Pháº§n Ä‘Æ¡n Ä‘ang giao chá»‰ Ä‘Æ°á»£c hoĂ n táº¥t khi khĂ¡ch hĂ ng xĂ¡c nháº­n Ä‘Ă£ nháº­n.");
        }
        validateStatusTransition(currentStatus, newStatus);

        if (newStatus == OrderStatus.CANCELLED) {
            releaseAllocatedInventoryForSubOrder(subOrder);
        }
        if (newStatus == OrderStatus.SHIPPING) {
            orderInventoryReservationService.shipReservedInventory(
                    buildSubOrderReferenceCode(subOrder),
                    "Xuat kho cho phan don " + subOrder.getOrder().getCode() + " khi chuyen sang giao hang");
        }
        if (newStatus == OrderStatus.RECEIVED
                && !canManuallyConfirmReceived(resolveStatusUpdatedAt(subOrder))) {
            throw new BadRequestException("ChĂ¡Â»â€° Ă„â€˜Ă†Â°Ă¡Â»Â£c xĂƒÂ¡c nhĂ¡ÂºÂ­n 'Ă„ÂĂƒÂ£ nhĂ¡ÂºÂ­n hĂƒÂ ng' sau khi Ă„â€˜Ă†Â¡n Ă„â€˜ang giao quĂƒÂ¡ 7 ngĂƒÂ y.");
        }
        if (newStatus == OrderStatus.RECEIVED && PaymentMethod.COD.equals(subOrder.getOrder().getPaymentMethod())) {
            subOrder.getOrder().setPaymentStatus(PaymentStatus.PAID);
            orderRepository.save(subOrder.getOrder());
        }

        applySubOrderStatus(subOrder, newStatus, LocalDateTime.now());
        subOrderRepository.saveAndFlush(subOrder);

        orderStatusSyncService.syncMasterOrderStatus(subOrder.getOrder().getId());
    }

    private void syncMasterOrderStatus(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("KhĂ´ng tĂ¬m tháº¥y Ä‘Æ¡n hĂ ng tá»•ng"));

        List<SubOrder> allSubs = subOrderRepository.findByOrderId(orderId);
        if (allSubs.isEmpty())
            return;

        List<SubOrder> activeSubs = allSubs.stream()
                .filter(s -> s.getStatus() != OrderStatus.CANCELLED && s.getStatus() != OrderStatus.RETURNED)
                .collect(Collectors.toList());
        List<SubOrder> nonCancelledSubs = allSubs.stream()
                .filter(s -> s.getStatus() != OrderStatus.CANCELLED)
                .toList();

        OrderStatus newMasterStatus;
        if (!nonCancelledSubs.isEmpty() && nonCancelledSubs.stream().allMatch(s -> s.getStatus() == OrderStatus.RETURNED)) {
            newMasterStatus = OrderStatus.RETURNED;
        } else if (activeSubs.isEmpty()) {
            newMasterStatus = OrderStatus.CANCELLED;
        } else if (activeSubs.stream().allMatch(s -> s.getStatus() == OrderStatus.COMPLETED)) {
            newMasterStatus = OrderStatus.COMPLETED;
            order.setPaymentStatus(PaymentStatus.PAID);
        } else if (activeSubs.stream().allMatch(
                s -> s.getStatus() == OrderStatus.RECEIVED || s.getStatus() == OrderStatus.COMPLETED)) {
            newMasterStatus = OrderStatus.RECEIVED;
            order.setPaymentStatus(PaymentStatus.PAID);
        } else {
            newMasterStatus = activeSubs.stream()
                    .map(SubOrder::getStatus)
                    .min(Comparator.comparingInt(this::statusWeight))
                    .orElse(OrderStatus.PENDING);
        }

        // Fix lá»—i: Khi táº¥t cáº£ SubOrder bá»‹ há»§y, Master Order chuyá»ƒn thĂ nh Há»§y -> HoĂ n
        // Voucher vĂ  Há»§y link PayOS
        if (newMasterStatus == OrderStatus.CANCELLED && order.getStatus() != OrderStatus.CANCELLED) {
            voucherService.restoreVoucherForOrder(order);
            if (PaymentMethod.PAYOS.equals(order.getPaymentMethod())
                    && PaymentStatus.UNPAID.equals(order.getPaymentStatus())) {
                payOSService.cancelPaymentLink(order);
            }
        }

        applyOrderStatus(order, newMasterStatus, LocalDateTime.now());
        orderRepository.saveAndFlush(order);

        if (newMasterStatus == OrderStatus.COMPLETED || newMasterStatus == OrderStatus.CANCELLED
                || newMasterStatus == OrderStatus.RETURNED) {
            customerService.evaluateAndHandleCustomerReputation(order.getUser().getId());
        }
    }

    private boolean canCustomerCancelOrder(OrderStatus status) {
        return status == OrderStatus.PENDING || status == OrderStatus.AWAITING_PAYMENT;
    }

    @Transactional
    public void expireUnpaidPaymentOrders() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(Math.max(1, paymentExpiryMinutes));
        List<Order> overdueOrders = orderRepository.findByStatusAndCreatedAtBefore(OrderStatus.AWAITING_PAYMENT, cutoff)
                .stream()
                .filter(order -> PaymentMethod.PAYOS.equals(order.getPaymentMethod()))
                .filter(this::isPendingPayosPayment)
                .toList();

        for (Order order : overdueOrders) {
            if (payOSService.checkPaymentStatus(order)) {
                payOSService.markOrderPaid(order);
                continue;
            }

            releaseAllocatedInventoryForOrder(order);
            voucherService.restoreVoucherForOrder(order);
            payOSService.cancelPaymentLink(order);

            LocalDateTime cancelledAt = LocalDateTime.now();
            applyOrderStatus(order, OrderStatus.CANCELLED, cancelledAt);
            if (order.getSubOrders() != null) {
                List<SubOrder> cancelledSubOrders = order.getSubOrders().stream()
                        .filter(subOrder -> subOrder.getStatus() != OrderStatus.CANCELLED)
                        .peek(subOrder -> applySubOrderStatus(subOrder, OrderStatus.CANCELLED, cancelledAt))
                        .toList();
                if (!cancelledSubOrders.isEmpty()) {
                    subOrderRepository.saveAll(cancelledSubOrders);
                }
            }

            String previousNote = order.getNote() != null ? order.getNote() + " | " : "";
            order.setNote(previousNote + "Payment expired after " + paymentExpiryMinutes + " minutes");
            order.setPaymentStatus(PaymentStatus.EXPIRED);
            order.setAutoApproveAt(null);
            orderRepository.save(order);

            if (order.getUser() != null) {
                customerService.evaluateAndHandleCustomerReputation(order.getUser().getId());
            }
        }
    }

    @Transactional
    public void autoApproveEligibleOrders() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime fallbackCutoff = now.minusMinutes(Math.max(1, autoApproveMinutes));

        List<Order> eligibleOrders = new ArrayList<>(orderRepository.findOrdersReadyForAutoApproval(now));
        List<Order> pendingFallbackOrders = orderRepository.findByStatusAndCreatedAtBefore(OrderStatus.PENDING, fallbackCutoff)
                .stream()
                .filter(order -> order.getAutoApproveAt() == null)
                .toList();

        Map<Long, Order> candidatesById = new LinkedHashMap<>();
        for (Order order : eligibleOrders) {
            if (order.getId() != null) {
                candidatesById.put(order.getId(), order);
            }
        }
        for (Order order : pendingFallbackOrders) {
            if (order.getId() != null) {
                candidatesById.putIfAbsent(order.getId(), order);
            }
        }

        for (Order order : candidatesById.values()) {
            if (!isEligibleForAutoApproval(order, now) && !shouldFallbackAutoApprove(order, now)) {
                continue;
            }

            confirmOrderAutomatically(order, now);
        }
    }

    @Transactional
    public void autoCompleteDeliveredOrders() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(7);

        List<SubOrder> overdueSubOrders = subOrderRepository.findByStatusAndUpdatedAtBefore(OrderStatus.SHIPPING,
                cutoff);
        List<Order> overdueLegacyOrders = orderRepository.findByStatusAndCreatedAtBefore(OrderStatus.SHIPPING, cutoff)
                .stream()
                .filter(order -> order.getSubOrders() == null || order.getSubOrders().isEmpty())
                .toList();

        if (!overdueSubOrders.isEmpty() || !overdueLegacyOrders.isEmpty()) {
            log.warn("CĂƒÂ³ {} phĂ¡ÂºÂ§n Ă„â€˜Ă†Â¡n vĂƒÂ  {} Ă„â€˜Ă†Â¡n legacy Ă„â€˜ang SHIPPING quĂƒÂ¡ {} ngĂƒÂ y, cĂ¡ÂºÂ§n xĂƒÂ¡c nhĂ¡ÂºÂ­n RECEIVED thĂ¡Â»Â§ cĂƒÂ´ng.",
                    overdueSubOrders.size(),
                    overdueLegacyOrders.size(),
                    SHIPPING_RECEIVED_CONFIRM_AFTER_DAYS);
        }
    }

    private void markOrderAsReceived(Order order, boolean force) {
        if (!force && !canManuallyConfirmReceived(resolveStatusUpdatedAt(order))) {
            throw new BadRequestException("ChĂ¡Â»â€° Ă„â€˜Ă†Â°Ă¡Â»Â£c xĂƒÂ¡c nhĂ¡ÂºÂ­n 'Ă„ÂĂƒÂ£ nhĂ¡ÂºÂ­n hĂƒÂ ng' sau khi Ă„â€˜Ă†Â¡n Ă„â€˜ang giao quĂƒÂ¡ 7 ngĂƒÂ y.");
        }

        LocalDateTime receivedAt = LocalDateTime.now();
        applyOrderStatus(order, OrderStatus.RECEIVED, receivedAt);
        if (PaymentMethod.COD.equals(order.getPaymentMethod())) {
            order.setPaymentStatus(PaymentStatus.PAID);
        }
        orderRepository.save(order);

        if (order.getSubOrders() != null) {
            List<SubOrder> shippingSubOrders = order.getSubOrders().stream()
                    .filter(subOrder -> subOrder.getStatus() == OrderStatus.SHIPPING)
                    .peek(subOrder -> applySubOrderStatus(subOrder, OrderStatus.RECEIVED, receivedAt))
                    .toList();
            if (!shippingSubOrders.isEmpty()) {
                subOrderRepository.saveAll(shippingSubOrders);
            }
        }
    }

    private void completeReceivedOrder(Order order) {
        LocalDateTime completedAt = LocalDateTime.now();
        applyOrderStatus(order, OrderStatus.COMPLETED, completedAt);
        order.setPaymentStatus(PaymentStatus.PAID);
        orderRepository.save(order);

        if (order.getSubOrders() != null) {
            List<SubOrder> receivedSubOrders = order.getSubOrders().stream()
                    .filter(subOrder -> subOrder.getStatus() == OrderStatus.RECEIVED)
                    .peek(subOrder -> applySubOrderStatus(subOrder, OrderStatus.COMPLETED, completedAt))
                    .toList();
            if (!receivedSubOrders.isEmpty()) {
                subOrderRepository.saveAll(receivedSubOrders);
            }
        }
    }

    private int statusWeight(OrderStatus s) {
        if (s == OrderStatus.AWAITING_PAYMENT)
            return 0;
        if (s == OrderStatus.AWAITING_REPLENISHMENT)
            return 1;
        if (s == OrderStatus.PENDING)
            return 2;
        return switch (s) {
            case PENDING -> 0;
            case AWAITING_PAYMENT -> 1;
            case AWAITING_REPLENISHMENT -> 2;
            case CONFIRMED -> 3;
            case PROCESSING -> 4;
            case READY_FOR_PICKUP -> 5;
            case SHIPPING -> 6;
            case RECEIVED -> 7;
            case COMPLETED -> 8;
            default -> 9;
        };
    }

    private boolean canManuallyConfirmReceived(LocalDateTime statusUpdatedAt) {
        return statusUpdatedAt != null
                && !statusUpdatedAt.isAfter(LocalDateTime.now().minusDays(SHIPPING_RECEIVED_CONFIRM_AFTER_DAYS));
    }

    private Long calculateOverdueShippingDays(LocalDateTime statusUpdatedAt) {
        if (statusUpdatedAt == null) {
            return 0L;
        }
        return Math.max(0, ChronoUnit.DAYS.between(statusUpdatedAt, LocalDateTime.now()));
    }

    private LocalDateTime resolveStatusUpdatedAt(Order order) {
        if (order.getSubOrders() != null && !order.getSubOrders().isEmpty()) {
            return order.getSubOrders().stream()
                    .map(this::resolveStatusUpdatedAt)
                    .filter(Objects::nonNull)
                    .max(LocalDateTime::compareTo)
                    .orElse(order.getCreatedAt());
        }
        return order.getCreatedAt();
    }

    private LocalDateTime resolveStatusUpdatedAt(SubOrder subOrder) {
        return subOrder.getUpdatedAt() != null ? subOrder.getUpdatedAt() : subOrder.getCreatedAt();
    }

    private void applyOrderStatus(Order order, OrderStatus status, LocalDateTime changedAt) {
        order.setStatus(status);
        if (status != OrderStatus.PENDING) {
            order.setAutoApproveAt(null);
        }
        switch (status) {
            case PROCESSING -> order.setFulfillmentStatus(FulfillmentStatus.PREPARING);
            case READY_FOR_PICKUP -> order.setFulfillmentStatus(FulfillmentStatus.READY_TO_SHIP);
            case SHIPPING -> order.setFulfillmentStatus(FulfillmentStatus.SHIPPING);
            case RECEIVED, COMPLETED -> order.setFulfillmentStatus(FulfillmentStatus.DELIVERED);
            case RETURNED -> order.setFulfillmentStatus(FulfillmentStatus.RETURNED);
            default -> {
                if (order.getFulfillmentStatus() == null
                        || status == OrderStatus.PENDING
                        || status == OrderStatus.AWAITING_PAYMENT
                        || status == OrderStatus.AWAITING_REPLENISHMENT
                        || status == OrderStatus.CONFIRMED
                        || status == OrderStatus.CANCELLED) {
                    order.setFulfillmentStatus(FulfillmentStatus.NOT_STARTED);
                }
            }
        }
        if (status == OrderStatus.RECEIVED && order.getReceivedAt() == null) {
            order.setReceivedAt(changedAt);
        }
        if (status == OrderStatus.COMPLETED) {
            if (order.getReceivedAt() == null) {
                order.setReceivedAt(changedAt);
            }
            if (order.getCompletedAt() == null) {
                order.setCompletedAt(changedAt);
            }
        }
        if (status == OrderStatus.RETURNED && order.getReturnedAt() == null) {
            order.setReturnedAt(changedAt);
        }
        if (status == OrderStatus.CANCELLED && order.getCancelledAt() == null) {
            order.setCancelledAt(changedAt);
        }
    }

    private void applySubOrderStatus(SubOrder subOrder, OrderStatus status, LocalDateTime changedAt) {
        subOrder.setStatus(status);
        if (status == OrderStatus.RECEIVED && subOrder.getReceivedAt() == null) {
            subOrder.setReceivedAt(changedAt);
        }
        if (status == OrderStatus.COMPLETED) {
            if (subOrder.getReceivedAt() == null) {
                subOrder.setReceivedAt(changedAt);
            }
            if (subOrder.getCompletedAt() == null) {
                subOrder.setCompletedAt(changedAt);
            }
        }
        if (status == OrderStatus.RETURNED && subOrder.getReturnedAt() == null) {
            subOrder.setReturnedAt(changedAt);
        }
        if (status == OrderStatus.CANCELLED && subOrder.getCancelledAt() == null) {
            subOrder.setCancelledAt(changedAt);
        }
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
                || !isPendingPayosPayment(order)) {
            return;
        }

        if (payOSService.checkPaymentStatus(order)) {
            payOSService.markOrderPaid(order);
            return;
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
                .shippingAddress(
                        order.getShippingAddress() != null ? order.getShippingAddress() : order.getDeliveryAddress())
                .createdAt(order.getCreatedAt())
                .paymentMethod(order.getPaymentMethod() != null ? order.getPaymentMethod().name() : "")
                .paymentStatus(resolvePaymentStatus(order))
                .orderStatus(resolveWorkflowStatus(order))
                .orderLegacyStatus(order.getStatus() != null ? order.getStatus().name() : "")
                .fulfillmentStatus(resolveFulfillmentStatus(order))
                .stockStatus(resolveStockStatus(order))
                .autoApproveAt(order.getAutoApproveAt())
                .subOrderId(subOrder.getId())
                .subOrderStatus(subOrder.getStatus() != null ? subOrder.getStatus().name() : "")
                .subtotal(subOrder.getSubtotal() != null ? subOrder.getSubtotal() : BigDecimal.ZERO)
                .shippingFee(subOrder.getShippingFee() != null ? subOrder.getShippingFee() : BigDecimal.ZERO)
                .estimatedDays(subOrder.getEstimatedDays())
                .carrier(subOrder.getCarrier())
                .statusUpdatedAt(resolveStatusUpdatedAt(subOrder))
                .shippingOverdue(subOrder.getStatus() == OrderStatus.SHIPPING
                        && canManuallyConfirmReceived(resolveStatusUpdatedAt(subOrder)))
                .canMarkReceived(subOrder.getStatus() == OrderStatus.SHIPPING
                        && canManuallyConfirmReceived(resolveStatusUpdatedAt(subOrder)))
                .overdueShippingDays(calculateOverdueShippingDays(resolveStatusUpdatedAt(subOrder)))
                .items(items)
                .build();
    }

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    // MAPPING LOGIC
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

    private OrderResponse mapToOrderResponse(Order order, boolean isUserView) {
        List<OrderItemResponse> itemResponses = new ArrayList<>();
        List<SubOrderSummaryDto> subOrderSummaries = order.getSubOrders() != null
                ? order.getSubOrders().stream()
                        .map(sub -> mapSubOrderToSummary(sub, isUserView))
                        .collect(Collectors.toList())
                : Collections.emptyList();

        if (order.getOrderItems() != null && !order.getOrderItems().isEmpty()) {
            itemResponses = order.getOrderItems().stream().map(this::mapItemToResponse).collect(Collectors.toList());
        } else if (order.getSubOrders() != null && !order.getSubOrders().isEmpty()) {
            itemResponses = order.getSubOrders().stream()
                    .filter(sub -> sub.getItems() != null)
                    .flatMap(sub -> sub.getItems().stream())
                    .map(item -> mapSubItemToResponse(item, !isUserView))
                    .collect(Collectors.toList());
        }

        String branchName;
        String branchPhone = null;
        String branchAddress = null;

        if (order.getBranch() != null) {
            branchName = order.getBranch().getName();
            branchPhone = order.getBranch().getPhone();
            branchAddress = order.getBranch().getAddressDetail();
        } else if (order.getSubOrders() != null && order.getSubOrders().size() == 1) {
            Branch singleBranch = order.getSubOrders().get(0).getBranch();
            branchName = singleBranch != null ? singleBranch.getName() : "Nhiá»u chi nhĂ¡nh";
            branchPhone = singleBranch != null ? singleBranch.getPhone() : null;
            branchAddress = singleBranch != null ? singleBranch.getAddressDetail() : null;
        } else if (order.getSubOrders() != null && order.getSubOrders().size() > 1) {
            branchName = "Nhiá»u chi nhĂ¡nh";
        } else {
            branchName = "KhĂ´ng xĂ¡c Ä‘á»‹nh";
        }

        String statusStr = isUserView ? resolveUserWorkflowStatus(order) : resolveWorkflowStatus(order);
        String legacyStatusStr = order.getStatus() != null ? order.getStatus().name() : "";

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
                .voucherCode(order.getVoucher() != null ? order.getVoucher().getCode() : null)
                .discountAmount(order.getDiscountAmount() != null ? order.getDiscountAmount() : BigDecimal.ZERO)
                .finalAmount(order.getFinalAmount() != null ? order.getFinalAmount() : BigDecimal.ZERO)
                .paymentMethod(order.getPaymentMethod() != null ? order.getPaymentMethod().name() : "")
                .paymentStatus(resolvePaymentStatus(order))
                .status(statusStr)
                .legacyStatus(legacyStatusStr)
                .fulfillmentStatus(resolveFulfillmentStatus(order))
                .stockStatus(isUserView ? null : resolveStockStatus(order))
                .autoApproveAt(isUserView ? null : order.getAutoApproveAt())
                .autoApprovalPaused(isUserView ? null : Boolean.TRUE.equals(order.getAutoApprovalPaused()))
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

    private SubOrderSummaryDto mapSubOrderToSummary(SubOrder subOrder, boolean isUserView) {
        String statusStr = subOrder.getStatus() == null
                ? null
                : (isUserView ? resolveUserWorkflowStatus(subOrder.getStatus()) : subOrder.getStatus().name());

        return SubOrderSummaryDto.builder()
                .subOrderId(subOrder.getId())
                .branchId(subOrder.getBranch() != null ? subOrder.getBranch().getId() : null)
                .branchName(subOrder.getBranch() != null ? subOrder.getBranch().getName() : null)
                .status(statusStr)
                .subtotal(subOrder.getSubtotal() != null ? subOrder.getSubtotal() : BigDecimal.ZERO)
                .shippingFee(subOrder.getShippingFee() != null ? subOrder.getShippingFee() : BigDecimal.ZERO)
                .estimatedDays(subOrder.getEstimatedDays())
                .carrier(subOrder.getCarrier())
                .carrierOrderId(subOrder.getCarrierOrderId())
                .build();
    }

    private boolean matchesWorkflowStatusFilter(Order order, String status) {
        if (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)) {
            return true;
        }

        String normalized = status.trim().toUpperCase(Locale.ROOT);
        String workflowStatus = resolveWorkflowStatus(order);
        String legacyStatus = order != null && order.getStatus() != null ? order.getStatus().name() : "";

        return normalized.equals(workflowStatus) || normalized.equals(legacyStatus);
    }

    private String resolveWorkflowStatus(Order order) {
        if (order == null || order.getStatus() == null) {
            return "";
        }

        return switch (order.getStatus()) {
            case AWAITING_PAYMENT -> "PENDING_PAYMENT";
            case AWAITING_REPLENISHMENT -> hasActiveTransferRequest(order)
                    ? "PENDING_TRANSFER"
                    : "PENDING_SHORTAGE_REVIEW";
            case PENDING -> order.getAutoApproveAt() != null
                    ? "PENDING_AUTO_APPROVAL"
                    : "PENDING";
            default -> order.getStatus().name();
        };
    }

    private String resolveUserWorkflowStatus(Order order) {
        if (order == null || order.getStatus() == null) {
            return "";
        }

        if (order.getStatus() == OrderStatus.AWAITING_PAYMENT) {
            return "PENDING_PAYMENT";
        }

        if (order.getStatus() == OrderStatus.PENDING) {
            return "PENDING";
        }

        if (order.getStatus() == OrderStatus.AWAITING_REPLENISHMENT) {
            return OrderStatus.PROCESSING.name();
        }

        return resolveUserWorkflowStatus(order.getStatus());
    }

    private String resolveUserWorkflowStatus(OrderStatus status) {
        if (status == null) {
            return "";
        }

        return switch (status) {
            case AWAITING_PAYMENT -> "PENDING_PAYMENT";
            case AWAITING_REPLENISHMENT, CONFIRMED, PROCESSING, READY_FOR_PICKUP -> OrderStatus.PROCESSING.name();
            case PENDING -> OrderStatus.PENDING.name();
            default -> status.name();
        };
    }

    private String resolvePaymentStatus(Order order) {
        if (order == null || order.getPaymentStatus() == null) {
            return "";
        }

        if (PaymentMethod.PAYOS.equals(order.getPaymentMethod())
                && order.getStatus() == OrderStatus.AWAITING_PAYMENT
                && PaymentStatus.UNPAID.equals(order.getPaymentStatus())) {
            return PaymentStatus.PENDING.name();
        }

        return order.getPaymentStatus().name();
    }

    private String resolveStockStatus(Order order) {
        if (order == null) {
            return "";
        }

        boolean hasMissingItems = order.getSubOrders() != null
                && order.getSubOrders().stream()
                        .filter(Objects::nonNull)
                        .flatMap(subOrder -> subOrder.getItems() == null ? Stream.empty() : subOrder.getItems().stream())
                        .anyMatch(item -> Objects.requireNonNullElse(item.getMissingQuantity(), 0) > 0);

        if (!hasMissingItems) {
            return StockStatus.FULLY_AVAILABLE.name();
        }

        if (order.getStockStatus() == StockStatus.OUT_OF_STOCK
                || order.getStockStatus() == StockStatus.AVAILABLE_AFTER_TRANSFER) {
            return order.getStockStatus().name();
        }

        return StockStatus.PARTIALLY_AVAILABLE.name();
    }

    private String resolveFulfillmentStatus(Order order) {
        if (order == null) {
            return "";
        }

        if (order.getFulfillmentStatus() != null) {
            return order.getFulfillmentStatus().name();
        }

        if (order.getStatus() == null) {
            return FulfillmentStatus.NOT_STARTED.name();
        }

        return switch (order.getStatus()) {
            case PROCESSING -> FulfillmentStatus.PREPARING.name();
            case READY_FOR_PICKUP -> FulfillmentStatus.READY_TO_SHIP.name();
            case SHIPPING -> FulfillmentStatus.SHIPPING.name();
            case RECEIVED, COMPLETED -> FulfillmentStatus.DELIVERED.name();
            case RETURNED -> FulfillmentStatus.RETURNED.name();
            default -> FulfillmentStatus.NOT_STARTED.name();
        };
    }

    private boolean hasActiveTransferRequest(Order order) {
        if (order == null || order.getSubOrders() == null || order.getSubOrders().isEmpty()) {
            return false;
        }

        return order.getSubOrders().stream()
                .filter(Objects::nonNull)
                .anyMatch(subOrder -> inventoryTransferRepository.existsByReferenceCodeAndStatusIn(
                        buildSubOrderReferenceCode(subOrder),
                        List.of(
                                InventoryTransferStatus.PENDING,
                                InventoryTransferStatus.SOURCE_CONFIRMED,
                                InventoryTransferStatus.APPROVED,
                                InventoryTransferStatus.SHIPPING,
                                InventoryTransferStatus.INSPECTING)));
    }

    private boolean isPendingPayosPayment(Order order) {
        if (order == null || !PaymentMethod.PAYOS.equals(order.getPaymentMethod())) {
            return false;
        }

        return PaymentStatus.PENDING.equals(order.getPaymentStatus())
                || PaymentStatus.UNPAID.equals(order.getPaymentStatus());
    }

    private boolean isEligibleForAutoApproval(Order order, LocalDateTime now) {
        if (!isEligibleForAutoApprovalBase(order)) {
            return false;
        }

        if (order.getAutoApproveAt() == null || order.getAutoApproveAt().isAfter(now)) {
            return false;
        }

        return true;
    }

    private boolean shouldFallbackAutoApprove(Order order, LocalDateTime now) {
        if (!isEligibleForAutoApprovalBase(order)) {
            return false;
        }

        if (order.getAutoApproveAt() != null) {
            return false;
        }

        if (!PaymentMethod.COD.equals(order.getPaymentMethod()) && !PaymentMethod.CASH.equals(order.getPaymentMethod())) {
            return false;
        }

        if (order.getCreatedAt() == null) {
            return false;
        }

        LocalDateTime fallbackReadyAt = order.getCreatedAt().plusMinutes(Math.max(1, autoApproveMinutes));
        return !fallbackReadyAt.isAfter(now);
    }

    private boolean isEligibleForAutoApprovalBase(Order order) {
        if (order == null || order.getStatus() != OrderStatus.PENDING) {
            return false;
        }

        if (Boolean.TRUE.equals(order.getAutoApprovalPaused())) {
            return false;
        }

        String stockStatus = resolveStockStatus(order);
        if (!StockStatus.FULLY_AVAILABLE.name().equals(stockStatus)) {
            return false;
        }

        if (PaymentMethod.PAYOS.equals(order.getPaymentMethod()) || PaymentMethod.TRANSFER.equals(order.getPaymentMethod())) {
            return PaymentStatus.PAID.equals(order.getPaymentStatus());
        }

        return PaymentStatus.UNPAID.equals(order.getPaymentStatus()) || PaymentStatus.PAID.equals(order.getPaymentStatus());
    }

    private void confirmOrderAutomatically(Order order, LocalDateTime changedAt) {
        applyOrderStatus(order, OrderStatus.CONFIRMED, changedAt);
        order.setFulfillmentStatus(FulfillmentStatus.PREPARING);
        order.setAutoApproveAt(null);

        if (order.getSubOrders() != null) {
            List<SubOrder> confirmedSubOrders = order.getSubOrders().stream()
                    .filter(subOrder -> subOrder.getStatus() == OrderStatus.PENDING)
                    .peek(subOrder -> applySubOrderStatus(subOrder, OrderStatus.CONFIRMED, changedAt))
                    .toList();
            if (!confirmedSubOrders.isEmpty()) {
                subOrderRepository.saveAll(confirmedSubOrders);
            }
        }

        orderRepository.save(order);
    }

    private OrderItemResponse mapItemToResponse(OrderItem item) {
        String pName = "Sáº£n pháº©m khĂ´ng xĂ¡c Ä‘á»‹nh";
        String pSku = "N/A";
        String pImg = null;
        Long productId = null;

        if (item.getProductVariant() != null) {
            pSku = item.getProductVariant().getSku();
            pImg = resolveOrderItemImage(item.getProductVariant());
            if (item.getProductVariant().getProduct() != null) {
                productId = item.getProductVariant().getProduct().getId();
                pName = item.getProductVariant().getProduct().getName();
            }
        }

        boolean canReview = false;
        if (item.getOrder() != null && item.getOrder().getStatus() == OrderStatus.COMPLETED && productId != null) {
            Long userId = item.getOrder().getUser().getId();
            canReview = !reviewRepository.existsByOrderIdAndProductIdAndUserId(item.getOrder().getId(), productId,
                    userId);
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
                .totalPrice(item.getPrice() != null ? item.getPrice().multiply(new BigDecimal(item.getQuantity()))
                        : BigDecimal.ZERO)
                .canReview(canReview)
                .build();
    }

    private OrderItemResponse mapSubItemToResponse(SubOrderItem item) {
        return mapSubItemToResponse(item, true);
    }

    private OrderItemResponse mapSubItemToResponse(SubOrderItem item, boolean exposeInternalDetails) {
        String pName = "Sáº£n pháº©m khĂ´ng xĂ¡c Ä‘á»‹nh";
        String pSku = "N/A";
        String pImg = null;
        Long productId = null;

        if (item.getProductVariant() != null) {
            pSku = item.getProductVariant().getSku();
            pImg = resolveOrderItemImage(item.getProductVariant());
            if (item.getProductVariant().getProduct() != null) {
                productId = item.getProductVariant().getProduct().getId();
                pName = item.getProductVariant().getProduct().getName();
            }
        }

        boolean canReview = false;
        if (item.getSubOrder() != null && item.getSubOrder().getStatus() == OrderStatus.COMPLETED
                && productId != null) {
            Long userId = item.getSubOrder().getOrder().getUser().getId();
            canReview = !reviewRepository.existsByOrderIdAndProductIdAndUserId(item.getSubOrder().getOrder().getId(),
                    productId, userId);
        }

        return OrderItemResponse.builder()
                .id(item.getId())
                .productId(productId)
                .productName(pName)
                .sku(pSku)
                .image(pImg)
                .quantity(item.getQuantity())
                .allocatedQuantity(
                        exposeInternalDetails && item.getAllocatedQuantity() != null
                                ? item.getAllocatedQuantity()
                                : item.getQuantity())
                .missingQuantity(
                        exposeInternalDetails && item.getMissingQuantity() != null
                                ? item.getMissingQuantity()
                                : 0)
                .price(item.getUnitPrice() != null ? item.getUnitPrice() : BigDecimal.ZERO)
                .totalPrice(
                        item.getUnitPrice() != null ? item.getUnitPrice().multiply(new BigDecimal(item.getQuantity()))
                                : BigDecimal.ZERO)
                .canReview(canReview)
                .build();
    }

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    // PREPARE & CONFIRM LOGIC (THUáº¬T TOĂN FIFO + LĂ” HĂ€NG)
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

    private String resolveOrderItemImage(ProductVariant productVariant) {
        if (productVariant == null) {
            return null;
        }

        if (productVariant.getImageUrl() != null && !productVariant.getImageUrl().isBlank()) {
            return productVariant.getImageUrl();
        }

        Product product = productVariant.getProduct();
        if (product == null || product.getProductImages() == null || product.getProductImages().isEmpty()) {
            return null;
        }

        return product.getProductImages().stream()
                .map(ProductImage::getImageUrl)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(imageUrl -> !imageUrl.isEmpty())
                .findFirst()
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public PrepareOrderResponse prepareOrder(Long userId, PrepareOrderRequest request) {
        com.zone.agri.entity.UserAddress addr = userAddressRepository
                .findByIdAndUserId(request.getUserAddressId(), userId)
                .orElseThrow(() -> new NotFoundException(
                        "KhĂ´ng tĂ¬m tháº¥y Ä‘á»‹a chá»‰ trong sá»• Ä‘á»‹a chá»‰ cá»§a báº¡n. ID: " + request.getUserAddressId()));

        String receiverName = addr.getReceiverName();
        String receiverPhone = addr.getReceiverPhone();
        String deliveryAddress = addr.getAddressDetail();
        Integer deliveryDistrictId = addr.getDistrictId() != null ? parseIntSafe(addr.getDistrictId()) : null;
        Integer deliveryProvinceId = addr.getProvinceId() != null ? parseIntSafe(addr.getProvinceId()) : null;
        String deliveryWardCode = normalizeWardCode(addr.getWardId());
        Double userLat = request.getUserLat();
        Double userLng = request.getUserLng();

        List<CartItemDto> finalCart = request.getCart();
        if (finalCart == null || finalCart.isEmpty()) {
            List<com.zone.agri.entity.CartItem> dbItems = cartItemRepository.findByUserId(userId);
            if (dbItems.isEmpty())
                throw new BadRequestException("Giá» hĂ ng cá»§a báº¡n Ä‘ang trá»‘ng");

            finalCart = dbItems.stream()
                    .map(item -> new CartItemDto(item.getProductVariant().getId(),
                            item.getQuantity()))
                    .collect(Collectors.toList());
        }

        finalCart = normalizeCartItems(finalCart);
        String voucherCode = voucherService.normalizeVoucherCode(request.getVoucherCode());

        List<Long> variantIds = finalCart.stream().map(CartItemDto::getProductVariantId).distinct().toList();
        List<ProductVariant> variants = variantRepository.findAllById(variantIds);
        if (variants.size() != variantIds.size())
            throw new NotFoundException("Má»™t hoáº·c nhiá»u sáº£n pháº©m khĂ´ng tá»“n táº¡i");

        Map<Long, ProductVariant> variantMap = variants.stream()
                .collect(Collectors.toMap(ProductVariant::getId, Function.identity()));

        List<BranchWithRealDistance> nearestBranches = requireCustomerFulfillmentBranches(
                branchSearchService.findBranchesForDelivery(
                        deliveryProvinceId,
                        deliveryDistrictId,
                        deliveryWardCode,
                        userLat,
                        userLng));

        List<Long> branchIds = nearestBranches.stream().map(bwr -> bwr.branch().getId()).toList();

        Map<Long, Map<Long, List<Inventory>>> inventoryMatrix = allocationService.buildInventoryMatrix(branchIds,
                variantIds);
        AllocationResult allocation = allocationService.allocate(finalCart, variantMap, nearestBranches,
                inventoryMatrix);

        DeliveryInfo deliveryInfo = DeliveryInfo.builder()
                .toDistrictId(deliveryDistrictId).toWardCode(deliveryWardCode)
                .deliveryAddress(deliveryAddress)
                .userLat(userLat != null ? userLat : 0d)
                .userLng(userLng != null ? userLng : 0d)
                .build();

        List<SubOrderDraftDto> enrichedSubOrders = shippingService.enrichWithShippingFees(allocation.subOrders(),
                deliveryInfo, variantMap);

        BigDecimal totalSubtotal = enrichedSubOrders.stream()
                .map(s -> s.getSubtotal() != null ? s.getSubtotal() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalShippingFee = enrichedSubOrders.stream()
                .map(s -> s.getShippingFee() != null ? s.getShippingFee() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal discountAmount = BigDecimal.ZERO;
        if (voucherCode != null) {
            User previewUser = userRepository.findById(userId)
                    .orElseThrow(() -> new NotFoundException("User khong ton tai"));
            discountAmount = voucherService.validateVoucherForOrder(
                    previewUser,
                    voucherCode,
                    totalSubtotal,
                    false,
                    false).discountAmount();
        }

        BigDecimal totalAmount = totalSubtotal.add(totalShippingFee).subtract(discountAmount);
        if (totalAmount.compareTo(BigDecimal.ZERO) < 0) {
            totalAmount = BigDecimal.ZERO;
        }

        SubOrderDraftDto primarySubOrder = enrichedSubOrders.isEmpty() ? null : enrichedSubOrders.get(0);
        List<SuggestedTransferDto> suggestedTransfers = inferSuggestedTransfers(
                primarySubOrder,
                nearestBranches,
                inventoryMatrix);
        String stockStatus = determinePrepareStockStatus(
                enrichedSubOrders,
                allocation.outOfStockItems(),
                suggestedTransfers);
        boolean canPlaceOrder = !enrichedSubOrders.isEmpty();
        boolean requiresManualApproval = !"FULLY_AVAILABLE".equals(stockStatus);
        LocalDateTime createdAt = LocalDateTime.now();
        LocalDateTime expiresAt = createdAt.plusMinutes(PREPARE_TTL_MINUTES);

        String token = UUID.randomUUID().toString();
        List<OrderItemDto> allFinalItems = enrichedSubOrders.stream().flatMap(s -> s.getItems().stream())
                .collect(Collectors.toList());
        Long mainBranchId = primarySubOrder != null ? primarySubOrder.getBranchId() : null;

        PrepareOrderDraft draft = PrepareOrderDraft.builder()
                .prepareToken(token).userId(userId).addressId(request.getUserAddressId()).voucherCode(voucherCode)
                .stockStatus(stockStatus).createdAt(createdAt).expiresAt(expiresAt)
                .branchId(mainBranchId).finalItems(allFinalItems).suggestedTransfers(suggestedTransfers).cartItems(finalCart)
                .receiverName(receiverName).receiverPhone(receiverPhone)
                .userLat(userLat).userLng(userLng).deliveryAddress(deliveryAddress)
                .deliveryDistrictId(deliveryDistrictId).deliveryProvinceId(deliveryProvinceId)
                .deliveryWardCode(deliveryWardCode).subOrders(enrichedSubOrders)
                .outOfStockItems(allocation.outOfStockItems()).totalSubtotal(totalSubtotal)
                .discountAmount(discountAmount)
                .totalShippingFee(totalShippingFee).totalAmount(totalAmount).build();

        saveDraftToRedis(token, draft);

        return PrepareOrderResponse.builder()
                .prepareToken(token)
                .expiresAt(expiresAt)
                .voucherCode(voucherCode)
                .canFulfill(allocation.outOfStockItems().isEmpty() && !enrichedSubOrders.isEmpty())
                .canPlaceOrder(canPlaceOrder)
                .requiresManualApproval(requiresManualApproval)
                .stockStatus(stockStatus)
                .primaryBranch(primarySubOrder != null
                        ? PreparePrimaryBranchDto.builder()
                                .id(primarySubOrder.getBranchId())
                                .name(primarySubOrder.getBranchName())
                                .distanceKm(primarySubOrder.getDistanceKm())
                                .build()
                        : null)
                .suggestedTransfers(suggestedTransfers)
                .subOrders(enrichedSubOrders)
                .totalSubtotal(totalSubtotal)
                .discountAmount(discountAmount)
                .totalShippingFee(totalShippingFee)
                .totalAmount(totalAmount)
                .outOfStockItems(allocation.outOfStockItems())
                .build();
    }

    private Integer parseIntSafe(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String normalizeWardCode(String wardCode) {
        if (wardCode == null) {
            return null;
        }

        String normalized = wardCode.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private List<BranchWithRealDistance> requireCustomerFulfillmentBranches(List<BranchWithRealDistance> branches) {
        if (branches == null || branches.isEmpty()) {
            throw new BadRequestException("Hiá»‡n chÆ°a cĂ³ chi nhĂ¡nh hoáº¡t Ä‘á»™ng Ä‘á»ƒ phá»¥c vá»¥ Ä‘Æ¡n hĂ ng cá»§a báº¡n.");
        }

        List<BranchWithRealDistance> sellableBranches = filterCustomerFulfillmentBranches(branches);
        if (sellableBranches.isEmpty()) {
            throw new BadRequestException("Hiá»‡n chÆ°a cĂ³ chi nhĂ¡nh bĂ¡n hĂ ng phĂ¹ há»£p cho Ä‘á»‹a chá»‰ giao hĂ ng nĂ y.");
        }

        return sellableBranches;
    }

    @Transactional
    public ConfirmOrderResponse confirmOrder(Long userId, ConfirmOrderRequest request) {
        String normalizedIdempotencyKey = normalizeIdempotencyKey(request.getIdempotencyKey());
        ConfirmOrderResponse idempotentResult = getConfirmResultByIdempotency(userId, normalizedIdempotencyKey);
        if (idempotentResult != null) {
            return idempotentResult;
        }

        ConfirmOrderResponse cachedResult = getConfirmResultFromRedis(request.getPrepareToken());
        if (cachedResult != null) {
            saveConfirmResultByIdempotency(userId, normalizedIdempotencyKey, cachedResult);
            return cachedResult;
        }

        String confirmLockKey = PREPARE_CONFIRM_LOCK_PREFIX + request.getPrepareToken();
        Boolean lockAcquired = redisTemplate.opsForValue().setIfAbsent(
                confirmLockKey,
                String.valueOf(userId),
                PREPARE_CONFIRM_LOCK_TTL_SECONDS,
                TimeUnit.SECONDS);
        if (Boolean.FALSE.equals(lockAcquired)) {
            ConfirmOrderResponse lockedResult = getConfirmResultFromRedis(request.getPrepareToken());
            if (lockedResult != null) {
                return lockedResult;
            }
            throw new ConflictException("ÄÆ¡n hĂ ng Ä‘ang Ä‘Æ°á»£c xá»­ lĂ½, vui lĂ²ng Ä‘á»£i trong giĂ¢y lĂ¡t", true);
        }

        try {
            PrepareOrderDraft draft = getDraftFromRedis(request.getPrepareToken());
            if (draft == null)
                throw new BadRequestException("Token háº¿t háº¡n");
            if (!userId.equals(draft.getUserId()))
                throw new BadRequestException("Token khĂ´ng há»£p lá»‡");

            User user = userRepository.findById(userId).orElseThrow(() -> new NotFoundException("User khĂ´ng tá»“n táº¡i"));
            List<CartItemDto> cartSnapshot = normalizeCartItems(draft.getCartItems());
            PreparedQuote liveQuote = buildPreparedQuote(
                    userId,
                    cartSnapshot,
                    draft.getUserLat(),
                    draft.getUserLng(),
                    draft.getDeliveryAddress(),
                    draft.getDeliveryProvinceId(),
                    draft.getDeliveryDistrictId(),
                    draft.getDeliveryWardCode(),
                    draft.getVoucherCode());
            if (liveQuote.subOrders().isEmpty()) {
                throw new ConflictException(
                        "Má»™t hoáº·c nhiá»u sáº£n pháº©m khĂ´ng cĂ²n Ä‘á»§ hĂ ng táº¡i chi nhĂ¡nh bĂ¡n hĂ ng gáº§n nháº¥t, vui lĂ²ng táº£i láº¡i giá» hĂ ng",
                        true);
            }
            // Allow checkout to continue even if stock moved after prepare.
            VoucherService.VoucherOrderEvaluation committedVoucher = voucherService.validateVoucherForOrder(
                    user,
                    draft.getVoucherCode(),
                    liveQuote.totalSubtotal(),
                    true,
                    true);
            PaymentMethod paymentMethod = request.getPaymentMethod() != null ? request.getPaymentMethod()
                    : PaymentMethod.COD;
            StockStatus initialStockStatus = parseStockStatus(draft.getStockStatus());
            PaymentStatus initialPaymentStatus = resolveInitialPaymentStatus(paymentMethod);
            OrderStatus initialStatus = resolveInitialOrderStatus(paymentMethod, initialStockStatus);
            LocalDateTime autoApproveAt = resolveInitialAutoApproveAt(paymentMethod, initialStockStatus);

            Branch primaryBranch = draft.getBranchId() != null
                    ? branchRepository.findById(draft.getBranchId()).orElse(null)
                    : null;

            Order order = Order.builder().code("ORD" + System.currentTimeMillis()).user(user).status(initialStatus)
                    .paymentMethod(paymentMethod).paymentStatus(initialPaymentStatus).createdAt(LocalDateTime.now())
                    .totalAmount(liveQuote.totalSubtotal()).discountAmount(committedVoucher.discountAmount())
                    .finalAmount(liveQuote.totalAmount())
                    .totalShippingFee(liveQuote.totalShippingFee()).userLat(draft.getUserLat())
                    .userLng(draft.getUserLng())
                    .deliveryAddress(draft.getDeliveryAddress()).shippingAddress(draft.getDeliveryAddress())
                    .receiverName(draft.getReceiverName()).receiverPhone(draft.getReceiverPhone())
                    .deliveryAddressId(draft.getAddressId())
                    .voucher(committedVoucher.voucher()).note(request.getNote()).branch(primaryBranch)
                    .stockStatus(initialStockStatus)
                    .fulfillmentStatus(FulfillmentStatus.NOT_STARTED)
                    .autoApproveAt(autoApproveAt)
                    .autoApprovalPaused(Boolean.FALSE)
                    .build();
            Order savedOrder = orderRepository.save(order);

            List<SubOrderSummaryDto> subOrderSummaries = new ArrayList<>();
            boolean anySubOrderMissing = false;

            for (SubOrderDraftDto subDraft : liveQuote.subOrders()) {
                Branch branch = branchRepository.findById(subDraft.getBranchId())
                        .orElseThrow(() -> new NotFoundException("Branch khĂ´ng tá»“n táº¡i"));
                OrderStatus subOrderStatus = (PaymentMethod.PAYOS.equals(paymentMethod)
                        || PaymentMethod.TRANSFER.equals(paymentMethod))
                                ? OrderStatus.AWAITING_PAYMENT
                                : OrderStatus.PENDING;
                SubOrder subOrder = SubOrder.builder().order(savedOrder).branch(branch).status(subOrderStatus)
                        .subtotal(subDraft.getSubtotal()).shippingFee(subDraft.getShippingFee())
                        .estimatedDays(subDraft.getEstimatedDays()).carrier(subDraft.getCarrier()).build();
                SubOrder savedSubOrder = subOrderRepository.save(subOrder);

                for (OrderItemDto item : subDraft.getItems()) {
                    ProductVariant variant = variantRepository.findById(item.getProductVariantId())
                            .orElseThrow(() -> new NotFoundException("Sáº£n pháº©m khĂ´ng tá»“n táº¡i"));

                    inventoryCheckGuardService.assertStockMutationAllowed(
                            subDraft.getBranchId(),
                            List.of(item.getProductVariantId()),
                            "xac nhan don hang"
                    );

                    int requestedQuantity = Objects.requireNonNullElse(item.getQuantity(), 0);
                    int allocatedQuantity = Objects.requireNonNullElse(item.getAllocatedQuantity(), 0);

                    if (allocatedQuantity > 0) {
                        allocatedQuantity = orderInventoryReservationService.reserveInventoryUpTo(
                                subDraft.getBranchId(),
                                item.getProductVariantId(),
                                allocatedQuantity,
                                buildSubOrderReferenceCode(savedSubOrder),
                                "Giu hang cho phan don " + savedOrder.getCode());
                    }

                    int missingQuantity = Math.max(0, requestedQuantity - allocatedQuantity);
                    anySubOrderMissing = anySubOrderMissing || missingQuantity > 0;



                    subOrderItemRepository.save(SubOrderItem.builder()
                            .subOrder(savedSubOrder)
                            .productVariant(variant)
                            .quantity(requestedQuantity)
                            .allocatedQuantity(allocatedQuantity)
                            .missingQuantity(missingQuantity)
                            .unitPrice(item.getUnitPrice())
                            .build());
                }
                subOrderSummaries.add(SubOrderSummaryDto.builder()
                        .subOrderId(savedSubOrder.getId()).branchId(branch.getId()).branchName(branch.getName())
                        .status(subOrderStatus.name()).subtotal(subDraft.getSubtotal())
                        .shippingFee(subDraft.getShippingFee())
                        .estimatedDays(subDraft.getEstimatedDays()).carrier(subDraft.getCarrier()).build());
            }

            savedOrder.setStockStatus(anySubOrderMissing ? StockStatus.PARTIALLY_AVAILABLE : StockStatus.FULLY_AVAILABLE);
            if (anySubOrderMissing) {
                savedOrder.setAutoApproveAt(null);
            }
            orderRepository.save(savedOrder);

            String checkoutUrl = null;
            if (PaymentMethod.PAYOS.equals(paymentMethod)) {
                try {
                    com.zone.agri.dto.response.payment.PayOSApiResponse.PayOSLinkData payosData = payOSService
                            .createPaymentLink(savedOrder);
                    savedOrder.setPayosPaymentLinkId(payosData.getPaymentLinkId());
                    savedOrder.setPayosCheckoutUrl(payosData.getCheckoutUrl());
                    orderRepository.save(savedOrder);
                    checkoutUrl = payosData.getCheckoutUrl();
                } catch (Exception e) {
                    throw new BadRequestException("Lá»—i táº¡o PayOS link: " + e.getMessage());
                }
            }

            redisTemplate.delete(PREPARE_KEY_PREFIX + request.getPrepareToken());
            cartSnapshot.stream().map(CartItemDto::getProductVariantId).distinct()
                    .forEach(vId -> cartItemRepository.findByUserIdAndProductVariantId(userId, vId)
                            .ifPresent(cartItemRepository::delete));

            ConfirmOrderResponse response = ConfirmOrderResponse.builder().orderId(savedOrder.getId())
                    .orderCode(savedOrder.getCode())
                    .status(resolveWorkflowStatus(savedOrder))
                    .legacyStatus(savedOrder.getStatus() != null ? savedOrder.getStatus().name() : "")
                    .paymentStatus(resolvePaymentStatus(savedOrder))
                    .fulfillmentStatus(resolveFulfillmentStatus(savedOrder))
                    .stockStatus(resolveStockStatus(savedOrder))
                    .autoApproveAt(savedOrder.getAutoApproveAt())
                    .voucherCode(draft.getVoucherCode()).subOrders(subOrderSummaries)
                    .totalAmount(liveQuote.totalAmount())
                    .discountAmount(committedVoucher.discountAmount()).totalShippingFee(liveQuote.totalShippingFee())
                    .checkoutUrl(checkoutUrl).build();

            saveConfirmResultToRedis(request.getPrepareToken(), response);
            saveConfirmResultByIdempotency(userId, normalizedIdempotencyKey, response);
            return response;
        } finally {
            redisTemplate.delete(confirmLockKey);
        }
    }

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    // HELPERS & REDIS
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

    private List<CartItemDto> normalizeCartItems(List<CartItemDto> cartItems) {
        if (cartItems == null || cartItems.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, Integer> quantityByVariant = new LinkedHashMap<>();
        for (CartItemDto item : cartItems) {
            if (item == null || item.getProductVariantId() == null || item.getQuantity() == null
                    || item.getQuantity() <= 0) {
                continue;
            }
            quantityByVariant.merge(item.getProductVariantId(), item.getQuantity(), Integer::sum);
        }

        return quantityByVariant.entrySet().stream()
                .map(entry -> new CartItemDto(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());
    }

    private int availableToAllocate(Inventory inventory) {
        if (inventory == null) {
            return 0;
        }

        int quantity = Objects.requireNonNullElse(inventory.getQuantity(), 0);
        int reserved = Objects.requireNonNullElse(inventory.getReservedQuantity(), 0);
        return Math.max(0, quantity - reserved);
    }

    private boolean subOrderHasMissingItems(SubOrder subOrder) {
        if (subOrder == null || subOrder.getItems() == null) {
            return false;
        }

        return subOrder.getItems().stream()
                .filter(Objects::nonNull)
                .anyMatch(item -> Objects.requireNonNullElse(item.getMissingQuantity(), 0) > 0);
    }

    private boolean orderHasMissingItems(Order order) {
        if (order == null || order.getSubOrders() == null) {
            return false;
        }

        return order.getSubOrders().stream()
                .filter(Objects::nonNull)
                .anyMatch(this::subOrderHasMissingItems);
    }

    private boolean canRequestReplenishment(SubOrder subOrder) {
        if (subOrder == null || subOrder.getStatus() == null) {
            return false;
        }

        if (subOrder.getStatus() == OrderStatus.CANCELLED
                || subOrder.getStatus() == OrderStatus.RETURNED
                || subOrder.getStatus() == OrderStatus.COMPLETED) {
            return false;
        }

        return (subOrder.getStatus() == OrderStatus.PENDING
                || subOrder.getStatus() == OrderStatus.AWAITING_REPLENISHMENT)
                && subOrderHasMissingItems(subOrder);
    }

    private PreparedQuote buildPreparedQuote(
            Long userId,
            List<CartItemDto> cartItems,
            Double userLat,
            Double userLng,
            String deliveryAddress,
            Integer deliveryProvinceId,
            Integer deliveryDistrictId,
            String deliveryWardCode,
            String voucherCode) {
        List<CartItemDto> normalizedCart = normalizeCartItems(cartItems);
        if (normalizedCart.isEmpty()) {
            throw new BadRequestException("Giá» hĂ ng cá»§a báº¡n Ä‘ang trá»‘ng");
        }

        List<Long> variantIds = normalizedCart.stream().map(CartItemDto::getProductVariantId).distinct().toList();
        List<ProductVariant> variants = variantRepository.findAllById(variantIds);
        if (variants.size() != variantIds.size()) {
            throw new NotFoundException("Má»™t hoáº·c nhiá»u sáº£n pháº©m khĂ´ng tá»“n táº¡i");
        }

        Map<Long, ProductVariant> variantMap = variants.stream()
                .collect(Collectors.toMap(ProductVariant::getId, Function.identity()));

        String normalizedWardCode = normalizeWardCode(deliveryWardCode);

        List<BranchWithRealDistance> nearestBranches = requireCustomerFulfillmentBranches(
                branchSearchService.findBranchesForDelivery(
                        deliveryProvinceId,
                        deliveryDistrictId,
                        normalizedWardCode,
                        userLat,
                        userLng));

        List<Long> branchIds = nearestBranches.stream().map(bwr -> bwr.branch().getId()).toList();
        Map<Long, Map<Long, List<Inventory>>> inventoryMatrix = allocationService.buildInventoryMatrix(branchIds,
                variantIds);
        AllocationResult allocation = allocationService.allocate(normalizedCart, variantMap, nearestBranches,
                inventoryMatrix);

        if (!allocation.outOfStockItems().isEmpty() && allocation.subOrders().isEmpty()) {
            return new PreparedQuote(
                    normalizedCart,
                    Collections.emptyList(),
                    allocation.outOfStockItems(),
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO);
        }

        DeliveryInfo deliveryInfo = DeliveryInfo.builder()
                .toDistrictId(deliveryDistrictId)
                .toWardCode(normalizedWardCode)
                .deliveryAddress(deliveryAddress)
                .userLat(userLat != null ? userLat : 0d)
                .userLng(userLng != null ? userLng : 0d)
                .build();

        List<SubOrderDraftDto> enrichedSubOrders = shippingService.enrichWithShippingFees(
                allocation.subOrders(),
                deliveryInfo,
                variantMap);

        BigDecimal totalSubtotal = enrichedSubOrders.stream()
                .map(s -> s.getSubtotal() != null ? s.getSubtotal() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalShippingFee = enrichedSubOrders.stream()
                .map(s -> s.getShippingFee() != null ? s.getShippingFee() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal discountAmount = BigDecimal.ZERO;
        String normalizedVoucherCode = voucherService.normalizeVoucherCode(voucherCode);
        if (normalizedVoucherCode != null) {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new NotFoundException("NgÆ°á»i dĂ¹ng khĂ´ng tá»“n táº¡i"));
            discountAmount = voucherService.validateVoucherForOrder(
                    user,
                    normalizedVoucherCode,
                    totalSubtotal,
                    false,
                    false).discountAmount();
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
                totalAmount);
    }

    private void ensurePreparedQuoteStillValid(PrepareOrderDraft draft, PreparedQuote liveQuote) {
        if (!Objects.equals(buildSubOrderSignature(draft.getSubOrders()),
                buildSubOrderSignature(liveQuote.subOrders()))) {
            PrepareOrderRequest refreshRequest = new PrepareOrderRequest();
            refreshRequest.setUserAddressId(draft.getAddressId());
            refreshRequest.setVoucherCode(draft.getVoucherCode());
            refreshRequest.setUserLat(draft.getUserLat());
            refreshRequest.setUserLng(draft.getUserLng());
            refreshRequest.setCart(draft.getCartItems());
            PrepareOrderResponse refreshedQuote = prepareOrder(draft.getUserId(), refreshRequest);
            throw new ConflictException(
                    "ORDER_QUOTE_CHANGED",
                    "Thong tin don hang da thay doi. Vui long kiem tra lai bao gia moi truoc khi dat.",
                    Map.of(
                            "newPrepareToken", refreshedQuote.getPrepareToken(),
                            "newQuote", refreshedQuote));
        }
    }

    private String determinePrepareStockStatus(
            List<SubOrderDraftDto> subOrders,
            List<OutOfStockItemDto> outOfStockItems,
            List<SuggestedTransferDto> suggestedTransfers) {
        if (subOrders == null || subOrders.isEmpty()) {
            return "OUT_OF_STOCK";
        }

        boolean hasMissingInPrimaryPlan = subOrders.stream()
                .filter(Objects::nonNull)
                .flatMap(subOrder -> subOrder.getItems() == null ? Stream.empty() : subOrder.getItems().stream())
                .anyMatch(item -> Objects.requireNonNullElse(item.getMissingQuantity(), 0) > 0);
        boolean hasNetworkShortage = outOfStockItems != null && !outOfStockItems.isEmpty();
        boolean hasTransferSuggestion = suggestedTransfers != null && !suggestedTransfers.isEmpty();

        if (!hasMissingInPrimaryPlan && !hasNetworkShortage) {
            return "FULLY_AVAILABLE";
        }
        if (!hasNetworkShortage && hasTransferSuggestion) {
            return "AVAILABLE_AFTER_TRANSFER";
        }
        return "PARTIALLY_AVAILABLE";
    }

    private StockStatus parseStockStatus(String stockStatus) {
        if (stockStatus == null || stockStatus.isBlank()) {
            return StockStatus.FULLY_AVAILABLE;
        }

        try {
            return StockStatus.valueOf(stockStatus.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return StockStatus.FULLY_AVAILABLE;
        }
    }

    private PaymentStatus resolveInitialPaymentStatus(PaymentMethod paymentMethod) {
        if (paymentMethod == null) {
            return PaymentStatus.UNPAID;
        }

        return switch (paymentMethod) {
            case PAYOS -> PaymentStatus.PENDING;
            case TRANSFER -> PaymentStatus.PENDING_VERIFICATION;
            default -> PaymentStatus.UNPAID;
        };
    }

    private OrderStatus resolveInitialOrderStatus(PaymentMethod paymentMethod, StockStatus stockStatus) {
        if (PaymentMethod.PAYOS.equals(paymentMethod) || PaymentMethod.TRANSFER.equals(paymentMethod)) {
            return OrderStatus.AWAITING_PAYMENT;
        }

        return OrderStatus.PENDING;
    }

    private LocalDateTime resolveInitialAutoApproveAt(PaymentMethod paymentMethod, StockStatus stockStatus) {
        if ((PaymentMethod.COD.equals(paymentMethod) || PaymentMethod.CASH.equals(paymentMethod))
                && stockStatus == StockStatus.FULLY_AVAILABLE) {
            return LocalDateTime.now().plusMinutes(Math.max(1, autoApproveMinutes));
        }

        return null;
    }

    private List<SuggestedTransferDto> inferSuggestedTransfers(
            SubOrderDraftDto primarySubOrder,
            List<BranchWithRealDistance> rankedBranches,
            Map<Long, Map<Long, List<Inventory>>> inventoryMatrix) {
        if (primarySubOrder == null || primarySubOrder.getBranchId() == null || primarySubOrder.getItems() == null) {
            return Collections.emptyList();
        }

        Map<Long, Integer> missingByVariant = primarySubOrder.getItems().stream()
                .filter(Objects::nonNull)
                .filter(item -> item.getProductVariantId() != null)
                .map(item -> Map.entry(
                        item.getProductVariantId(),
                        Math.max(0, Objects.requireNonNullElse(item.getMissingQuantity(), 0))))
                .filter(entry -> entry.getValue() > 0)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        Integer::sum,
                        LinkedHashMap::new));

        if (missingByVariant.isEmpty()) {
            return Collections.emptyList();
        }

        List<SuggestedTransferDto> suggestions = new ArrayList<>();
        for (Map.Entry<Long, Integer> entry : missingByVariant.entrySet()) {
            Long variantId = entry.getKey();
            int remaining = entry.getValue();

            for (BranchWithRealDistance branchCandidate : rankedBranches) {
                if (branchCandidate == null || branchCandidate.branch() == null || remaining <= 0) {
                    continue;
                }

                Long candidateBranchId = branchCandidate.branch().getId();
                if (Objects.equals(candidateBranchId, primarySubOrder.getBranchId())) {
                    continue;
                }

                int available = inventoryMatrix.getOrDefault(candidateBranchId, Collections.emptyMap())
                        .getOrDefault(variantId, Collections.emptyList())
                        .stream()
                        .mapToInt(this::availableToAllocate)
                        .sum();
                if (available <= 0) {
                    continue;
                }

                int quantity = Math.min(available, remaining);
                suggestions.add(SuggestedTransferDto.builder()
                        .fromBranchId(candidateBranchId)
                        .fromBranchName(branchCandidate.branch().getName())
                        .toBranchId(primarySubOrder.getBranchId())
                        .productVariantId(variantId)
                        .quantity(quantity)
                        .build());
                remaining -= quantity;
            }
        }

        return suggestions;
    }


    private List<BranchWithRealDistance> filterCustomerFulfillmentBranches(List<BranchWithRealDistance> branches) {
        if (branches == null || branches.isEmpty()) {
            return Collections.emptyList();
        }

        List<BranchWithRealDistance> preferredBranches = branches.stream()
                .filter(branchWithDistance -> isCustomerFulfillmentBranch(branchWithDistance.branch()))
                .toList();

        if (!preferredBranches.isEmpty()) {
            return preferredBranches;
        }

        return branches.stream()
                .filter(branchWithDistance -> branchWithDistance.branch() != null)
                .toList();
    }

    private boolean isCustomerFulfillmentBranch(Branch branch) {
        return branch != null
                && (branch.getBranchType() == null
                        || !"WAREHOUSE".equalsIgnoreCase(branch.getBranchType()));
    }

    private String buildSubOrderSignature(List<SubOrderDraftDto> subOrders) {
        if (subOrders == null || subOrders.isEmpty()) {
            return "";
        }

        return subOrders.stream()
                .sorted(Comparator.comparing(SubOrderDraftDto::getBranchId, Comparator.nullsLast(Long::compareTo)))
                .map(subOrder -> {
                    String itemSignature = subOrder.getItems() == null ? ""
                            : subOrder.getItems().stream()
                                    .sorted(Comparator
                                            .comparing(OrderItemDto::getProductVariantId,
                                                    Comparator.nullsLast(Long::compareTo))
                                            .thenComparing(item -> Objects.requireNonNullElse(item.getQuantity(), 0)))
                                    .map(item -> String.join(":",
                                            String.valueOf(item.getProductVariantId()),
                                            String.valueOf(Objects.requireNonNullElse(item.getQuantity(), 0)),
                                            String.valueOf(Objects.requireNonNullElse(item.getUnitPrice(), BigDecimal.ZERO))))
                                    .collect(Collectors.joining(","));

                    return String.join("|",
                            String.valueOf(subOrder.getBranchId()),
                            Objects.toString(subOrder.getEstimatedDays(), ""),
                            Objects.toString(subOrder.getCarrier(), ""),
                            itemSignature);
                })
                .collect(Collectors.joining("||"));
    }

    private String normalizeVoucherCode(String voucherCode) {
        return voucherService.normalizeVoucherCode(voucherCode);
    }

    private VoucherValidation validateVoucher(
            User user,
            String voucherCode,
            BigDecimal orderSubtotal,
            boolean consume,
            boolean conflictOnUnavailable) {
        String normalizedVoucherCode = normalizeVoucherCode(voucherCode);
        if (normalizedVoucherCode == null) {
            return new VoucherValidation(null, null, BigDecimal.ZERO);
        }

        Voucher voucher = (consume ? voucherRepository.findByCodeForUpdate(normalizedVoucherCode)
                : voucherRepository.findByCode(normalizedVoucherCode))
                .orElseThrow(() -> voucherValidationException(conflictOnUnavailable, "MĂ£ voucher khĂ´ng tá»“n táº¡i"));

        LocalDateTime now = LocalDateTime.now();
        if (voucher.getStatus() != VoucherStatus.ACTIVE
                || voucher.getStartDate() == null
                || voucher.getEndDate() == null
                || now.isBefore(voucher.getStartDate())
                || now.isAfter(voucher.getEndDate())) {
            throw voucherValidationException(conflictOnUnavailable, "Voucher khĂ´ng há»£p lá»‡ hoáº·c Ä‘Ă£ háº¿t háº¡n");
        }

        BigDecimal minOrderValue = voucher.getMinOrderValue() != null ? voucher.getMinOrderValue() : BigDecimal.ZERO;
        if (orderSubtotal.compareTo(minOrderValue) < 0) {
            throw voucherValidationException(conflictOnUnavailable,
                    "ÄÆ¡n hĂ ng chÆ°a Ä‘áº¡t giĂ¡ trá»‹ tá»‘i thiá»ƒu Ä‘á»ƒ sá»­ dá»¥ng voucher nĂ y");
        }

        if (Objects.requireNonNullElse(voucher.getQuantity(), 0) <= 0) {
            throw voucherValidationException(conflictOnUnavailable, "Voucher nĂ y Ä‘Ă£ háº¿t lÆ°á»£t sá»­ dá»¥ng trĂªn há»‡ thá»‘ng");
        }

        UserVoucher userVoucher = userVoucherRepository.findByUserAndVoucher(user, voucher)
                .orElse(new UserVoucher(user, voucher, 0, false));
        int maxUsagePerUser = voucher.getMaxUsagePerUser() != null ? voucher.getMaxUsagePerUser() : 1;
        if (Objects.requireNonNullElse(userVoucher.getUsageCount(), 0) >= maxUsagePerUser) {
            throw voucherValidationException(conflictOnUnavailable,
                    "Báº¡n Ä‘Ă£ sá»­ dá»¥ng tá»‘i Ä‘a sá»‘ lÆ°á»£t cho phĂ©p cá»§a voucher nĂ y");
        }

        BigDecimal discountAmount;
        if (VoucherDiscountType.PERCENT.equals(voucher.getDiscountType())) {
            BigDecimal percentValue = voucher.getValue() != null ? voucher.getValue() : BigDecimal.ZERO;
            BigDecimal calculatedDiscount = orderSubtotal.multiply(percentValue).divide(BigDecimal.valueOf(100));

            discountAmount = voucher.getMaxDiscount() != null && voucher.getMaxDiscount().compareTo(BigDecimal.ZERO) > 0
                    ? calculatedDiscount.min(voucher.getMaxDiscount())
                    : calculatedDiscount;
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
            try {
                userVoucherRepository.saveAndFlush(userVoucher);
            } catch (DataIntegrityViolationException ex) {
                throw new ConflictException("Báº¡n Ä‘ang thanh toĂ¡n Ä‘á»“ng thá»i vá»›i cĂ¹ng voucher nĂ y. Vui lĂ²ng thá»­ láº¡i.");
            }
        }

        return new VoucherValidation(voucher, userVoucher, discountAmount);
    }

    private RuntimeException voucherValidationException(boolean conflictOnUnavailable, String message) {
        return conflictOnUnavailable ? new ConflictException(message) : new BadRequestException(message);
    }

    private void restoreVoucherForOrder(Order order) {
        voucherService.restoreVoucherForOrder(order);
    }

    private String buildSubOrderReferenceCode(SubOrder subOrder) {
        return orderInventoryReservationService.buildSubOrderReferenceCode(subOrder);
    }

    private void releaseAllocatedInventoryForOrder(Order order) {
        if (order.getSubOrders() != null && !order.getSubOrders().isEmpty()) {
            order.getSubOrders().stream()
                    .filter(subOrder -> subOrder.getStatus() != OrderStatus.CANCELLED)
                    .forEach(this::releaseAllocatedInventoryForSubOrder);
            return;
        }

        orderInventoryReservationService.releaseReservedInventory(
                order.getCode(),
                "Giai phong hang giu do huy don hang");
        List<InventoryTransaction> legacyTransactions = transactionRepository
                .findByReferenceCodeAndType(order.getCode(), TransactionType.SALE);
        releaseInventoryTransactions(order.getCode(), legacyTransactions, "HoĂ n kho do há»§y Ä‘Æ¡n hĂ ng");
    }

    private void releaseAllocatedInventoryForSubOrder(SubOrder subOrder) {
        orderInventoryReservationService.releaseReservedInventory(
                buildSubOrderReferenceCode(subOrder),
                "Giai phong hang giu do huy phan don");
        List<InventoryTransaction> saleTransactions = transactionRepository.findByReferenceCodeAndType(
                buildSubOrderReferenceCode(subOrder),
                TransactionType.SALE);
        releaseInventoryTransactions(buildSubOrderReferenceCode(subOrder), saleTransactions,
                "HoĂ n kho do há»§y pháº§n Ä‘Æ¡n");
    }

    private void releaseInventoryTransactions(String referenceCode, List<InventoryTransaction> saleTransactions,
            String reason) {
        for (InventoryTransaction saleTransaction : saleTransactions) {
            int quantityToRelease = Math.abs(Objects.requireNonNullElse(saleTransaction.getQuantityChange(), 0));
            if (quantityToRelease <= 0 || saleTransaction.getInventory() == null) {
                continue;
            }

            Inventory inventory = saleTransaction.getInventory();
            inventoryCheckGuardService.assertStockMutationAllowed(
                    inventory.getBranch() != null ? inventory.getBranch().getId() : null,
                    inventory.getProductVariant() != null ? List.of(inventory.getProductVariant().getId()) : List.of(),
                    "hoàn kho chứng từ"
            );
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

            // Khi há»§y Ä‘Æ¡n hoĂ n kho, tá»± Ä‘á»™ng cáº¥p láº¡i cho cĂ¡c Ä‘Æ¡n Ä‘ang thiáº¿u hĂ ng
            // táº¡i cĂ¹ng chi nhĂ¡nh náº¿u cĂ³ thá»ƒ.
            if (inventory.getBranch() != null && inventory.getProductVariant() != null) {
                backorderService.fulfillBackordersOnStockReceive(
                        inventory.getBranch().getId(),
                        inventory.getProductVariant().getId(),
                        quantityToRelease);
            }
        }
    }

    private String normalizeIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null) {
            return null;
        }

        String normalized = idempotencyKey.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String buildIdempotencyRedisKey(Long userId, String idempotencyKey) {
        return PREPARE_CONFIRM_IDEMPOTENCY_PREFIX + userId + ":" + idempotencyKey;
    }

    private void saveDraftToRedis(String token, PrepareOrderDraft draft) {
        try {
            redisTemplate.opsForValue().set(PREPARE_KEY_PREFIX + token, objectMapper.writeValueAsString(draft),
                    PREPARE_TTL_MINUTES, TimeUnit.MINUTES);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Lá»—i lÆ°u Redis");
        }
    }

    private PrepareOrderDraft getDraftFromRedis(String token) {
        String json = redisTemplate.opsForValue().get(PREPARE_KEY_PREFIX + token);
        if (json == null)
            return null;
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
                    TimeUnit.MINUTES);
        } catch (JsonProcessingException e) {
            log.warn("KhĂ´ng thá»ƒ lÆ°u káº¿t quáº£ xĂ¡c nháº­n vĂ o Redis cho token {}: {}", token, e.getMessage());
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

    private void saveConfirmResultByIdempotency(Long userId, String idempotencyKey, ConfirmOrderResponse response) {
        if (userId == null || idempotencyKey == null || response == null) {
            return;
        }

        try {
            redisTemplate.opsForValue().set(
                    buildIdempotencyRedisKey(userId, idempotencyKey),
                    objectMapper.writeValueAsString(response),
                    PREPARE_TTL_MINUTES,
                    TimeUnit.MINUTES);
        } catch (JsonProcessingException e) {
            log.warn("Khong the luu ket qua idempotency cho user {}: {}", userId, e.getMessage());
        }
    }

    private ConfirmOrderResponse getConfirmResultByIdempotency(Long userId, String idempotencyKey) {
        if (userId == null || idempotencyKey == null) {
            return null;
        }

        String json = redisTemplate.opsForValue().get(buildIdempotencyRedisKey(userId, idempotencyKey));
        if (json == null) {
            return null;
        }

        try {
            return objectMapper.readValue(json, ConfirmOrderResponse.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    // CHECKOUT API TRá»°C TIáº¾P (CĂ“ ĂP Dá»¤NG VOUCHER & PAYOS)
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

    @Transactional
    public Order placeOrder(Long userId, CheckoutRequest req) {
        User user = userRepository.findById(userId).orElseThrow(() -> new NotFoundException("User khĂ´ng tĂ¬m tháº¥y"));

        // Láº¥y chi nhĂ¡nh tá»« request (gá»­i tá»« FE) hoáº·c tĂ¬m tá»± Ä‘á»™ng
        Branch selectedBranch;
        if (req.getBranchId() != null) {
            selectedBranch = branchRepository.findById(req.getBranchId())
                    .orElseThrow(() -> new NotFoundException("Chi nhĂ¡nh khĂ´ng tá»“n táº¡i"));
        } else {
            selectedBranch = findBranchWithEnoughStock(req.getItems());
            if (selectedBranch == null)
                throw new BadRequestException("KhĂ´ng chi nhĂ¡nh nĂ o Ä‘á»§ hĂ ng");
        }

        PaymentMethod paymentMethod = req.getPaymentMethod() != null ? req.getPaymentMethod() : PaymentMethod.COD;
        OrderStatus legacyInitialStatus = PaymentMethod.PAYOS.equals(paymentMethod)
                ? OrderStatus.AWAITING_PAYMENT
                : OrderStatus.PENDING;

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
                .paymentStatus(resolveInitialPaymentStatus(paymentMethod))
                .stockStatus(StockStatus.FULLY_AVAILABLE)
                .fulfillmentStatus(FulfillmentStatus.NOT_STARTED)
                .autoApproveAt(resolveInitialAutoApproveAt(paymentMethod, StockStatus.FULLY_AVAILABLE))
                .autoApprovalPaused(Boolean.FALSE)
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
                    .orElseThrow(() -> new NotFoundException("Sáº£n pháº©m khĂ´ng tá»“n táº¡i"));

            inventoryCheckGuardService.assertStockMutationAllowed(
                    selectedBranch.getId(),
                    List.of(variant.getId()),
                    "xac nhan xuat kho"
            );

            // LOGIC TRỪ KHO THEO LÔ (FIFO)
            int remainingToDeduct = itemReq.getQuantity();
            List<Inventory> batches = inventoryRepository.findForUpdateFIFO(selectedBranch.getId(), variant.getId());

            for (Inventory batch : batches) {
                if (remainingToDeduct <= 0)
                    break;
                int available = Objects.requireNonNullElse(batch.getQuantity(), 0);
                if (available <= 0)
                    continue;

                int deduct = Math.min(available, remainingToDeduct);
                int newQty = available - deduct;
                batch.setQuantity(newQty);
                inventoryRepository.save(batch);

                transactionRepository.save(InventoryTransaction.builder()
                        .type(TransactionType.SALE)
                        .quantityChange(-deduct)
                        .newBalance(newQty)
                        .referenceCode(savedOrder.getCode())
                        .reason("BĂ¡n hĂ ng trá»±c tiáº¿p (ÄÆ¡n: " + savedOrder.getCode() + ")")
                        .createdAt(LocalDateTime.now())
                        .inventory(batch)
                        .build());

                remainingToDeduct -= deduct;

                BigDecimal importPrice = batch.getImportPrice() != null ? batch.getImportPrice() : BigDecimal.ZERO;
                BigDecimal sellingPrice = settingService.calculateSellingPrice(importPrice);

                orderItemRepository.save(OrderItem.builder()
                        .order(savedOrder)
                        .productVariant(variant)
                        .quantity(deduct)
                        .price(sellingPrice)
                        .build());

                subTotal = subTotal.add(sellingPrice.multiply(new BigDecimal(deduct)));
            }

            if (remainingToDeduct > 0) {
                throw new ConflictException("Háº¿t hĂ ng trong lĂºc thanh toĂ¡n cho sáº£n pháº©m: " + variant.getSku());
            }
        }

        // đŸ‘‰ VOUCHER LOGIC
        BigDecimal discountAmount = BigDecimal.ZERO;
        if (req.getVoucherCode() != null && !req.getVoucherCode().trim().isEmpty()) {
            VoucherService.VoucherOrderEvaluation validation = voucherService.validateVoucherForOrder(
                    user,
                    req.getVoucherCode(),
                    subTotal,
                    true,
                    false);
            discountAmount = validation.discountAmount();
        }

        // đŸ‘‰ PHĂ Váº¬N CHUYá»‚N
        BigDecimal shippingFee = new BigDecimal("15000");

        // đŸ‘‰ CHá»T Tá»”NG TIá»€N VĂ€ Cáº¬P NHáº¬T ÄÆ N HĂ€NG
        BigDecimal finalAmount = subTotal.add(shippingFee).subtract(discountAmount);
        if (finalAmount.compareTo(BigDecimal.ZERO) < 0) {
            finalAmount = BigDecimal.ZERO;
        }

        savedOrder.setTotalAmount(subTotal);
        savedOrder.setTotalShippingFee(shippingFee);
        savedOrder.setDiscountAmount(discountAmount);
        savedOrder.setFinalAmount(finalAmount);

        // đŸ‘‰ PAYOS LOGIC
        if (PaymentMethod.PAYOS.equals(paymentMethod)) {
            try {
                com.zone.agri.dto.response.payment.PayOSApiResponse.PayOSLinkData payosData = payOSService
                        .createPaymentLink(savedOrder);
                savedOrder.setPayosPaymentLinkId(payosData.getPaymentLinkId());
                savedOrder.setPayosCheckoutUrl(payosData.getCheckoutUrl());
            } catch (Exception e) {
                throw new BadRequestException("Lá»—i táº¡o PayOS link: " + e.getMessage());
            }
        }

        orderRepository.save(savedOrder);

        // XĂ³a sáº£n pháº©m khá»i giá» hĂ ng
        req.getItems().stream().map(CheckoutItemRequest::getVariantId).distinct()
                .forEach(vId -> cartItemRepository.findByUserIdAndProductVariantId(userId, vId)
                        .ifPresent(cartItemRepository::delete));

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
