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
import com.zone.agri.exception.Forbidden;
import com.zone.agri.exception.NotFoundException;
import com.zone.agri.repository.*;
import com.zone.agri.service.BranchSearchService.BranchWithRealDistance;
import com.zone.agri.service.InventoryAllocationService.AllocationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
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
    private final InventoryTransferService inventoryTransferService;
    private final BackorderService backorderService;
    private final ImmediateReplenishmentService immediateReplenishmentService;

    @Lazy
    private final CustomerService customerService;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private static final String PREPARE_KEY_PREFIX = "prepare:";
    private static final String PREPARE_CONFIRM_LOCK_PREFIX = "prepare:confirm:lock:";
    private static final String PREPARE_CONFIRM_RESULT_PREFIX = "prepare:confirm:result:";
    private static final long PREPARE_TTL_MINUTES = 30;
    private static final long PREPARE_CONFIRM_LOCK_TTL_SECONDS = 120;
    private static final long SHIPPING_RECEIVED_CONFIRM_AFTER_DAYS = 7;

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

    // ══════════════════════════════════════════════════════════════════════
    // QUẢN LÝ ĐƠN HÀNG CHO USER
    // ══════════════════════════════════════════════════════════════════════

    public List<OrderResponse> getMyOrders(Long userId, OrderStatus status) {
        List<Order> orders;
        if (status != null) {
            if (status == OrderStatus.PROCESSING) {
                orders = orderRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                        .filter(o -> o.getStatus() == OrderStatus.PROCESSING
                                || o.getStatus() == OrderStatus.AWAITING_REPLENISHMENT)
                        .collect(Collectors.toList());
            } else if (status == OrderStatus.AWAITING_REPLENISHMENT) {
                orders = new ArrayList<>();
            } else {
                orders = orderRepository.findByUserIdAndStatusOrderByCreatedAtDesc(userId, status);
            }
        } else {
            orders = orderRepository.findByUserIdOrderByCreatedAtDesc(userId);
        }
        syncPayOSPaymentStatuses(orders);
        return orders.stream().map(o -> mapToOrderResponse(o, true)).collect(Collectors.toList());
    }

    public OrderResponse getMyOrderDetail(Long userId, Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy đơn hàng ID: " + orderId));

        if (!order.getUser().getId().equals(userId)) {
            throw new BadRequestException("Bạn không có quyền xem đơn hàng này!");
        }

        if (PaymentMethod.PAYOS.equals(order.getPaymentMethod())
                && PaymentStatus.UNPAID.equals(order.getPaymentStatus())) {
            if (payOSService.checkPaymentStatus(order)) {
                payOSService.markOrderPaid(order);
            }
        }

        return mapToOrderResponse(order, true);
    }

    @Transactional
    public void confirmReceivedByCustomer(Long userId, Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy đơn hàng ID: " + orderId));

        if (order.getUser() == null || !order.getUser().getId().equals(userId)) {
            throw new Forbidden("Bạn không có quyền thao tác trên đơn hàng này");
        }

        if (order.getStatus() != OrderStatus.SHIPPING) {
            throw new BadRequestException("Chỉ có thể xác nhận khi đơn hàng đang giao.");
        }

        markOrderAsReceived(order, true);
    }

    // ══════════════════════════════════════════════════════════════════════
    // QUẢN LÝ ĐƠN HÀNG CHO ADMIN
    // ══════════════════════════════════════════════════════════════════════

    public Page<OrderResponse> getAdminOrders(OrderStatus status, String search, Pageable pageable) {
        Page<Order> orderPage = orderRepository.findAdminOrdersWithFilter(status, search, pageable);

        syncPayOSPaymentStatuses(orderPage.getContent());
        return orderPage.map(o -> mapToOrderResponse(o, false));
    }

    public OrderResponse getAdminOrderDetail(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy đơn hàng ID: " + orderId));

        if (PaymentMethod.PAYOS.equals(order.getPaymentMethod())
                && PaymentStatus.UNPAID.equals(order.getPaymentStatus())) {
            if (payOSService.checkPaymentStatus(order)) {
                payOSService.markOrderPaid(order);
            }
        }

        return mapToOrderResponse(order, false);
    }

    @Transactional
    public void cancelMyOrder(Long userId, Long orderId, String cancelReason) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy đơn hàng ID: " + orderId));

        if (!order.getUser().getId().equals(userId)) {
            throw new BadRequestException("Bạn không có quyền hủy đơn hàng này");
        }
        if (order.getStatus() == OrderStatus.CANCELLED || order.getStatus() == OrderStatus.COMPLETED
                || order.getStatus() == OrderStatus.RETURNED) {
            throw new BadRequestException("Đơn hàng đã đóng, không thể hủy");
        }
        if (order.getStatus() == OrderStatus.SHIPPING) {
            throw new BadRequestException("Đơn hàng đang giao, không thể hủy");
        }

        releaseAllocatedInventoryForOrder(order);
        restoreVoucherForOrder(order);
        order.setStatus(OrderStatus.CANCELLED);
        if (order.getSubOrders() != null) {
            order.getSubOrders().forEach(subOrder -> subOrder.setStatus(OrderStatus.CANCELLED));
        }
        if (PaymentMethod.PAYOS.equals(order.getPaymentMethod())
                && PaymentStatus.UNPAID.equals(order.getPaymentStatus())) {
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
                .orElseThrow(() -> new NotFoundException("Không tìm thấy đơn hàng ID: " + orderId));

        OrderStatus currentStatus = order.getStatus();
        if (currentStatus == OrderStatus.CANCELLED || currentStatus == OrderStatus.COMPLETED
                || currentStatus == OrderStatus.RETURNED) {
            throw new BadRequestException("Không thể thay đổi trạng thái của đơn hàng đã đóng!");
        }

        if (currentStatus == OrderStatus.SHIPPING && newStatus == OrderStatus.COMPLETED) {
            throw new BadRequestException("Đơn hàng đang giao chỉ được hoàn tất khi khách hàng xác nhận đã nhận.");
        }

        validateStatusTransition(currentStatus, newStatus);

        if (newStatus == OrderStatus.RECEIVED) {
            markOrderAsReceived(order, false);
            return;
        }
        if (newStatus == OrderStatus.COMPLETED) {
            completeReceivedOrder(order);
            customerService.evaluateAndHandleCustomerReputation(order.getUser().getId());
            return;
        }
        if (newStatus == OrderStatus.CANCELLED) {
            releaseAllocatedInventoryForOrder(order);
            restoreVoucherForOrder(order);
            if (order.getSubOrders() != null) {
                order.getSubOrders().stream()
                        .filter(subOrder -> subOrder.getStatus() != OrderStatus.CANCELLED)
                        .forEach(subOrder -> subOrder.setStatus(OrderStatus.CANCELLED));
            }
            if (PaymentMethod.PAYOS.equals(order.getPaymentMethod())
                    && PaymentStatus.UNPAID.equals(order.getPaymentStatus())) {
                payOSService.cancelPaymentLink(order);
            }
        }

        order.setStatus(newStatus);
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
                        "Đơn hàng chờ xác nhận chỉ có thể chuyển sang 'Đã duyệt' hoặc 'Đang xử lý'.");
            }
            return;
        }
        if (current == OrderStatus.AWAITING_PAYMENT) {
            if (next != OrderStatus.CONFIRMED && next != OrderStatus.AWAITING_REPLENISHMENT
                    && next != OrderStatus.PROCESSING) {
                throw new BadRequestException(
                        "Đơn hàng chờ thanh toán chỉ có thể chuyển sang 'Đang xử lý' hoặc 'Chờ bổ sung' sau khi thanh toán xong.");
            }
            return;
        }
        if (current == OrderStatus.AWAITING_REPLENISHMENT) {
            if (next != OrderStatus.CONFIRMED && next != OrderStatus.PROCESSING) {
                throw new BadRequestException(
                        "Đơn hàng thiếu hàng chỉ có thể chuyển sang 'Đang xử lý' sau khi nhập đủ.");
            }
            return;
        }
        if (current == OrderStatus.CONFIRMED) {
            if (next != OrderStatus.PROCESSING) {
                throw new BadRequestException("Đơn hàng đã duyệt chỉ có thể chuyển sang 'Đang xử lý'.");
            }
            return;
        }
        if (current == OrderStatus.PROCESSING) {
            if (next != OrderStatus.READY_FOR_PICKUP) {
                throw new BadRequestException(
                        "Đơn hàng ở trạng thái 'Đang xử lý' chỉ có thể chuyển sang 'Sẵn sàng lấy' hoặc 'Đang giao'.");
            }
            return;
        }
        if (current == OrderStatus.READY_FOR_PICKUP) {
            if (next != OrderStatus.SHIPPING) {
                throw new BadRequestException("Đơn hàng chờ lấy hàng chỉ có thể chuyển sang 'Đang giao'.");
            }
            return;
        }
        if (current == OrderStatus.SHIPPING) {
            if (next != OrderStatus.RECEIVED && next != OrderStatus.RETURNED) {
                throw new BadRequestException(
                        "Đơn hàng đang giao chỉ có thể chuyển sang 'Hoàn thành' hoặc 'Đã trả hàng'.");
            }
            return;
        }
        if (current == OrderStatus.RECEIVED && next != OrderStatus.COMPLETED) {
            throw new BadRequestException("ÄÆ¡n hÃ ng Ä‘Ã£ nháº­n chá»‰ cÃ³ thá»ƒ chuyá»ƒn sang 'HoÃ n thÃ nh'.");
        }
    }

    public List<Order> getOrdersByUserId(Long userId) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    // ══════════════════════════════════════════════════════════════════════
    // QUẢN LÝ ĐƠN HÀNG CHO CHI NHÁNH / KHO
    // ══════════════════════════════════════════════════════════════════════

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
                .orElseThrow(() -> new NotFoundException("Không tìm thấy đơn hàng cho chi nhánh này"));
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
                .orElseThrow(() -> new NotFoundException("Không tìm thấy đơn hàng ID: " + orderId));

        List<SubOrder> awaitingSubOrders = order.getSubOrders() == null
                ? Collections.emptyList()
                : order.getSubOrders().stream()
                        .filter(subOrder -> subOrder.getStatus() == OrderStatus.AWAITING_REPLENISHMENT)
                        .toList();

        if (awaitingSubOrders.isEmpty()) {
            throw new BadRequestException("Đơn hàng này không ở trạng thái chờ điều chuyển bổ sung.");
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
                .orElseThrow(() -> new NotFoundException("Không tìm thấy phần đơn cho chi nhánh này"));

        if (subOrder.getStatus() != OrderStatus.AWAITING_REPLENISHMENT) {
            throw new BadRequestException("Phần đơn này không ở trạng thái chờ điều chuyển bổ sung.");
        }

        return inventoryTransferService.createReplenishmentTransfersForSubOrder(subOrder).stream()
                .map(InventoryTransfer::getTransferCode)
                .toList();
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
        if (currentStatus == OrderStatus.SHIPPING && newStatus == OrderStatus.COMPLETED) {
            throw new BadRequestException("Phần đơn đang giao chỉ được hoàn tất khi khách hàng xác nhận đã nhận.");
        }
        validateStatusTransition(currentStatus, newStatus);

        if (newStatus == OrderStatus.CANCELLED) {
            releaseAllocatedInventoryForSubOrder(subOrder);
        }
        if (newStatus == OrderStatus.RECEIVED
                && !canManuallyConfirmReceived(resolveStatusUpdatedAt(subOrder))) {
            throw new BadRequestException("Chá»‰ Ä‘Æ°á»£c xÃ¡c nháº­n 'ÄÃ£ nháº­n hÃ ng' sau khi Ä‘Æ¡n Ä‘ang giao quÃ¡ 7 ngÃ y.");
        }
        if (newStatus == OrderStatus.RECEIVED && PaymentMethod.COD.equals(subOrder.getOrder().getPaymentMethod())) {
            subOrder.getOrder().setPaymentStatus(PaymentStatus.PAID);
            orderRepository.save(subOrder.getOrder());
        }

        subOrder.setStatus(newStatus);
        subOrderRepository.saveAndFlush(subOrder);

        syncMasterOrderStatus(subOrder.getOrder().getId());
    }

    private void syncMasterOrderStatus(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy đơn hàng tổng"));

        List<SubOrder> allSubs = subOrderRepository.findByOrderId(orderId);
        if (allSubs.isEmpty())
            return;

        List<SubOrder> activeSubs = allSubs.stream()
                .filter(s -> s.getStatus() != OrderStatus.CANCELLED && s.getStatus() != OrderStatus.RETURNED)
                .collect(Collectors.toList());

        OrderStatus newMasterStatus;
        if (activeSubs.isEmpty()) {
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

        // Fix lỗi: Khi tất cả SubOrder bị hủy, Master Order chuyển thành Hủy -> Hoàn
        // Voucher và Hủy link PayOS
        if (newMasterStatus == OrderStatus.CANCELLED && order.getStatus() != OrderStatus.CANCELLED) {
            restoreVoucherForOrder(order);
            if (PaymentMethod.PAYOS.equals(order.getPaymentMethod())
                    && PaymentStatus.UNPAID.equals(order.getPaymentStatus())) {
                payOSService.cancelPaymentLink(order);
            }
        }

        order.setStatus(newMasterStatus);
        orderRepository.saveAndFlush(order);

        if (newMasterStatus == OrderStatus.COMPLETED || newMasterStatus == OrderStatus.CANCELLED
                || newMasterStatus == OrderStatus.RETURNED) {
            customerService.evaluateAndHandleCustomerReputation(order.getUser().getId());
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
            log.warn("CÃ³ {} pháº§n Ä‘Æ¡n vÃ  {} Ä‘Æ¡n legacy Ä‘ang SHIPPING quÃ¡ {} ngÃ y, cáº§n xÃ¡c nháº­n RECEIVED thá»§ cÃ´ng.",
                    overdueSubOrders.size(),
                    overdueLegacyOrders.size(),
                    SHIPPING_RECEIVED_CONFIRM_AFTER_DAYS);
        }
    }

    private void markOrderAsReceived(Order order, boolean force) {
        if (!force && !canManuallyConfirmReceived(resolveStatusUpdatedAt(order))) {
            throw new BadRequestException("Chá»‰ Ä‘Æ°á»£c xÃ¡c nháº­n 'ÄÃ£ nháº­n hÃ ng' sau khi Ä‘Æ¡n Ä‘ang giao quÃ¡ 7 ngÃ y.");
        }

        order.setStatus(OrderStatus.RECEIVED);
        if (PaymentMethod.COD.equals(order.getPaymentMethod())) {
            order.setPaymentStatus(PaymentStatus.PAID);
        }
        orderRepository.save(order);

        if (order.getSubOrders() != null) {
            List<SubOrder> shippingSubOrders = order.getSubOrders().stream()
                    .filter(subOrder -> subOrder.getStatus() == OrderStatus.SHIPPING)
                    .peek(subOrder -> subOrder.setStatus(OrderStatus.RECEIVED))
                    .toList();
            if (!shippingSubOrders.isEmpty()) {
                subOrderRepository.saveAll(shippingSubOrders);
            }
        }
    }

    private void completeReceivedOrder(Order order) {
        order.setStatus(OrderStatus.COMPLETED);
        order.setPaymentStatus(PaymentStatus.PAID);
        orderRepository.save(order);

        if (order.getSubOrders() != null) {
            List<SubOrder> receivedSubOrders = order.getSubOrders().stream()
                    .filter(subOrder -> subOrder.getStatus() == OrderStatus.RECEIVED)
                    .peek(subOrder -> subOrder.setStatus(OrderStatus.COMPLETED))
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
                .paymentStatus(order.getPaymentStatus() != null ? order.getPaymentStatus().name() : "")
                .orderStatus(order.getStatus() != null ? order.getStatus().name() : "")
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

    // ══════════════════════════════════════════════════════════════════════
    // MAPPING LOGIC
    // ══════════════════════════════════════════════════════════════════════

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
                    .map(this::mapSubItemToResponse)
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
            branchName = singleBranch != null ? singleBranch.getName() : "Nhiều chi nhánh";
            branchPhone = singleBranch != null ? singleBranch.getPhone() : null;
            branchAddress = singleBranch != null ? singleBranch.getAddressDetail() : null;
        } else if (order.getSubOrders() != null && order.getSubOrders().size() > 1) {
            branchName = "Nhiều chi nhánh";
        } else {
            branchName = "Không xác định";
        }

        String statusStr = order.getStatus() != null ? order.getStatus().name() : "";

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
                .status(statusStr)
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
        String statusStr = subOrder.getStatus() != null ? subOrder.getStatus().name() : null;

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
                if (item.getProductVariant().getProduct().getProductImages() != null
                        && !item.getProductVariant().getProduct().getProductImages().isEmpty()) {
                    pImg = item.getProductVariant().getProduct().getProductImages().iterator().next().getImageUrl();
                }
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
        String pName = "Sản phẩm không xác định";
        String pSku = "N/A";
        String pImg = null;
        Long productId = null;

        if (item.getProductVariant() != null) {
            pSku = item.getProductVariant().getSku();
            if (item.getProductVariant().getProduct() != null) {
                productId = item.getProductVariant().getProduct().getId();
                pName = item.getProductVariant().getProduct().getName();
                if (item.getProductVariant().getProduct().getProductImages() != null
                        && !item.getProductVariant().getProduct().getProductImages().isEmpty()) {
                    pImg = item.getProductVariant().getProduct().getProductImages().iterator().next().getImageUrl();
                }
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
                        item.getAllocatedQuantity() != null ? item.getAllocatedQuantity() : item.getQuantity())
                .missingQuantity(item.getMissingQuantity() != null ? item.getMissingQuantity() : 0)
                .price(item.getUnitPrice() != null ? item.getUnitPrice() : BigDecimal.ZERO)
                .totalPrice(
                        item.getUnitPrice() != null ? item.getUnitPrice().multiply(new BigDecimal(item.getQuantity()))
                                : BigDecimal.ZERO)
                .canReview(canReview)
                .build();
    }

    // ══════════════════════════════════════════════════════════════════════
    // PREPARE & CONFIRM LOGIC (THUẬT TOÁN FIFO + LÔ HÀNG)
    // ══════════════════════════════════════════════════════════════════════

    public PrepareOrderResponse prepareOrder(Long userId, PrepareOrderRequest request) {
        com.zone.agri.entity.UserAddress addr = userAddressRepository
                .findByIdAndUserId(request.getUserAddressId(), userId)
                .orElseThrow(() -> new NotFoundException(
                        "Không tìm thấy địa chỉ trong sổ địa chỉ của bạn. ID: " + request.getUserAddressId()));

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
                log.warn("Geocode thất bại cho địa chỉ '{}', dùng tọa độ mặc định: {}", deliveryAddress,
                        e.getMessage());
                userLat = 10.0341;
                userLng = 105.7904;
            }
        }

        List<CartItemDto> finalCart = request.getCart();
        if (finalCart == null || finalCart.isEmpty()) {
            List<com.zone.agri.entity.CartItem> dbItems = cartItemRepository.findByUserId(userId);
            if (dbItems.isEmpty())
                throw new BadRequestException("Giỏ hàng của bạn đang trống");

            finalCart = dbItems.stream()
                    .map(item -> new CartItemDto(item.getProductVariant().getId(),
                            item.getQuantity()))
                    .collect(Collectors.toList());
        }

        finalCart = normalizeCartItems(finalCart);
        String voucherCode = normalizeVoucherCode(request.getVoucherCode());

        List<Long> variantIds = finalCart.stream().map(CartItemDto::getProductVariantId).distinct().toList();
        List<ProductVariant> variants = variantRepository.findAllById(variantIds);
        if (variants.size() != variantIds.size())
            throw new NotFoundException("Một hoặc nhiều sản phẩm không tồn tại");

        Map<Long, ProductVariant> variantMap = variants.stream()
                .collect(Collectors.toMap(ProductVariant::getId, Function.identity()));

        List<BranchWithRealDistance> nearestBranches = branchSearchService.findNearestBranches(userLat, userLng);
        if (nearestBranches.isEmpty())
            throw new NotFoundException("Không có chi nhánh hoạt động");

        nearestBranches = filterCustomerFulfillmentBranches(nearestBranches);
        if (nearestBranches.isEmpty())
            throw new NotFoundException("KhÃ´ng cÃ³ chi nhÃ¡nh bÃ¡n hÃ ng phÃ¹ há»£p");

        nearestBranches = filterCustomerFulfillmentBranches(nearestBranches);
        if (nearestBranches.isEmpty()) {
            throw new NotFoundException("KhÃ´ng cÃ³ chi nhÃ¡nh bÃ¡n hÃ ng phÃ¹ há»£p");
        }

        nearestBranches = filterCustomerFulfillmentBranches(nearestBranches);
        if (nearestBranches.isEmpty()) {
            throw new NotFoundException("Không có chi nhánh bán hàng phù hợp");
        }

        nearestBranches = filterCustomerFulfillmentBranches(nearestBranches);
        if (nearestBranches.isEmpty()) {
            throw new NotFoundException("KhÃ´ng cÃ³ chi nhÃ¡nh bÃ¡n hÃ ng phÃ¹ há»£p");
        }

        List<Long> branchIds = nearestBranches.stream().map(bwr -> bwr.branch().getId()).toList();

        Map<Long, Map<Long, List<Inventory>>> inventoryMatrix = allocationService.buildInventoryMatrix(branchIds,
                variantIds);
        AllocationResult allocation = allocationService.allocate(finalCart, variantMap, nearestBranches,
                inventoryMatrix);

        DeliveryInfo deliveryInfo = DeliveryInfo.builder()
                .toDistrictId(deliveryDistrictId).toWardCode(deliveryWardCode)
                .deliveryAddress(deliveryAddress).userLat(userLat).userLng(userLng).build();

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
            discountAmount = validateVoucher(previewUser, voucherCode, totalSubtotal, false, false).discountAmount();
        }

        BigDecimal totalAmount = totalSubtotal.add(totalShippingFee).subtract(discountAmount);
        if (totalAmount.compareTo(BigDecimal.ZERO) < 0) {
            totalAmount = BigDecimal.ZERO;
        }

        String token = UUID.randomUUID().toString();
        List<OrderItemDto> allFinalItems = enrichedSubOrders.stream().flatMap(s -> s.getItems().stream())
                .collect(Collectors.toList());
        Long mainBranchId = enrichedSubOrders.size() == 1 ? enrichedSubOrders.get(0).getBranchId() : null;

        PrepareOrderDraft draft = PrepareOrderDraft.builder()
                .prepareToken(token).userId(userId).voucherCode(voucherCode).branchId(mainBranchId)
                .finalItems(allFinalItems).cartItems(finalCart)
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
                .canFulfill(true) // Đánh lừa Frontend để luôn cho phép bấm nút Thanh toán
                .voucherCode(voucherCode)
                .canFulfill(allocation.outOfStockItems().isEmpty() && !enrichedSubOrders.isEmpty())
                .subOrders(enrichedSubOrders)
                .totalSubtotal(totalSubtotal)
                .discountAmount(discountAmount)
                .totalShippingFee(totalShippingFee)
                .totalAmount(totalAmount)
                .canFulfill(!enrichedSubOrders.isEmpty())
                .outOfStockItems(allocation.outOfStockItems())
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
                TimeUnit.SECONDS);
        if (Boolean.FALSE.equals(lockAcquired)) {
            ConfirmOrderResponse lockedResult = getConfirmResultFromRedis(request.getPrepareToken());
            if (lockedResult != null) {
                return lockedResult;
            }
            throw new ConflictException("Đơn hàng đang được xử lý, vui lòng đợi trong giây lát", true);
        }

        try {
            PrepareOrderDraft draft = getDraftFromRedis(request.getPrepareToken());
            if (draft == null)
                throw new BadRequestException("Token hết hạn");
            if (!userId.equals(draft.getUserId()))
                throw new BadRequestException("Token không hợp lệ");

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
                    draft.getVoucherCode());
            if (false && (!liveQuote.outOfStockItems().isEmpty() || liveQuote.subOrders().isEmpty())) {
                throw new ConflictException(
                        "Một hoặc nhiều sản phẩm không còn đủ hàng tại chi nhánh bán hàng gần nhất, vui lòng tải lại giỏ hàng",
                        true);
            }
            ensurePreparedQuoteStillValid(draft, liveQuote);
            VoucherValidation committedVoucher = validateVoucher(
                    user,
                    draft.getVoucherCode(),
                    liveQuote.totalSubtotal(),
                    true,
                    true);
            PaymentMethod paymentMethod = request.getPaymentMethod() != null ? request.getPaymentMethod()
                    : PaymentMethod.COD;
            boolean hasMissingItems = liveQuote.subOrders().stream()
                    .flatMap(subOrder -> subOrder.getItems().stream())
                    .anyMatch(item -> Objects.requireNonNullElse(item.getMissingQuantity(), 0) > 0);
            OrderStatus initialStatus = hasMissingItems
                    ? OrderStatus.AWAITING_REPLENISHMENT
                    : (PaymentMethod.PAYOS.equals(paymentMethod) ? OrderStatus.AWAITING_PAYMENT
                            : OrderStatus.PROCESSING);

            Order order = Order.builder().code("ORD" + System.currentTimeMillis()).user(user).status(initialStatus)
                    .paymentMethod(paymentMethod).paymentStatus(PaymentStatus.UNPAID).createdAt(LocalDateTime.now())
                    .totalAmount(liveQuote.totalSubtotal()).discountAmount(committedVoucher.discountAmount())
                    .finalAmount(liveQuote.totalAmount())
                    .totalShippingFee(liveQuote.totalShippingFee()).userLat(draft.getUserLat())
                    .userLng(draft.getUserLng())
                    .deliveryAddress(draft.getDeliveryAddress()).shippingAddress(draft.getDeliveryAddress())
                    .receiverName(draft.getReceiverName()).receiverPhone(draft.getReceiverPhone())
                    .voucher(committedVoucher.voucher()).note(request.getNote()).build();
            Order savedOrder = orderRepository.save(order);

            List<SubOrderSummaryDto> subOrderSummaries = new ArrayList<>();
            boolean anySubOrderMissing = hasMissingItems;
            List<Long> awaitingReplenishmentSubOrderIds = new ArrayList<>();

            for (SubOrderDraftDto subDraft : liveQuote.subOrders()) {
                Branch branch = branchRepository.findById(subDraft.getBranchId())
                        .orElseThrow(() -> new NotFoundException("Branch không tồn tại"));
                boolean subOrderHasMissingItems = subDraft.getItems().stream()
                        .anyMatch(item -> Objects.requireNonNullElse(item.getMissingQuantity(), 0) > 0);
                OrderStatus subOrderStatus = subOrderHasMissingItems
                        ? OrderStatus.AWAITING_REPLENISHMENT
                        : (PaymentMethod.PAYOS.equals(paymentMethod) ? OrderStatus.AWAITING_PAYMENT
                                : OrderStatus.PROCESSING);
                SubOrder subOrder = SubOrder.builder().order(savedOrder).branch(branch).status(subOrderStatus)
                        .subtotal(subDraft.getSubtotal()).shippingFee(subDraft.getShippingFee())
                        .estimatedDays(subDraft.getEstimatedDays()).carrier(subDraft.getCarrier()).build();
                SubOrder savedSubOrder = subOrderRepository.save(subOrder);
                boolean dynamicallyMissing = false;

                for (OrderItemDto item : subDraft.getItems()) {
                    ProductVariant variant = variantRepository.findById(item.getProductVariantId())
                            .orElseThrow(() -> new NotFoundException("Sản phẩm không tồn tại"));

                    int targetToDeduct = Objects.requireNonNullElse(item.getAllocatedQuantity(), item.getQuantity());
                    int originallyMissing = Objects.requireNonNullElse(item.getMissingQuantity(), 0);
                    int actuallyDeducted = 0;

                    List<Inventory> batches = inventoryRepository.findForUpdateFIFO(subDraft.getBranchId(),
                            item.getProductVariantId());

                    for (Inventory batch : batches) {
                        if (targetToDeduct <= 0)
                            break;
                        int available = Objects.requireNonNullElse(batch.getQuantity(), 0);
                        if (available <= 0)
                            continue;

                        int deductAmount = Math.min(available, targetToDeduct);
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

                        targetToDeduct -= deductAmount;
                        actuallyDeducted += deductAmount;
                    }

                    int finalMissing = originallyMissing + targetToDeduct;
                    int finalAllocated = actuallyDeducted;
                    if (finalMissing > 0)
                        dynamicallyMissing = true;

                    subOrderItemRepository.save(SubOrderItem.builder()
                            .subOrder(savedSubOrder)
                            .productVariant(variant)
                            .quantity(item.getQuantity())
                            .allocatedQuantity(finalAllocated)
                            .missingQuantity(finalMissing)
                            .unitPrice(item.getUnitPrice())
                            .build());
                }

                if (dynamicallyMissing && subOrderStatus == OrderStatus.PROCESSING) {
                    subOrderStatus = OrderStatus.AWAITING_REPLENISHMENT;
                    savedSubOrder.setStatus(subOrderStatus);
                    subOrderRepository.save(savedSubOrder);
                    anySubOrderMissing = true;
                }
                if (subOrderStatus == OrderStatus.AWAITING_REPLENISHMENT) {
                    awaitingReplenishmentSubOrderIds.add(savedSubOrder.getId());
                }

                subOrderSummaries.add(SubOrderSummaryDto.builder()
                        .subOrderId(savedSubOrder.getId()).branchId(branch.getId()).branchName(branch.getName())
                        .status(subOrderStatus.name()).subtotal(subDraft.getSubtotal())
                        .shippingFee(subDraft.getShippingFee())
                        .estimatedDays(subDraft.getEstimatedDays()).carrier(subDraft.getCarrier()).build());
            }

            if (anySubOrderMissing && initialStatus == OrderStatus.PROCESSING) {
                initialStatus = OrderStatus.AWAITING_REPLENISHMENT;
                savedOrder.setStatus(initialStatus);
                orderRepository.save(savedOrder);
            }

            if (!awaitingReplenishmentSubOrderIds.isEmpty()) {
                immediateReplenishmentService.scheduleAfterCommit(
                        awaitingReplenishmentSubOrderIds,
                        savedOrder.getCode());
            }

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
                    throw new BadRequestException("Lỗi tạo PayOS link: " + e.getMessage());
                }
            }

            redisTemplate.delete(PREPARE_KEY_PREFIX + request.getPrepareToken());
            cartSnapshot.stream().map(CartItemDto::getProductVariantId).distinct()
                    .forEach(vId -> cartItemRepository.findByUserIdAndProductVariantId(userId, vId)
                            .ifPresent(cartItemRepository::delete));

            ConfirmOrderResponse response = ConfirmOrderResponse.builder().orderId(savedOrder.getId())
                    .orderCode(savedOrder.getCode())
                    .status(initialStatus.name()).voucherCode(draft.getVoucherCode()).subOrders(subOrderSummaries)
                    .totalAmount(liveQuote.totalAmount())
                    .discountAmount(committedVoucher.discountAmount()).totalShippingFee(liveQuote.totalShippingFee())
                    .checkoutUrl(checkoutUrl).build();

            saveConfirmResultToRedis(request.getPrepareToken(), response);
            return response;
        } finally {
            redisTemplate.delete(confirmLockKey);
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // HELPERS & REDIS
    // ══════════════════════════════════════════════════════════════════════

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

    private PreparedQuote buildPreparedQuote(
            Long userId,
            List<CartItemDto> cartItems,
            Double userLat,
            Double userLng,
            String deliveryAddress,
            Integer deliveryDistrictId,
            String deliveryWardCode,
            String voucherCode) {
        List<CartItemDto> normalizedCart = normalizeCartItems(cartItems);
        if (normalizedCart.isEmpty()) {
            throw new BadRequestException("Giỏ hàng của bạn đang trống");
        }

        List<Long> variantIds = normalizedCart.stream().map(CartItemDto::getProductVariantId).distinct().toList();
        List<ProductVariant> variants = variantRepository.findAllById(variantIds);
        if (variants.size() != variantIds.size()) {
            throw new NotFoundException("Một hoặc nhiều sản phẩm không tồn tại");
        }

        Map<Long, ProductVariant> variantMap = variants.stream()
                .collect(Collectors.toMap(ProductVariant::getId, Function.identity()));

        double finalUserLat = userLat != null ? userLat : 10.0341;
        double finalUserLng = userLng != null ? userLng : 105.7904;

        List<BranchWithRealDistance> nearestBranches = branchSearchService.findNearestBranches(finalUserLat,
                finalUserLng);
        if (nearestBranches.isEmpty()) {
            throw new NotFoundException("Không có chi nhánh hoạt động");
        }

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
                .toWardCode(deliveryWardCode)
                .deliveryAddress(deliveryAddress)
                .userLat(finalUserLat)
                .userLng(finalUserLng)
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
        String normalizedVoucherCode = normalizeVoucherCode(voucherCode);
        if (normalizedVoucherCode != null) {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new NotFoundException("Người dùng không tồn tại"));
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
                totalAmount);
    }

    private void ensurePreparedQuoteStillValid(PrepareOrderDraft draft, PreparedQuote liveQuote) {
        if (!Objects.equals(buildSubOrderSignature(draft.getSubOrders()),
                buildSubOrderSignature(liveQuote.subOrders()))) {
            throw new ConflictException("Don hang vua thay doi, vui long tai lai gio hang truoc khi dat", true);
        }
    }


    private List<BranchWithRealDistance> filterCustomerFulfillmentBranches(List<BranchWithRealDistance> branches) {
        if (branches == null || branches.isEmpty()) {
            return Collections.emptyList();
        }

        return branches.stream()
                .filter(branchWithDistance -> isCustomerFulfillmentBranch(branchWithDistance.branch()))
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
                                            String.valueOf(Objects.requireNonNullElse(item.getAllocatedQuantity(), 0)),
                                            String.valueOf(Objects.requireNonNullElse(item.getMissingQuantity(), 0))))
                                    .collect(Collectors.joining(","));

                    return String.join("|",
                            String.valueOf(subOrder.getBranchId()),
                            Objects.toString(subOrder.getEstimatedDays(), ""),
                            Objects.toString(subOrder.getCarrier(), ""),
                            itemSignature);
                })
                .collect(Collectors.joining("||"));
    }

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
            boolean conflictOnUnavailable) {
        String normalizedVoucherCode = normalizeVoucherCode(voucherCode);
        if (normalizedVoucherCode == null) {
            return new VoucherValidation(null, null, BigDecimal.ZERO);
        }

        Voucher voucher = (consume ? voucherRepository.findByCodeForUpdate(normalizedVoucherCode)
                : voucherRepository.findByCode(normalizedVoucherCode))
                .orElseThrow(() -> voucherValidationException(conflictOnUnavailable, "Mã voucher không tồn tại"));

        LocalDateTime now = LocalDateTime.now();
        if (voucher.getStatus() != VoucherStatus.ACTIVE
                || voucher.getStartDate() == null
                || voucher.getEndDate() == null
                || now.isBefore(voucher.getStartDate())
                || now.isAfter(voucher.getEndDate())) {
            throw voucherValidationException(conflictOnUnavailable, "Voucher không hợp lệ hoặc đã hết hạn");
        }

        BigDecimal minOrderValue = voucher.getMinOrderValue() != null ? voucher.getMinOrderValue() : BigDecimal.ZERO;
        if (orderSubtotal.compareTo(minOrderValue) < 0) {
            throw voucherValidationException(conflictOnUnavailable,
                    "Đơn hàng chưa đạt giá trị tối thiểu để sử dụng voucher này");
        }

        if (Objects.requireNonNullElse(voucher.getQuantity(), 0) <= 0) {
            throw voucherValidationException(conflictOnUnavailable, "Voucher này đã hết lượt sử dụng trên hệ thống");
        }

        UserVoucher userVoucher = userVoucherRepository.findByUserAndVoucher(user, voucher)
                .orElse(new UserVoucher(user, voucher, 0, false));
        int maxUsagePerUser = voucher.getMaxUsagePerUser() != null ? voucher.getMaxUsagePerUser() : 1;
        if (Objects.requireNonNullElse(userVoucher.getUsageCount(), 0) >= maxUsagePerUser) {
            throw voucherValidationException(conflictOnUnavailable,
                    "Bạn đã sử dụng tối đa số lượt cho phép của voucher này");
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
                throw new ConflictException("Bạn đang thanh toán đồng thời với cùng voucher này. Vui lòng thử lại.");
            }
        }

        return new VoucherValidation(voucher, userVoucher, discountAmount);
    }

    private RuntimeException voucherValidationException(boolean conflictOnUnavailable, String message) {
        return conflictOnUnavailable ? new ConflictException(message) : new BadRequestException(message);
    }

    private void restoreVoucherForOrder(Order order) {
        if (order.getVoucher() != null) {
            Voucher voucher = voucherRepository.findByIdForUpdate(order.getVoucher().getId()).orElse(null);
            if (voucher != null) {
                voucher.setQuantity(Objects.requireNonNullElse(voucher.getQuantity(), 0) + 1);
                voucherRepository.save(voucher);

                userVoucherRepository.findByUserAndVoucher(order.getUser(), voucher).ifPresent(userVoucher -> {
                    int currentUsage = Objects.requireNonNullElse(userVoucher.getUsageCount(), 0);
                    if (currentUsage > 0) {
                        userVoucher.setUsageCount(currentUsage - 1);
                        userVoucherRepository.save(userVoucher);
                    }
                });
            }
        }
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

        List<InventoryTransaction> legacyTransactions = transactionRepository
                .findByReferenceCodeAndType(order.getCode(), TransactionType.SALE);
        releaseInventoryTransactions(order.getCode(), legacyTransactions, "Hoàn kho do hủy đơn hàng");
    }

    private void releaseAllocatedInventoryForSubOrder(SubOrder subOrder) {
        List<InventoryTransaction> saleTransactions = transactionRepository.findByReferenceCodeAndType(
                buildSubOrderReferenceCode(subOrder),
                TransactionType.SALE);
        releaseInventoryTransactions(buildSubOrderReferenceCode(subOrder), saleTransactions,
                "Hoàn kho do hủy phần đơn");
    }

    private void releaseInventoryTransactions(String referenceCode, List<InventoryTransaction> saleTransactions,
            String reason) {
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

            // Khi hủy đơn hoàn kho, tự động cấp lại cho các đơn đang thiếu hàng
            // tại cùng chi nhánh nếu có thể.
            if (inventory.getBranch() != null && inventory.getProductVariant() != null) {
                backorderService.fulfillBackordersOnStockReceive(
                        inventory.getBranch().getId(),
                        inventory.getProductVariant().getId(),
                        quantityToRelease);
            }
        }
    }

    private void saveDraftToRedis(String token, PrepareOrderDraft draft) {
        try {
            redisTemplate.opsForValue().set(PREPARE_KEY_PREFIX + token, objectMapper.writeValueAsString(draft),
                    PREPARE_TTL_MINUTES, TimeUnit.MINUTES);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Lỗi lưu Redis");
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
            log.warn("Không thể lưu kết quả xác nhận vào Redis cho token {}: {}", token, e.getMessage());
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

    // ══════════════════════════════════════════════════════════════════════
    // CHECKOUT API TRỰC TIẾP (CÓ ÁP DỤNG VOUCHER & PAYOS)
    // ══════════════════════════════════════════════════════════════════════

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
            if (selectedBranch == null)
                throw new BadRequestException("Không chi nhánh nào đủ hàng");
        }

        PaymentMethod paymentMethod = req.getPaymentMethod() != null ? req.getPaymentMethod() : PaymentMethod.COD;
        OrderStatus legacyInitialStatus = PaymentMethod.PAYOS.equals(paymentMethod)
                ? OrderStatus.AWAITING_PAYMENT
                : OrderStatus.PROCESSING;

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
                        .reason("Bán hàng trực tiếp (Đơn: " + savedOrder.getCode() + ")")
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
                throw new ConflictException("Hết hàng trong lúc thanh toán cho sản phẩm: " + variant.getSku());
            }
        }

        // 👉 VOUCHER LOGIC
        BigDecimal discountAmount = BigDecimal.ZERO;
        if (req.getVoucherCode() != null && !req.getVoucherCode().trim().isEmpty()) {
            VoucherValidation validation = validateVoucher(user, req.getVoucherCode(), subTotal, true, false);
            discountAmount = validation.discountAmount();
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
                com.zone.agri.dto.response.payment.PayOSApiResponse.PayOSLinkData payosData = payOSService
                        .createPaymentLink(savedOrder);
                savedOrder.setPayosPaymentLinkId(payosData.getPaymentLinkId());
                savedOrder.setPayosCheckoutUrl(payosData.getCheckoutUrl());
            } catch (Exception e) {
                throw new BadRequestException("Lỗi tạo PayOS link: " + e.getMessage());
            }
        }

        orderRepository.save(savedOrder);

        // Xóa sản phẩm khỏi giỏ hàng
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
