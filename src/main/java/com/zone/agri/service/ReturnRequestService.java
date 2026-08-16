package com.zone.agri.service;

import com.zone.agri.dto.request.returns.*;
import com.zone.agri.dto.response.returns.*;
import com.zone.agri.entity.*;
import com.zone.agri.entity.enums.*;
import com.zone.agri.exception.BadRequestException;
import com.zone.agri.exception.Forbidden;
import com.zone.agri.exception.NotFoundException;
import com.zone.agri.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReturnRequestService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ReturnRequestRepository returnRequestRepository;
    private final ReturnRequestItemRepository returnRequestItemRepository;
    private final ReturnRequestEvidenceRepository returnRequestEvidenceRepository;
    private final SubOrderItemRepository subOrderItemRepository;

    @Transactional(readOnly = true)
    public ReturnOrderDraftResponse getReturnDraft(Long userId, Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy đơn hàng"));

        if (order.getUser() == null || !Objects.equals(order.getUser().getId(), userId)) {
            throw new Forbidden("Bạn không có quyền tạo yêu cầu cho đơn hàng này");
        }

        if (order.getStatus() != OrderStatus.COMPLETED) {
            throw new BadRequestException("Chỉ hỗ trợ trả hàng với đơn đã giao hoàn tất");
        }

        List<ReturnDraftItemResponse> items = new ArrayList<>();

        if (order.getSubOrders() != null && !order.getSubOrders().isEmpty()) {
            for (SubOrder subOrder : order.getSubOrders()) {
                Long branchId = subOrder.getBranch() != null ? subOrder.getBranch().getId() : null;
                String branchName = subOrder.getBranch() != null ? subOrder.getBranch().getName() : null;
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
                            .build());
                }
            }
        } else {
            Branch orderBranch = order.getBranch();
            Long branchId = orderBranch != null ? orderBranch.getId() : null;
            String branchName = orderBranch != null ? orderBranch.getName() : null;
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
                .message("Các sản phẩm chọn trả phải cùng chi nhánh phục vụ. Nếu đơn có nhiều chi nhánh, vui lòng tách thành nhiều yêu cầu.")
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

        if (order.getStatus() != OrderStatus.COMPLETED) {
            throw new BadRequestException("Chỉ hỗ trợ trả hàng với đơn đã giao hoàn tất");
        }

        validateBankTransferRefundMethod(request.getRefundMethod());
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
            throw new BadRequestException("Các sản phẩm trả hàng phải thuộc cùng một chi nhánh phục vụ. Vui lòng tách thành các yêu cầu riêng.");
        }

        boolean requiresPhysicalReturn = request.getIssueType() != ReturnIssueType.MISSING_ITEM;

        ReturnRequest entity = ReturnRequest.builder()
                .code(generateReturnRequestCode())
                .status(ReturnRequestStatus.PENDING)
                .issueType(request.getIssueType())
                .refundMethod(ReturnRefundMethod.BANK_TRANSFER)
                .requiresPhysicalReturn(requiresPhysicalReturn)
                .customerName(request.getFullName().trim())
                .customerPhone(request.getPhoneNumber().trim())
                .customerEmail(trimToNull(request.getEmail()))
                .bankAccountName(request.getBankAccountName().trim())
                .bankAccountNumber(request.getBankAccountNumber().trim())
                .bankName(request.getBankName().trim())
                .bankBranch(trimToNull(request.getBankBranch()))
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
        return returnRequestRepository.findAllForUser(userId).stream()
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
        applyReceive(entity, request != null ? request.getInternalNote() : null);
        return mapResponse(entity);
    }

    @Transactional
    public ReturnRequestResponse receiveForBranch(Long branchId, Long requestId, ReturnRequestReceiveRequest request) {
        ReturnRequest entity = getDetailedAndCheckBranch(requestId, branchId);
        applyReceive(entity, request != null ? request.getInternalNote() : null);
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

    private void applyReceive(ReturnRequest entity, String internalNote) {
        if (Boolean.FALSE.equals(entity.getRequiresPhysicalReturn())) {
            throw new BadRequestException("Yêu cầu thiếu hàng không cần bước nhận lại hàng");
        }
        if (entity.getStatus() != ReturnRequestStatus.APPROVED) {
            throw new BadRequestException("Chỉ nhận lại hàng sau khi yêu cầu đã được duyệt");
        }
        entity.setStatus(ReturnRequestStatus.RECEIVED);
        entity.setReceivedAt(LocalDateTime.now());
        entity.setInternalNote(trimToNull(internalNote));
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
        if (request.getRefundMethod() != null) {
            validateBankTransferRefundMethod(request.getRefundMethod());
        }
        entity.setRefundMethod(ReturnRefundMethod.BANK_TRANSFER);
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
                .refundMethod(entity.getRefundMethod())
                .requiresPhysicalReturn(entity.getRequiresPhysicalReturn())
                .orderId(entity.getOrder() != null ? entity.getOrder().getId() : null)
                .orderCode(entity.getOrder() != null ? entity.getOrder().getCode() : null)
                .branchId(entity.getBranch() != null ? entity.getBranch().getId() : null)
                .branchName(entity.getBranch() != null ? entity.getBranch().getName() : null)
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

    private void validateBankTransferRefundMethod(ReturnRefundMethod refundMethod) {
        if (refundMethod != ReturnRefundMethod.BANK_TRANSFER) {
            throw new BadRequestException("Yêu cầu trả hàng chỉ hỗ trợ hoàn tiền qua chuyển khoản ngân hàng");
        }
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
}
