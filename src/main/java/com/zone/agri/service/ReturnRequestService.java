package com.zone.agri.service;

import com.zone.agri.common.AuthUtils;
import com.zone.agri.dto.request.returns.*;
import com.zone.agri.dto.response.returns.*;
import com.zone.agri.entity.*;
import com.zone.agri.entity.enums.*;
import com.zone.agri.exception.BadRequestException;
import com.zone.agri.exception.Forbidden;
import com.zone.agri.exception.NotFoundException;
import com.zone.agri.repository.*;
import com.zone.agri.utils.HaversineUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReturnRequestService {

    private static final double MAX_CASH_REFUND_DISTANCE_KM = 15d;
    private static final String CUSTOMER_RETURN_TAG = "CUSTOMER_RETURN";

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ReturnRequestRepository returnRequestRepository;
    private final ReturnRequestItemRepository returnRequestItemRepository;
    private final ReturnRequestEvidenceRepository returnRequestEvidenceRepository;
    private final SubOrderItemRepository subOrderItemRepository;
    private final ProductVariantRepository productVariantRepository;
    private final InventoryRepository inventoryRepository;
    private final InventoryNoteRepository inventoryNoteRepository;
    private final InventoryNoteDetailRepository inventoryNoteDetailRepository;
    private final InventoryTransactionRepository inventoryTransactionRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public ReturnOrderDraftResponse getReturnDraft(Long userId, Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy đơn hàng"));

        if (order.getUser() == null || !Objects.equals(order.getUser().getId(), userId)) {
            throw new Forbidden("Bạn không có quyền tạo yêu cầu cho đơn hàng này");
        }

        Optional<ReturnRequest> existingRequest = returnRequestRepository
                .findTopByUserIdAndOrderIdOrderByCreatedAtDesc(userId, orderId);
        if (existingRequest.isPresent()) {
            ReturnRequest request = existingRequest.get();
            return ReturnOrderDraftResponse.builder()
                    .orderId(order.getId())
                    .orderCode(order.getCode())
                    .orderStatus(order.getStatus() != null ? order.getStatus().name() : null)
                    .customerName(firstNonBlank(order.getReceiverName(), order.getUser() != null ? order.getUser().getFullName() : null))
                    .customerPhone(firstNonBlank(order.getReceiverPhone(), order.getUser() != null ? order.getUser().getPhoneNumber() : null))
                    .singleBranchOnly(Boolean.TRUE)
                    .canCreateRequest(Boolean.FALSE)
                    .existingRequestId(request.getId())
                    .existingRequestCode(request.getCode())
                    .message("Đơn hàng này đã có yêu cầu trả hàng. Bạn có thể theo dõi phiếu đã gửi trong tab Trả hàng.")
                    .items(List.of())
                    .build();
        }

        if (!isEligibleForCustomerReturn(order)) {
            throw new BadRequestException("Chỉ hỗ trợ trả hàng với đơn đã giao hoàn tất");
        }

        List<ReturnDraftItemResponse> items = new ArrayList<>();

        if (order.getSubOrders() != null && !order.getSubOrders().isEmpty()) {
            for (SubOrder subOrder : order.getSubOrders()) {
                Long branchId = subOrder.getBranch() != null ? subOrder.getBranch().getId() : null;
                String branchName = subOrder.getBranch() != null ? subOrder.getBranch().getName() : null;
                CashRefundEligibility cashRefundEligibility = resolveCashRefundEligibility(order, subOrder.getBranch());
                for (SubOrderItem item : Optional.ofNullable(subOrder.getItems()).orElse(List.of())) {
                    items.add(ReturnDraftItemResponse.builder()
                            .sourceType(ReturnItemSourceType.SUB_ORDER_ITEM)
                            .sourceItemId(item.getId())
                            .productVariantId(item.getProductVariant() != null ? item.getProductVariant().getId() : null)
                            .subOrderId(subOrder.getId())
                            .branchId(branchId)
                            .branchName(branchName)
                            .productName(resolveProductName(item.getProductVariant()))
                            .variantName(resolveVariantName(item.getProductVariant()))
                            .sku(item.getProductVariant() != null ? item.getProductVariant().getSku() : null)
                            .image(item.getProductVariant() != null ? item.getProductVariant().getImageUrl() : null)
                            .orderedQuantity(defaultQuantity(item.getQuantity()))
                            .maxReturnQuantity(defaultQuantity(item.getQuantity()))
                            .unitPrice(safeAmount(item.getUnitPrice()))
                            .totalPrice(safeAmount(item.getUnitPrice()).multiply(BigDecimal.valueOf(defaultQuantity(item.getQuantity()))))
                            .allowedRefundMethods(cashRefundEligibility.allowedRefundMethods())
                            .cashRefundEligible(cashRefundEligibility.cashRefundEligible())
                            .cashRefundDistanceKm(cashRefundEligibility.cashRefundDistanceKm())
                            .build());
                }
            }
        } else {
            Branch orderBranch = order.getBranch();
            Long branchId = orderBranch != null ? orderBranch.getId() : null;
            String branchName = orderBranch != null ? orderBranch.getName() : null;
            CashRefundEligibility cashRefundEligibility = resolveCashRefundEligibility(order, orderBranch);
            for (OrderItem item : Optional.ofNullable(order.getOrderItems()).orElse(List.of())) {
                items.add(ReturnDraftItemResponse.builder()
                        .sourceType(ReturnItemSourceType.ORDER_ITEM)
                        .sourceItemId(item.getId())
                        .productVariantId(item.getProductVariant() != null ? item.getProductVariant().getId() : null)
                        .subOrderId(null)
                        .branchId(branchId)
                        .branchName(branchName)
                        .productName(resolveProductName(item.getProductVariant()))
                        .variantName(resolveVariantName(item.getProductVariant()))
                        .sku(item.getProductVariant() != null ? item.getProductVariant().getSku() : null)
                        .image(item.getProductVariant() != null ? item.getProductVariant().getImageUrl() : null)
                        .orderedQuantity(defaultQuantity(item.getQuantity()))
                        .maxReturnQuantity(defaultQuantity(item.getQuantity()))
                        .unitPrice(safeAmount(item.getPrice()))
                        .totalPrice(safeAmount(item.getPrice()).multiply(BigDecimal.valueOf(defaultQuantity(item.getQuantity()))))
                        .allowedRefundMethods(cashRefundEligibility.allowedRefundMethods())
                        .cashRefundEligible(cashRefundEligibility.cashRefundEligible())
                        .cashRefundDistanceKm(cashRefundEligibility.cashRefundDistanceKm())
                        .build());
            }
        }

        return ReturnOrderDraftResponse.builder()
                .orderId(order.getId())
                .orderCode(order.getCode())
                .orderStatus(order.getStatus() != null ? order.getStatus().name() : null)
                .customerName(firstNonBlank(order.getReceiverName(), order.getUser() != null ? order.getUser().getFullName() : null))
                .customerPhone(firstNonBlank(order.getReceiverPhone(), order.getUser() != null ? order.getUser().getPhoneNumber() : null))
                .singleBranchOnly(Boolean.TRUE)
                .canCreateRequest(Boolean.TRUE)
                .existingRequestId(null)
                .existingRequestCode(null)
                .message("Các sản phẩm đã chọn hiện chưa thể gửi chung trong một yêu cầu. Vui lòng tách thành các yêu cầu riêng nếu cần.")
                .items(items)
                .build();
    }

    @Transactional
    public ReturnRequestResponse createReturnRequest(Long userId, CreateReturnRequest request) {
        Order order = orderRepository.findById(request.getOrderId())
                .orElseThrow(() -> new NotFoundException("Không tìm thấy đơn hàng"));

        if (order.getUser() == null || !Objects.equals(order.getUser().getId(), userId)) {
            throw new Forbidden("Bạn không có quyền tạo yêu cầu cho đơn hàng này");
        }

        if (returnRequestRepository.findTopByUserIdAndOrderIdOrderByCreatedAtDesc(userId, request.getOrderId()).isPresent()) {
            throw new BadRequestException("Đơn hàng này đã có yêu cầu trả hàng. Vui lòng theo dõi phiếu đã gửi trong tab Trả hàng.");
        }

        if (!isEligibleForCustomerReturn(order)) {
            throw new BadRequestException("Chỉ hỗ trợ trả hàng với đơn đã giao hoàn tất");
        }

        validateEvidenceRequirements(request.getEvidences());

        Map<Long, SubOrderItem> subOrderItemMap = loadSubOrderItems(request.getItems());
        Map<Long, OrderItem> orderItemMap = loadOrderItems(request.getItems());

        List<ResolvedReturnLine> resolvedLines = new ArrayList<>();
        Set<Long> branchIds = new LinkedHashSet<>();
        Branch resolvedBranch = null;
        BigDecimal totalRefundAmount = BigDecimal.ZERO;

        for (CreateReturnRequestItem itemRequest : request.getItems()) {
            ResolvedReturnLine line = resolveLine(order, itemRequest, subOrderItemMap, orderItemMap);
            resolvedLines.add(line);
            if (line.branchId() != null) {
                branchIds.add(line.branchId());
            }
            if (resolvedBranch == null) {
                resolvedBranch = line.branch();
            }
            totalRefundAmount = totalRefundAmount.add(line.refundAmount());
        }

        if (branchIds.size() > 1) {
            throw new BadRequestException("Các sản phẩm đã chọn hiện chưa thể gửi chung trong một yêu cầu. Vui lòng tách thành các yêu cầu riêng nếu cần.");
        }

        if (resolvedLines.isEmpty()) {
            throw new BadRequestException("Yêu cầu trả hàng phải có ít nhất 1 sản phẩm.");
        }

        ReturnHandlingOption handlingOption = resolveHandlingOption(request);
        boolean requiresPhysicalReturn = handlingOption == ReturnHandlingOption.RETURN_AND_REFUND;
        RefundPreference refundPreference = resolveRefundPreference(
                order,
                resolvedBranch != null ? resolvedBranch : order.getBranch(),
                request.getRefundMethod(),
                request.getBankAccountName(),
                request.getBankAccountNumber(),
                request.getBankName(),
                request.getBankBranch());

        ReturnRequest entity = ReturnRequest.builder()
                .code(generateReturnRequestCode())
                .status(ReturnRequestStatus.PENDING)
                .issueType(request.getIssueType())
                .refundMethod(refundPreference.refundMethod())
                .requiresPhysicalReturn(requiresPhysicalReturn)
                .customerName(request.getFullName().trim())
                .customerPhone(request.getPhoneNumber().trim())
                .customerEmail(trimToNull(request.getEmail()))
                .bankAccountName(refundPreference.bankAccountName())
                .bankAccountNumber(refundPreference.bankAccountNumber())
                .bankName(refundPreference.bankName())
                .bankBranch(refundPreference.bankBranch())
                .reason(request.getReason().trim())
                .description(request.getDescription().trim())
                .totalRefundAmount(totalRefundAmount)
                .user(order.getUser())
                .order(order)
                .branch(resolvedBranch != null ? resolvedBranch : order.getBranch())
                .build();

        ReturnRequest saved = returnRequestRepository.save(entity);

        List<ReturnRequestItem> savedItems = resolvedLines.stream()
                .map(line -> (ReturnRequestItem) ReturnRequestItem.builder()
                        .returnRequest(saved)
                        .sourceType(line.sourceType())
                        .sourceItemId(line.sourceItemId())
                        .productVariantId(line.productVariantId())
                        .subOrderId(line.subOrderId())
                        .productName(line.productName())
                        .variantName(line.variantName())
                        .sku(line.sku())
                        .imageUrl(line.imageUrl())
                        .quantity(line.quantity())
                        .orderedQuantity(line.orderedQuantity())
                        .unitPrice(line.unitPrice())
                        .refundAmount(line.refundAmount())
                        .restockQuantity(0)
                        .defectiveQuantity(0)
                        .build())
                .collect(Collectors.toList());
        returnRequestItemRepository.saveAll(savedItems);

        List<ReturnRequestEvidence> savedEvidences = request.getEvidences().stream()
                .map(evidence -> (ReturnRequestEvidence) ReturnRequestEvidence.builder()
                        .returnRequest(saved)
                        .mediaType(evidence.getMediaType())
                        .fileUrl(evidence.getFileUrl().trim())
                        .publicId(trimToNull(evidence.getPublicId()))
                        .fileName(trimToNull(evidence.getFileName()))
                        .build())
                .collect(Collectors.toList());
        returnRequestEvidenceRepository.saveAll(savedEvidences);

        saved.setItems(new LinkedHashSet<>(savedItems));
        saved.setEvidences(new LinkedHashSet<>(savedEvidences));
        return mapResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<ReturnRequestResponse> getMyReturnRequests(Long userId) {
        return returnRequestRepository.findAllDetailedForUser(userId).stream()
                .map(this::mapResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ReturnRequestResponse getMyReturnRequestDetail(Long userId, Long requestId) {
        ReturnRequest entity = returnRequestRepository.findDetailedByIdAndUserId(requestId, userId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy yêu cầu trả hàng"));
        return mapResponse(entity);
    }

    @Transactional(readOnly = true)
    public List<ReturnRequestResponse> getAdminReturnRequests(String status, String search) {
        return searchVisible(null, status, search);
    }

    @Transactional(readOnly = true)
    public List<ReturnRequestResponse> getBranchReturnRequests(Long branchId, String status, String search) {
        return searchVisible(branchId, status, search);
    }

    @Transactional(readOnly = true)
    public ReturnRequestResponse getAdminReturnRequestDetail(Long requestId) {
        ReturnRequest entity = returnRequestRepository.findDetailedById(requestId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy yêu cầu trả hàng"));
        return mapResponse(entity);
    }

    @Transactional(readOnly = true)
    public ReturnRequestResponse getBranchReturnRequestDetail(Long branchId, Long requestId) {
        ReturnRequest entity = getDetailedAndCheckBranch(requestId, branchId);
        return mapResponse(entity);
    }

    @Transactional
    public ReturnRequestResponse approveForAdmin(Long requestId, ReturnRequestApproveRequest request) {
        ReturnRequest entity = getDetailed(requestId);
        applyApprove(entity, request != null ? request.getInternalNote() : null);
        return mapResponse(entity);
    }

    @Transactional
    public ReturnRequestResponse approveForBranch(Long branchId, Long requestId, ReturnRequestApproveRequest request) {
        ReturnRequest entity = getDetailedAndCheckBranch(requestId, branchId);
        applyApprove(entity, request != null ? request.getInternalNote() : null);
        return mapResponse(entity);
    }

    @Transactional
    public ReturnRequestResponse rejectForAdmin(Long requestId, ReturnRequestRejectRequest request) {
        ReturnRequest entity = getDetailed(requestId);
        applyReject(entity, request);
        return mapResponse(entity);
    }

    @Transactional
    public ReturnRequestResponse rejectForBranch(Long branchId, Long requestId, ReturnRequestRejectRequest request) {
        ReturnRequest entity = getDetailedAndCheckBranch(requestId, branchId);
        applyReject(entity, request);
        return mapResponse(entity);
    }

    @Transactional
    public ReturnRequestResponse receiveForAdmin(Long requestId, ReturnRequestReceiveRequest request) {
        ReturnRequest entity = getDetailed(requestId);
        applyReceive(entity, request);
        return mapResponse(entity);
    }

    @Transactional
    public ReturnRequestResponse receiveForBranch(Long branchId, Long requestId, ReturnRequestReceiveRequest request) {
        ReturnRequest entity = getDetailedAndCheckBranch(requestId, branchId);
        applyReceive(entity, request);
        return mapResponse(entity);
    }

    @Transactional
    public ReturnRequestResponse refundForAdmin(Long requestId, ReturnRequestRefundRequest request) {
        ReturnRequest entity = getDetailed(requestId);
        applyRefund(entity, request);
        return mapResponse(entity);
    }

    @Transactional
    public ReturnRequestResponse refundForBranch(Long branchId, Long requestId, ReturnRequestRefundRequest request) {
        ReturnRequest entity = getDetailedAndCheckBranch(requestId, branchId);
        applyRefund(entity, request);
        return mapResponse(entity);
    }

    private List<ReturnRequestResponse> searchVisible(Long branchId, String status, String search) {
        ReturnRequestStatus parsedStatus = parseStatus(status);
        return returnRequestRepository.searchVisible(branchId, parsedStatus, trimToNull(search)).stream()
                .map(this::mapResponse)
                .toList();
    }

    private ReturnRequest getDetailed(Long requestId) {
        return returnRequestRepository.findDetailedById(requestId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy yêu cầu trả hàng"));
    }

    private ReturnRequest getDetailedAndCheckBranch(Long requestId, Long branchId) {
        ReturnRequest entity = getDetailed(requestId);
        Long requestBranchId = entity.getBranch() != null ? entity.getBranch().getId() : null;
        if (!Objects.equals(requestBranchId, branchId)) {
            throw new Forbidden("Yêu cầu trả hàng này không thuộc chi nhánh của bạn");
        }
        return entity;
    }

    private void applyApprove(ReturnRequest entity, String internalNote) {
        if (entity.getStatus() != ReturnRequestStatus.PENDING) {
            throw new BadRequestException("Chỉ có thể duyệt yêu cầu đang chờ xử lý");
        }
        entity.setStatus(ReturnRequestStatus.APPROVED);
        entity.setApprovedAt(LocalDateTime.now());
        if (internalNote != null) {
            entity.setInternalNote(trimToNull(internalNote));
        }
    }

    private void applyReject(ReturnRequest entity, ReturnRequestRejectRequest request) {
        if (entity.getStatus() == ReturnRequestStatus.REFUNDED || entity.getStatus() == ReturnRequestStatus.REJECTED) {
            throw new BadRequestException("Yêu cầu đã kết thúc nên không thể từ chối thêm");
        }
        entity.setStatus(ReturnRequestStatus.REJECTED);
        entity.setRejectReason(request.getRejectReason().trim());
        entity.setRejectedAt(LocalDateTime.now());
        entity.setInternalNote(trimToNull(request.getInternalNote()));
    }

    private void applyReceive(ReturnRequest entity, ReturnRequestReceiveRequest request) {
        if (Boolean.FALSE.equals(entity.getRequiresPhysicalReturn())) {
            throw new BadRequestException("Yêu cầu thiếu hàng không cần bước nhận lại hàng");
        }
        if (entity.getStatus() != ReturnRequestStatus.APPROVED) {
            throw new BadRequestException("Chỉ nhận lại hàng sau khi yêu cầu đã được duyệt");
        }
        ReceiveDecision receiveDecision = buildReceiveDecision(entity, request);
        InventoryNote receivedInventoryNote = createReceivedInventoryNote(entity, receiveDecision);

        for (ReceiveItemDecision itemDecision : receiveDecision.items()) {
            itemDecision.returnItem().setRestockQuantity(itemDecision.restockQuantity());
            itemDecision.returnItem().setDefectiveQuantity(itemDecision.defectiveQuantity());
        }

        entity.setStatus(ReturnRequestStatus.RECEIVED);
        entity.setReceivedAt(LocalDateTime.now());
        entity.setInternalNote(receiveDecision.internalNote());
        entity.setReceivedInventoryNote(receivedInventoryNote);
    }

    private void applyRefund(ReturnRequest entity, ReturnRequestRefundRequest request) {
        if (entity.getStatus() == ReturnRequestStatus.REFUNDED || entity.getStatus() == ReturnRequestStatus.REJECTED) {
            throw new BadRequestException("Yêu cầu đã kết thúc nên không thể hoàn tiền thêm");
        }
        if (Boolean.TRUE.equals(entity.getRequiresPhysicalReturn()) && entity.getStatus() != ReturnRequestStatus.RECEIVED) {
            throw new BadRequestException("Cần xác nhận đã nhận lại hàng trước khi hoàn tiền");
        }
        if (Boolean.FALSE.equals(entity.getRequiresPhysicalReturn()) && entity.getStatus() != ReturnRequestStatus.APPROVED) {
            throw new BadRequestException("Yêu cầu thiếu hàng phải được duyệt trước khi hoàn tiền");
        }
        ReturnRefundMethod refundMethod = resolveRefundMethodForSettlement(entity, request.getRefundMethod());
        entity.setRefundMethod(refundMethod);
        entity.setTotalRefundAmount(safeAmount(request.getRefundAmount()));
        entity.setStatus(ReturnRequestStatus.REFUNDED);
        entity.setRefundedAt(LocalDateTime.now());
        entity.setInternalNote(trimToNull(request.getInternalNote()));
    }

    private Map<Long, SubOrderItem> loadSubOrderItems(List<CreateReturnRequestItem> items) {
        List<Long> ids = items.stream()
                .filter(item -> item.getSourceType() == ReturnItemSourceType.SUB_ORDER_ITEM)
                .map(CreateReturnRequestItem::getSourceItemId)
                .distinct()
                .toList();

        if (ids.isEmpty()) {
            return Map.of();
        }

        return subOrderItemRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(SubOrderItem::getId, Function.identity()));
    }

    private Map<Long, OrderItem> loadOrderItems(List<CreateReturnRequestItem> items) {
        List<Long> ids = items.stream()
                .filter(item -> item.getSourceType() == ReturnItemSourceType.ORDER_ITEM)
                .map(CreateReturnRequestItem::getSourceItemId)
                .distinct()
                .toList();

        if (ids.isEmpty()) {
            return Map.of();
        }

        return orderItemRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(OrderItem::getId, Function.identity()));
    }

    private ResolvedReturnLine resolveLine(
            Order order,
            CreateReturnRequestItem itemRequest,
            Map<Long, SubOrderItem> subOrderItemMap,
            Map<Long, OrderItem> orderItemMap
    ) {
        if (itemRequest.getSourceType() == ReturnItemSourceType.SUB_ORDER_ITEM) {
            SubOrderItem item = subOrderItemMap.get(itemRequest.getSourceItemId());
            if (item == null || item.getSubOrder() == null || item.getSubOrder().getOrder() == null) {
                throw new BadRequestException("Không tìm thấy dòng sản phẩm nguồn cho yêu cầu trả hàng");
            }
            if (!Objects.equals(item.getSubOrder().getOrder().getId(), order.getId())) {
                throw new BadRequestException("Có dòng sản phẩm không thuộc đơn hàng đã chọn");
            }
            int orderedQuantity = defaultQuantity(item.getQuantity());
            int requestedQuantity = defaultQuantity(itemRequest.getQuantity());
            if (requestedQuantity > orderedQuantity) {
                throw new BadRequestException("Số lượng trả vượt quá số lượng đã mua");
            }
            ProductVariant variant = item.getProductVariant();
            BigDecimal unitPrice = safeAmount(item.getUnitPrice());
            Branch branch = item.getSubOrder().getBranch();
            return new ResolvedReturnLine(
                    ReturnItemSourceType.SUB_ORDER_ITEM,
                    item.getId(),
                    variant != null ? variant.getId() : null,
                    item.getSubOrder().getId(),
                    branch != null ? branch.getId() : null,
                    branch,
                    resolveProductName(variant),
                    resolveVariantName(variant),
                    variant != null ? variant.getSku() : null,
                    variant != null ? variant.getImageUrl() : null,
                    requestedQuantity,
                    orderedQuantity,
                    unitPrice,
                    unitPrice.multiply(BigDecimal.valueOf(requestedQuantity))
            );
        }

        OrderItem item = orderItemMap.get(itemRequest.getSourceItemId());
        if (item == null || item.getOrder() == null) {
            throw new BadRequestException("Không tìm thấy dòng sản phẩm nguồn cho yêu cầu trả hàng");
        }
        if (!Objects.equals(item.getOrder().getId(), order.getId())) {
            throw new BadRequestException("Có dòng sản phẩm không thuộc đơn hàng đã chọn");
        }
        int orderedQuantity = defaultQuantity(item.getQuantity());
        int requestedQuantity = defaultQuantity(itemRequest.getQuantity());
        if (requestedQuantity > orderedQuantity) {
            throw new BadRequestException("Số lượng trả vượt quá số lượng đã mua");
        }
        ProductVariant variant = item.getProductVariant();
        BigDecimal unitPrice = safeAmount(item.getPrice());
        Branch branch = order.getBranch();
        return new ResolvedReturnLine(
                ReturnItemSourceType.ORDER_ITEM,
                item.getId(),
                variant != null ? variant.getId() : null,
                null,
                branch != null ? branch.getId() : null,
                branch,
                resolveProductName(variant),
                resolveVariantName(variant),
                variant != null ? variant.getSku() : null,
                variant != null ? variant.getImageUrl() : null,
                requestedQuantity,
                orderedQuantity,
                unitPrice,
                unitPrice.multiply(BigDecimal.valueOf(requestedQuantity))
        );
    }

    private void validateEvidenceRequirements(List<CreateReturnRequestEvidence> evidences) {
        boolean hasImage = evidences.stream().anyMatch(evidence -> evidence.getMediaType() == ReturnEvidenceType.IMAGE);
        boolean hasVideo = evidences.stream().anyMatch(evidence -> evidence.getMediaType() == ReturnEvidenceType.VIDEO);
        if (!hasImage || !hasVideo) {
            throw new BadRequestException("Yêu cầu trả hàng phải có ít nhất 1 hình ảnh và 1 video làm bằng chứng");
        }
    }

    private boolean isEligibleForCustomerReturn(Order order) {
        if (order == null) {
            return false;
        }

        if (order.getReceivedAt() != null || order.getStatus() == OrderStatus.RECEIVED) {
            return true;
        }

        if (order.getStatus() != OrderStatus.COMPLETED) {
            return false;
        }

        return !canCustomerStillConfirmReceived(order);
    }

    private boolean canCustomerStillConfirmReceived(Order order) {
        if (order == null || order.getReceivedAt() != null) {
            return false;
        }

        if (order.getStatus() != OrderStatus.SHIPPING && order.getStatus() != OrderStatus.COMPLETED) {
            return false;
        }

        LocalDateTime shippingStartedAt = resolveShippingStartedAt(order);
        if (shippingStartedAt == null) {
            return order.getStatus() == OrderStatus.SHIPPING;
        }

        return !shippingStartedAt.isBefore(LocalDateTime.now().minusHours(72));
    }

    private LocalDateTime resolveShippingStartedAt(Order order) {
        if (order == null) {
            return null;
        }

        if (order.getShippingStartedAt() != null) {
            return order.getShippingStartedAt();
        }

        if (order.getSubOrders() != null && !order.getSubOrders().isEmpty()) {
            LocalDateTime subOrderShippingStartedAt = order.getSubOrders().stream()
                    .map(SubOrder::getShippingStartedAt)
                    .filter(Objects::nonNull)
                    .max(LocalDateTime::compareTo)
                    .orElse(null);
            if (subOrderShippingStartedAt != null) {
                return subOrderShippingStartedAt;
            }
        }

        if (order.getCompletedAt() != null) {
            return order.getCompletedAt();
        }

        if (order.getUpdatedAt() != null) {
            return order.getUpdatedAt();
        }

        return order.getCreatedAt();
    }

    private ReceiveDecision buildReceiveDecision(ReturnRequest entity, ReturnRequestReceiveRequest request) {
        List<ReturnRequestItem> returnItems = Optional.ofNullable(entity.getItems())
                .orElse(Collections.emptySet())
                .stream()
                .sorted(Comparator.comparing(ReturnRequestItem::getId, Comparator.nullsLast(Long::compareTo)))
                .toList();
        if (returnItems.isEmpty()) {
            throw new BadRequestException("Yêu cầu trả hàng này chưa có sản phẩm để xác nhận nhận lại.");
        }

        List<ReturnRequestReceiveItemRequest> payloadItems = request != null ? request.getItems() : null;
        if (payloadItems == null || payloadItems.isEmpty()) {
            throw new BadRequestException("Vui lòng nhập số lượng nhập lại và số lượng vào tồn lỗi cho từng sản phẩm.");
        }

        Map<Long, ReturnRequestReceiveItemRequest> payloadByItemId = new LinkedHashMap<>();
        for (ReturnRequestReceiveItemRequest payloadItem : payloadItems) {
            if (payloadItem == null || payloadItem.getReturnRequestItemId() == null) {
                throw new BadRequestException("Thiếu thông tin dòng sản phẩm khi xác nhận nhận lại hàng.");
            }
            if (payloadByItemId.put(payloadItem.getReturnRequestItemId(), payloadItem) != null) {
                throw new BadRequestException("Có sản phẩm bị gửi lặp trong xác nhận nhận lại hàng.");
            }
        }

        if (payloadByItemId.size() != returnItems.size()) {
            throw new BadRequestException("Cần nhập đủ kết quả xử lý kho cho toàn bộ sản phẩm của phiếu trả hàng.");
        }

        Branch receivingBranch = entity.getBranch() != null ? entity.getBranch() : entity.getOrder() != null
                ? entity.getOrder().getBranch()
                : null;
        if (receivingBranch == null) {
            throw new BadRequestException("Không xác định được điểm xử lý để nhập lại hàng trả về.");
        }

        Map<Long, ProductVariant> variantsById = productVariantRepository.findAllById(
                        returnItems.stream()
                                .map(ReturnRequestItem::getProductVariantId)
                                .filter(Objects::nonNull)
                                .distinct()
                                .toList())
                .stream()
                .collect(Collectors.toMap(ProductVariant::getId, Function.identity()));

        Map<LineagePoolKey, Deque<SourceBatchState>> lineagePools = new HashMap<>();
        List<ReceiveItemDecision> decisions = new ArrayList<>();
        for (ReturnRequestItem returnItem : returnItems) {
            ReturnRequestReceiveItemRequest payloadItem = payloadByItemId.get(returnItem.getId());
            if (payloadItem == null) {
                throw new BadRequestException("Thiếu kết quả xử lý kho cho sản phẩm " + firstNonBlank(returnItem.getProductName(), "đã chọn") + ".");
            }

            int expectedQuantity = defaultQuantity(returnItem.getQuantity());
            int restockQuantity = defaultQuantity(payloadItem.getRestockQuantity());
            int defectiveQuantity = defaultQuantity(payloadItem.getDefectiveQuantity());
            if (restockQuantity + defectiveQuantity != expectedQuantity) {
                throw new BadRequestException("Sản phẩm " + firstNonBlank(returnItem.getProductName(), "đã chọn")
                        + " phải có tổng số lượng nhập lại và tồn lỗi đúng bằng số lượng trả.");
            }

            ProductVariant variant = variantsById.get(returnItem.getProductVariantId());
            if (variant == null) {
                throw new BadRequestException("Không tìm thấy biến thể sản phẩm để nhập kho cho SKU "
                        + firstNonBlank(returnItem.getSku(), "không xác định") + ".");
            }

            String referenceCode = resolveReturnReferenceCode(entity, returnItem);
            List<InboundReturnAllocation> allocations = resolveInboundReturnAllocations(
                    entity,
                    returnItem,
                    variant,
                    expectedQuantity,
                    referenceCode,
                    lineagePools);

            decisions.add(new ReceiveItemDecision(
                    returnItem,
                    variant,
                    restockQuantity,
                    defectiveQuantity,
                    trimToNull(payloadItem.getItemNote()),
                    allocations
            ));
        }

        return new ReceiveDecision(receivingBranch, trimToNull(request != null ? request.getInternalNote() : null), decisions);
    }

    private InventoryNote createReceivedInventoryNote(ReturnRequest entity, ReceiveDecision receiveDecision) {
        LocalDateTime now = LocalDateTime.now();
        User actor = resolveCurrentActor();

        InventoryNote note = InventoryNote.builder()
                .code(generateCustomerReturnReceiptCode())
                .type(InventoryNoteType.IMPORT)
                .reason("Nhập lại hàng khách trả từ phiếu " + entity.getCode())
                .status(InventoryNoteStatus.COMPLETED)
                .totalAmount(BigDecimal.ZERO)
                .createdAt(now)
                .note(buildCustomerReturnNote(entity, receiveDecision.internalNote()))
                .tags(CUSTOMER_RETURN_TAG)
                .deliverer(firstNonBlank(entity.getCustomerName(), "Khách trả hàng"))
                .entryDate(now)
                .paymentAmount(BigDecimal.ZERO)
                .debtAmount(BigDecimal.ZERO)
                .branch(receiveDecision.receivingBranch())
                .createdBy(actor)
                .build();
        InventoryNote savedNote = inventoryNoteRepository.save(note);

        List<InventoryNoteDetail> noteDetails = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (ReceiveItemDecision itemDecision : receiveDecision.items()) {
            int remainingRestock = itemDecision.restockQuantity();
            int remainingDefective = itemDecision.defectiveQuantity();

            for (InboundReturnAllocation allocation : itemDecision.allocations()) {
                int availableInAllocation = allocation.quantity();

                int acceptedQuantity = Math.min(remainingRestock, availableInAllocation);
                remainingRestock -= acceptedQuantity;
                availableInAllocation -= acceptedQuantity;

                int defectiveQuantity = Math.min(remainingDefective, availableInAllocation);
                remainingDefective -= defectiveQuantity;

                if (acceptedQuantity + defectiveQuantity <= 0) {
                    continue;
                }

                BigDecimal linePrice = resolveAllocationImportPrice(allocation.importPrice(), itemDecision.returnItem().getUnitPrice());
                noteDetails.add(InventoryNoteDetail.builder()
                        .inventoryNote(savedNote)
                        .productVariant(itemDecision.variant())
                        .quantity(acceptedQuantity + defectiveQuantity)
                        .price(linePrice)
                        .quantityRequested(acceptedQuantity + defectiveQuantity)
                        .quantityReal(acceptedQuantity + defectiveQuantity)
                        .quantityAccepted(acceptedQuantity)
                        .quantityRejected(defectiveQuantity)
                        .batchNumber(allocation.batchNumber())
                        .expiryDate(allocation.expiryDate())
                        .note(firstNonBlank(itemDecision.itemNote(), receiveDecision.internalNote()))
                        .build());

                totalAmount = totalAmount.add(linePrice.multiply(BigDecimal.valueOf(acceptedQuantity + defectiveQuantity)));
                applyInboundReturnAllocation(
                        savedNote,
                        receiveDecision.receivingBranch(),
                        itemDecision.variant(),
                        allocation,
                        acceptedQuantity,
                        defectiveQuantity,
                        actor,
                        entity.getCode());
            }
        }

        if (!noteDetails.isEmpty()) {
            inventoryNoteDetailRepository.saveAll(noteDetails);
        }
        savedNote.setDetails(noteDetails);
        savedNote.setTotalAmount(totalAmount);
        return inventoryNoteRepository.save(savedNote);
    }

    private void applyInboundReturnAllocation(
            InventoryNote note,
            Branch branch,
            ProductVariant variant,
            InboundReturnAllocation allocation,
            int acceptedQuantity,
            int defectiveQuantity,
            User actor,
            String returnRequestCode
    ) {
        if (acceptedQuantity <= 0 && defectiveQuantity <= 0) {
            return;
        }

        BigDecimal importPrice = resolveAllocationImportPrice(allocation.importPrice(), BigDecimal.ZERO);
        String batchNumber = trimToNull(allocation.batchNumber());
        Inventory inventory = inventoryRepository.findExactBatchWithLock(branch, variant, batchNumber, importPrice, allocation.expiryDate())
                .orElseGet(() -> inventoryRepository.save(Inventory.builder()
                        .branch(branch)
                        .productVariant(variant)
                        .batchNumber(batchNumber)
                        .importPrice(importPrice)
                        .expiryDate(allocation.expiryDate())
                        .quantity(0)
                        .defectiveQuantity(0)
                        .build()));

        if (acceptedQuantity > 0) {
            inventory.setQuantity(Objects.requireNonNullElse(inventory.getQuantity(), 0) + acceptedQuantity);
        }
        if (defectiveQuantity > 0) {
            inventory.setDefectiveQuantity(Objects.requireNonNullElse(inventory.getDefectiveQuantity(), 0) + defectiveQuantity);
        }
        inventory.setLastReceiptDate(LocalDateTime.now());
        inventory = inventoryRepository.save(inventory);

        int balanceAfterAccepted = physicalBalance(inventory) - defectiveQuantity;
        if (acceptedQuantity > 0) {
            inventoryTransactionRepository.save(InventoryTransaction.builder()
                    .type(TransactionType.IMPORT)
                    .quantityChange(acceptedQuantity)
                    .newBalance(balanceAfterAccepted)
                    .referenceCode(note.getCode())
                    .reason("Nhập lại hàng khách trả đạt điều kiện (" + returnRequestCode + ")")
                    .createdAt(LocalDateTime.now())
                    .inventory(inventory)
                    .inventoryNote(note)
                    .createdBy(actor)
                    .build());
        }
        if (defectiveQuantity > 0) {
            inventoryTransactionRepository.save(InventoryTransaction.builder()
                    .type(TransactionType.IMPORT)
                    .quantityChange(defectiveQuantity)
                    .newBalance(physicalBalance(inventory))
                    .referenceCode(note.getCode())
                    .reason("Nhập lại hàng khách trả vào tồn lỗi (" + returnRequestCode + ")")
                    .createdAt(LocalDateTime.now())
                    .inventory(inventory)
                    .inventoryNote(note)
                    .createdBy(actor)
                    .build());
        }
    }

    private List<InboundReturnAllocation> resolveInboundReturnAllocations(
            ReturnRequest entity,
            ReturnRequestItem returnItem,
            ProductVariant variant,
            int quantity,
            String referenceCode,
            Map<LineagePoolKey, Deque<SourceBatchState>> lineagePools
    ) {
        LineagePoolKey key = new LineagePoolKey(referenceCode, variant.getId());
        Deque<SourceBatchState> sourcePool = lineagePools.computeIfAbsent(
                key,
                ignored -> loadSaleLineagePool(entity, returnItem, variant, referenceCode));

        List<InboundReturnAllocation> allocations = new ArrayList<>();
        int remaining = quantity;
        while (remaining > 0 && sourcePool != null && !sourcePool.isEmpty()) {
            SourceBatchState state = sourcePool.peekFirst();
            if (state == null || state.remainingQuantity() <= 0) {
                sourcePool.pollFirst();
                continue;
            }

            int allocatedQuantity = Math.min(remaining, state.remainingQuantity());
            allocations.add(new InboundReturnAllocation(
                    state.batchNumber(),
                    state.importPrice(),
                    state.expiryDate(),
                    allocatedQuantity));
            state.consume(allocatedQuantity);
            if (state.remainingQuantity() <= 0) {
                sourcePool.pollFirst();
            }
            remaining -= allocatedQuantity;
        }

        if (remaining > 0) {
            allocations.add(new InboundReturnAllocation(
                    buildFallbackReturnBatchNumber(entity),
                    safeAmount(returnItem.getUnitPrice()),
                    null,
                    remaining));
        }

        return allocations;
    }

    private Deque<SourceBatchState> loadSaleLineagePool(
            ReturnRequest entity,
            ReturnRequestItem returnItem,
            ProductVariant variant,
            String referenceCode
    ) {
        Deque<SourceBatchState> pool = new ArrayDeque<>();
        List<InventoryTransaction> saleTransactions = inventoryTransactionRepository.findByReferenceCodeAndType(
                        referenceCode,
                        TransactionType.SALE)
                .stream()
                .filter(transaction -> transaction.getInventory() != null
                        && transaction.getInventory().getProductVariant() != null
                        && Objects.equals(transaction.getInventory().getProductVariant().getId(), variant.getId()))
                .sorted(Comparator
                        .comparing(InventoryTransaction::getCreatedAt, Comparator.nullsLast(LocalDateTime::compareTo))
                        .thenComparing(InventoryTransaction::getId, Comparator.nullsLast(Long::compareTo)))
                .toList();

        for (InventoryTransaction transaction : saleTransactions) {
            int soldQuantity = Math.abs(Objects.requireNonNullElse(transaction.getQuantityChange(), 0));
            if (soldQuantity <= 0 || transaction.getInventory() == null) {
                continue;
            }

            Inventory sourceInventory = transaction.getInventory();
            pool.addLast(new SourceBatchState(
                    firstNonBlank(trimToNull(sourceInventory.getBatchNumber()), buildFallbackReturnBatchNumber(entity)),
                    sourceInventory.getImportPrice(),
                    sourceInventory.getExpiryDate(),
                    soldQuantity));
        }

        return pool;
    }

    private String resolveReturnReferenceCode(ReturnRequest entity, ReturnRequestItem item) {
        if (entity.getOrder() == null || entity.getOrder().getCode() == null) {
            throw new BadRequestException("Không xác định được đơn hàng nguồn cho phiếu trả hàng này.");
        }
        return item.getSubOrderId() != null
                ? entity.getOrder().getCode() + "-SUB-" + item.getSubOrderId()
                : entity.getOrder().getCode();
    }

    private String buildFallbackReturnBatchNumber(ReturnRequest entity) {
        return "RETURN-" + entity.getCode();
    }

    private BigDecimal resolveAllocationImportPrice(BigDecimal preferredPrice, BigDecimal fallbackPrice) {
        if (preferredPrice != null) {
            return preferredPrice;
        }
        if (fallbackPrice != null) {
            return fallbackPrice;
        }
        return BigDecimal.ZERO;
    }

    private String buildCustomerReturnNote(ReturnRequest entity, String internalNote) {
        return firstNonBlank(
                internalNote,
                "Nhập lại hàng khách trả từ phiếu " + entity.getCode() + " cho đơn " + (entity.getOrder() != null ? entity.getOrder().getCode() : "")
        );
    }

    private User resolveCurrentActor() {
        com.zone.agri.dto.response.user.UserDetail currentUser = AuthUtils.getUserDetail();
        if (currentUser == null || currentUser.getId() == null) {
            return null;
        }
        return userRepository.findById(currentUser.getId()).orElse(null);
    }

    private int physicalBalance(Inventory inventory) {
        return Objects.requireNonNullElse(inventory.getQuantity(), 0)
                + Objects.requireNonNullElse(inventory.getDefectiveQuantity(), 0);
    }

    private String generateCustomerReturnReceiptCode() {
        return "RIN"
                + LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyMMddHHmmss"))
                + UUID.randomUUID().toString().substring(0, 4).toUpperCase(Locale.ROOT);
    }

    private ReturnRequestStatus parseStatus(String status) {
        String normalized = trimToNull(status);
        if (normalized == null || "ALL".equalsIgnoreCase(normalized)) {
            return null;
        }
        try {
            return ReturnRequestStatus.valueOf(normalized.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Trạng thái trả hàng không hợp lệ");
        }
    }

    private ReturnRequestResponse mapResponse(ReturnRequest entity) {
        List<ReturnItemResponse> itemResponses = Optional.ofNullable(entity.getItems()).orElse(Collections.emptySet()).stream()
                .map(item -> ReturnItemResponse.builder()
                        .id(item.getId())
                        .sourceType(item.getSourceType())
                        .sourceItemId(item.getSourceItemId())
                        .productVariantId(item.getProductVariantId())
                        .subOrderId(item.getSubOrderId())
                        .productName(item.getProductName())
                        .variantName(item.getVariantName())
                        .sku(item.getSku())
                        .image(item.getImageUrl())
                        .quantity(item.getQuantity())
                        .orderedQuantity(item.getOrderedQuantity())
                        .unitPrice(item.getUnitPrice())
                        .refundAmount(item.getRefundAmount())
                        .restockQuantity(item.getRestockQuantity())
                        .defectiveQuantity(item.getDefectiveQuantity())
                        .build())
                .toList();

        List<ReturnEvidenceResponse> evidenceResponses = Optional.ofNullable(entity.getEvidences()).orElse(Collections.emptySet()).stream()
                .map(evidence -> ReturnEvidenceResponse.builder()
                        .id(evidence.getId())
                        .mediaType(evidence.getMediaType())
                        .fileUrl(evidence.getFileUrl())
                        .publicId(evidence.getPublicId())
                        .fileName(evidence.getFileName())
                        .build())
                .toList();

        return ReturnRequestResponse.builder()
                .id(entity.getId())
                .code(entity.getCode())
                .status(entity.getStatus())
                .issueType(entity.getIssueType())
                .handlingOption(resolveHandlingOption(entity))
                .refundMethod(entity.getRefundMethod())
                .requiresPhysicalReturn(entity.getRequiresPhysicalReturn())
                .orderId(entity.getOrder() != null ? entity.getOrder().getId() : null)
                .orderCode(entity.getOrder() != null ? entity.getOrder().getCode() : null)
                .branchId(entity.getBranch() != null ? entity.getBranch().getId() : null)
                .branchName(entity.getBranch() != null ? entity.getBranch().getName() : null)
                .receivedInventoryNoteId(entity.getReceivedInventoryNote() != null ? entity.getReceivedInventoryNote().getId() : null)
                .receivedInventoryNoteCode(entity.getReceivedInventoryNote() != null ? entity.getReceivedInventoryNote().getCode() : null)
                .customerName(entity.getCustomerName())
                .customerPhone(entity.getCustomerPhone())
                .customerEmail(entity.getCustomerEmail())
                .bankAccountName(entity.getBankAccountName())
                .bankAccountNumber(entity.getBankAccountNumber())
                .bankName(entity.getBankName())
                .bankBranch(entity.getBankBranch())
                .reason(entity.getReason())
                .description(entity.getDescription())
                .rejectReason(entity.getRejectReason())
                .internalNote(entity.getInternalNote())
                .totalRefundAmount(entity.getTotalRefundAmount())
                .createdAt(entity.getCreatedAt())
                .approvedAt(entity.getApprovedAt())
                .rejectedAt(entity.getRejectedAt())
                .receivedAt(entity.getReceivedAt())
                .refundedAt(entity.getRefundedAt())
                .items(itemResponses)
                .evidences(evidenceResponses)
                .build();
    }

    private ReturnHandlingOption resolveHandlingOption(CreateReturnRequest request) {
        ReturnHandlingOption requestedHandlingOption = request.getHandlingOption();
        ReturnIssueType issueType = request.getIssueType();

        if (requestedHandlingOption == null) {
            return issueType == ReturnIssueType.MISSING_ITEM
                    ? ReturnHandlingOption.REFUND_ONLY
                    : ReturnHandlingOption.RETURN_AND_REFUND;
        }

        if (issueType == ReturnIssueType.MISSING_ITEM
                && requestedHandlingOption == ReturnHandlingOption.RETURN_AND_REFUND) {
            throw new BadRequestException("Trường hợp thiếu hàng chỉ hỗ trợ phương án chỉ hoàn tiền.");
        }

        return requestedHandlingOption;
    }

    private ReturnHandlingOption resolveHandlingOption(ReturnRequest entity) {
        return Boolean.TRUE.equals(entity.getRequiresPhysicalReturn())
                ? ReturnHandlingOption.RETURN_AND_REFUND
                : ReturnHandlingOption.REFUND_ONLY;
    }

    private String resolveProductName(ProductVariant variant) {
        if (variant == null || variant.getProduct() == null) {
            return "Sản phẩm";
        }
        return firstNonBlank(variant.getProduct().getName(), "Sản phẩm");
    }

    private String resolveVariantName(ProductVariant variant) {
        if (variant == null) {
            return null;
        }
        return trimToNull(variant.getCustomSpecs());
    }

    private int defaultQuantity(Integer quantity) {
        return quantity == null || quantity < 0 ? 0 : quantity;
    }

    private BigDecimal safeAmount(BigDecimal amount) {
        return amount != null ? amount : BigDecimal.ZERO;
    }

    private RefundPreference resolveRefundPreference(
            Order order,
            Branch branch,
            ReturnRefundMethod requestedMethod,
            String bankAccountName,
            String bankAccountNumber,
            String bankName,
            String bankBranch
    ) {
        ReturnRefundMethod refundMethod = requestedMethod != null
                ? requestedMethod
                : ReturnRefundMethod.BANK_TRANSFER;
        validateRefundMethodAllowed(order, branch, refundMethod);

        if (refundMethod == ReturnRefundMethod.CASH) {
            return new RefundPreference(refundMethod, null, null, null, null);
        }

        return new RefundPreference(
                refundMethod,
                requiredTrimmed(bankAccountName, "Vui lòng nhập tên chủ tài khoản."),
                requiredTrimmed(bankAccountNumber, "Vui lòng nhập số tài khoản."),
                requiredTrimmed(bankName, "Vui lòng nhập tên ngân hàng."),
                trimToNull(bankBranch)
        );
    }

    private ReturnRefundMethod resolveRefundMethodForSettlement(
            ReturnRequest entity,
            ReturnRefundMethod requestedMethod
    ) {
        ReturnRefundMethod persistedRefundMethod = entity.getRefundMethod();
        if (persistedRefundMethod != null) {
            if (requestedMethod != null && requestedMethod != persistedRefundMethod) {
                throw new BadRequestException("Phương thức hoàn tiền phải khớp với phiếu trả hàng đã tạo.");
            }
            return persistedRefundMethod;
        }

        ReturnRefundMethod refundMethod = requestedMethod != null
                ? requestedMethod
                : ReturnRefundMethod.BANK_TRANSFER;
        validateRefundMethodAllowed(entity.getOrder(), entity.getBranch(), refundMethod);
        return refundMethod;
    }

    private void validateRefundMethodAllowed(
            Order order,
            Branch branch,
            ReturnRefundMethod refundMethod
    ) {
        if (refundMethod != ReturnRefundMethod.CASH) {
            return;
        }

        CashRefundEligibility cashRefundEligibility = resolveCashRefundEligibility(order, branch);
        if (!cashRefundEligibility.cashRefundEligible()) {
            throw new BadRequestException("Đơn trả hàng này chưa đủ điều kiện hoàn tiền mặt tại điểm xử lý gần bạn.");
        }
    }

    private CashRefundEligibility resolveCashRefundEligibility(Order order, Branch branch) {
        Double distanceKm = calculateCashRefundDistanceKm(order, branch);
        boolean eligible = distanceKm != null && distanceKm <= MAX_CASH_REFUND_DISTANCE_KM;
        List<ReturnRefundMethod> allowedRefundMethods = eligible
                ? List.of(ReturnRefundMethod.BANK_TRANSFER, ReturnRefundMethod.CASH)
                : List.of(ReturnRefundMethod.BANK_TRANSFER);

        return new CashRefundEligibility(allowedRefundMethods, eligible, distanceKm);
    }

    private Double calculateCashRefundDistanceKm(Order order, Branch branch) {
        if (order == null
                || branch == null
                || order.getUserLat() == null
                || order.getUserLng() == null
                || branch.getLat() == null
                || branch.getLng() == null) {
            return null;
        }

        return roundDistance(HaversineUtils.distanceKm(
                order.getUserLat(),
                order.getUserLng(),
                branch.getLat(),
                branch.getLng()));
    }

    private double roundDistance(double value) {
        return BigDecimal.valueOf(value)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private String requiredTrimmed(String value, String message) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new BadRequestException(message);
        }
        return trimmed;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String generateReturnRequestCode() {
        return "RET-" + LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyMMddHHmmss"))
                + "-"
                + UUID.randomUUID().toString().substring(0, 4).toUpperCase(Locale.ROOT);
    }

    private record ReceiveDecision(
            Branch receivingBranch,
            String internalNote,
            List<ReceiveItemDecision> items
    ) {
    }

    private record ReceiveItemDecision(
            ReturnRequestItem returnItem,
            ProductVariant variant,
            Integer restockQuantity,
            Integer defectiveQuantity,
            String itemNote,
            List<InboundReturnAllocation> allocations
    ) {
    }

    private record InboundReturnAllocation(
            String batchNumber,
            BigDecimal importPrice,
            LocalDateTime expiryDate,
            Integer quantity
    ) {
    }

    private record LineagePoolKey(
            String referenceCode,
            Long productVariantId
    ) {
    }

    private static final class SourceBatchState {
        private final String batchNumber;
        private final BigDecimal importPrice;
        private final LocalDateTime expiryDate;
        private int remainingQuantity;

        private SourceBatchState(
                String batchNumber,
                BigDecimal importPrice,
                LocalDateTime expiryDate,
                int remainingQuantity
        ) {
            this.batchNumber = batchNumber;
            this.importPrice = importPrice;
            this.expiryDate = expiryDate;
            this.remainingQuantity = remainingQuantity;
        }

        private String batchNumber() {
            return batchNumber;
        }

        private BigDecimal importPrice() {
            return importPrice;
        }

        private LocalDateTime expiryDate() {
            return expiryDate;
        }

        private int remainingQuantity() {
            return remainingQuantity;
        }

        private void consume(int quantity) {
            if (quantity <= 0) {
                return;
            }
            remainingQuantity = Math.max(0, remainingQuantity - quantity);
        }
    }

    private record ResolvedReturnLine(
            ReturnItemSourceType sourceType,
            Long sourceItemId,
            Long productVariantId,
            Long subOrderId,
            Long branchId,
            Branch branch,
            String productName,
            String variantName,
            String sku,
            String imageUrl,
            Integer quantity,
            Integer orderedQuantity,
            BigDecimal unitPrice,
            BigDecimal refundAmount
    ) {
    }

    private record RefundPreference(
            ReturnRefundMethod refundMethod,
            String bankAccountName,
            String bankAccountNumber,
            String bankName,
            String bankBranch
    ) {
    }

    private record CashRefundEligibility(
            List<ReturnRefundMethod> allowedRefundMethods,
            boolean cashRefundEligible,
            Double cashRefundDistanceKm
    ) {
    }
}
