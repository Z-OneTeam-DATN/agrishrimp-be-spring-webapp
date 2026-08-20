package com.zone.agri.service;

import com.zone.agri.common.AuthUtils;
import com.zone.agri.common.RoleUtils;
import com.zone.agri.common.WarehouseContext;
import com.zone.agri.dto.request.purchase.PurchaseRequestCreateRequest;
import com.zone.agri.dto.response.purchase.PurchaseRequestResponse;
import com.zone.agri.entity.*;
import com.zone.agri.entity.enums.PurchaseRequestStatus;
import com.zone.agri.entity.enums.SupplierProductCatalogStatus;
import com.zone.agri.exception.BadRequestException;
import com.zone.agri.exception.Forbidden;
import com.zone.agri.exception.NotFoundException;
import com.zone.agri.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PurchaseRequestService {
    private final PurchaseRequestRepository purchaseRequestRepository;
    private final PurchaseRequestItemRepository purchaseRequestItemRepository;
    private final InventoryNoteRepository inventoryNoteRepository;
    private final SupplierRepository supplierRepository;
    private final SupplierProductCatalogRepository supplierProductCatalogRepository;
    private final BranchRepository branchRepository;
    private final ProductVariantRepository productVariantRepository;
    private final UserRepository userRepository;
    private final WarehouseContext warehouseContext;
    private final EmailService emailService;
    private final SubOrderRepository subOrderRepository;
    private final InventoryTransferService inventoryTransferService;
    private final NotificationService notificationService;

    public record AutomaticReplenishmentRequestResult(
            List<PurchaseRequest> purchaseRequests,
            Map<Long, Integer> blockedQuantitiesByVariantId,
            Map<Long, String> blockedMessagesByVariantId) {
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HELPER
    // ─────────────────────────────────────────────────────────────────────────

    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }
        return userRepository.findByEmail(auth.getName()).orElse(null);
    }

    private boolean hasAuthority(String authority) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(authority));
    }

    private boolean canApprovePurchaseRequests() {
        return hasAuthority("PURCHASE_REQUEST_APPROVE");
    }

    private boolean canApprovePurchaseRequestsAcrossBranches() {
        return canApprovePurchaseRequests()
                && RoleUtils.hasAdminLikeAuthority(AuthUtils.getAuthorities());
    }

    private void assertPurchaseRequestReadOrApproveAccess(PurchaseRequest pr) {
        if (!canApprovePurchaseRequestsAcrossBranches()) {
            warehouseContext.assertAccess(pr.getBranch().getId());
        }
    }

    private Long resolvePurchaseRequestListBranchId(Long requestedBranchId) {
        if (canApprovePurchaseRequestsAcrossBranches()) {
            return requestedBranchId;
        }

        Long scopedBranchId = warehouseContext.resolveWarehouseId();
        if (requestedBranchId != null) {
            warehouseContext.assertAccess(requestedBranchId);
        }
        return scopedBranchId;
    }

    private Branch resolveRequestBranch(PurchaseRequestCreateRequest request) {
        if (request.getBranchId() != null) {
            return branchRepository.findById(request.getBranchId())
                    .orElseThrow(() -> new NotFoundException("Không tìm thấy chi nhánh ID: " + request.getBranchId()));
        }

        String branchName = request.getBranchName() != null ? request.getBranchName().trim() : "";
        return branchRepository.findByName(branchName)
                .orElseThrow(() -> new NotFoundException("Chi nhánh không tồn tại: " + branchName));
    }

    private String generateCode() {
        String prefix = "YCM-";
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyMMddHHmmss"));
        return prefix + ts;
    }

    private String normalizeText(String value) {
        if (value == null) {
            return "";
        }
        return java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replace('\u0111', 'd')
                .replace('\u0110', 'D')
                .toLowerCase(Locale.ROOT)
                .trim();
    }

    private boolean isMainWarehouseBranch(Branch branch) {
        return branch != null
                && ((branch.getBranchType() != null
                        && normalizeText(branch.getBranchType()).contains("warehouse"))
                        || normalizeText(branch.getName()).contains("kho tong"));
    }

    private Branch resolveMainWarehouseBranch() {
        return branchRepository.findAll().stream()
                .filter(this::isMainWarehouseBranch)
                .findFirst()
                .orElseThrow(() -> new BadRequestException("Khong tim thay kho tong de tao yeu cau mua tu dong."));
    }

    private String buildReplenishmentReferenceCode(SubOrder subOrder) {
        return subOrder.getOrder().getCode() + "-SUB-" + subOrder.getId();
    }

    private String buildAutomaticReplenishmentNote(SubOrder subOrder) {
        String orderCode = subOrder.getOrder() != null ? subOrder.getOrder().getCode() : "N/A";
        String branchName = subOrder.getBranch() != null ? subOrder.getBranch().getName() : "chi nhanh phuc vu";
        return "Tu dong tao yeu cau mua bo sung cho " + orderCode + " de cap cho " + branchName;
    }

    private boolean isMissingResendApiKey(BadRequestException exception) {
        return exception != null
                && exception.getMessage() != null
                && exception.getMessage().contains("RESEND_API_KEY");
    }

    private String appendOperationalNote(String baseNote, String extraNote) {
        if (extraNote == null || extraNote.isBlank()) {
            return baseNote;
        }
        if (baseNote == null || baseNote.isBlank()) {
            return extraNote;
        }
        if (baseNote.contains(extraNote)) {
            return baseNote;
        }
        return baseNote + "\n" + extraNote;
    }

    private boolean enforceMainWarehousePurchaseRequests() {
        return true;
    }

    private void assertPurchaseRequestCreatorCanUseBranch(Branch branch) {
        validatePurchaseRequestBranch(branch);

        if (RoleUtils.hasSuperAdminAuthority(AuthUtils.getAuthorities())) {
            return;
        }

        User creator = getCurrentUser();
        Branch creatorBranch = creator != null ? creator.getBranch() : null;
        if (!isMainWarehouseBranch(creatorBranch)) {
            throw new Forbidden("Chi kho tong moi duoc tao phieu yeu cau nhap NCC.");
        }

        if (branch == null
                || branch.getId() == null
                || creatorBranch.getId() == null
                || !Objects.equals(creatorBranch.getId(), branch.getId())) {
            throw new Forbidden("Chi duoc tao phieu yeu cau nhap NCC cho kho tong minh quan ly.");
        }
    }

    @Transactional(readOnly = true)
    public List<PurchaseRequest> findActiveAutoReplenishmentRequestsForSubOrder(Long subOrderId) {
        return purchaseRequestRepository.findAutoReplenishmentRequestsByLinkedSubOrderIdExcludingStatuses(
                subOrderId,
                List.of(
                        PurchaseRequestStatus.CANCELLED,
                        PurchaseRequestStatus.CLOSED,
                        PurchaseRequestStatus.COMPLETED));
    }

    @Transactional
    public List<PurchaseRequest> createAutomaticReplenishmentRequestsForSubOrder(SubOrder subOrder) {
        return createAutomaticReplenishmentRequestResultForSubOrder(subOrder, Map.of()).purchaseRequests();
    }

    @Transactional
    public List<PurchaseRequest> createAutomaticReplenishmentRequestsForSubOrder(
            SubOrder subOrder,
            Map<Long, Integer> requestedQuantitiesByVariantId) {
        return createAutomaticReplenishmentRequestResultForSubOrder(
                subOrder,
                requestedQuantitiesByVariantId).purchaseRequests();
    }

    @Transactional
    public AutomaticReplenishmentRequestResult createAutomaticReplenishmentRequestResultForSubOrder(
            SubOrder subOrder,
            Map<Long, Integer> requestedQuantitiesByVariantId) {
        SubOrder replenishmentSubOrder = subOrderRepository.findByIdWithItems(subOrder.getId())
                .orElseThrow(() -> new NotFoundException("Khong tim thay phan don can tao yeu cau mua."));

        List<PurchaseRequest> existingRequests = findActiveAutoReplenishmentRequestsForSubOrder(replenishmentSubOrder.getId());

        boolean limitRequestedQuantities = requestedQuantitiesByVariantId != null
                && !requestedQuantitiesByVariantId.isEmpty();
        Map<Long, Integer> remainingRequestedQuantities = new LinkedHashMap<>();
        if (limitRequestedQuantities) {
            requestedQuantitiesByVariantId.forEach((variantId, quantity) -> {
                int safeQuantity = Objects.requireNonNullElse(quantity, 0);
                if (variantId != null && safeQuantity > 0) {
                    remainingRequestedQuantities.put(variantId, safeQuantity);
                }
            });
        }

        Map<ProductVariant, Integer> missingQuantities = new LinkedHashMap<>();
        for (SubOrderItem item : replenishmentSubOrder.getItems() != null
                ? replenishmentSubOrder.getItems()
                : Collections.<SubOrderItem>emptyList()) {
            int missingQty = Objects.requireNonNullElse(item.getMissingQuantity(), 0);
            if (missingQty <= 0 || item.getProductVariant() == null || item.getProductVariant().getId() == null) {
                continue;
            }

            Long variantId = item.getProductVariant().getId();
            if (limitRequestedQuantities) {
                int remainingRequestedQty = remainingRequestedQuantities.getOrDefault(variantId, 0);
                missingQty = Math.min(missingQty, remainingRequestedQty);
                remainingRequestedQuantities.put(variantId, Math.max(0, remainingRequestedQty - missingQty));
            }
            if (missingQty <= 0) {
                continue;
            }

            missingQuantities.merge(item.getProductVariant(), missingQty, Integer::sum);
        }

        missingQuantities = reduceMissingQuantitiesByOpenPurchaseRequests(missingQuantities, existingRequests);
        if (missingQuantities.isEmpty()) {
            return new AutomaticReplenishmentRequestResult(existingRequests, Map.of(), Map.of());
        }

        Branch mainWarehouse = resolveMainWarehouseBranch();
        String referenceCode = buildReplenishmentReferenceCode(replenishmentSubOrder);
        List<Long> variantIds = missingQuantities.keySet().stream()
                .map(ProductVariant::getId)
                .toList();

        List<SupplierProductCatalog> catalogs = supplierProductCatalogRepository.findByProductVariantIdInAndStatus(
                variantIds,
                SupplierProductCatalogStatus.AVAILABLE);
        Map<Long, SupplierProductCatalog> firstCatalogByVariantId = new LinkedHashMap<>();
        for (SupplierProductCatalog catalog : catalogs) {
            if (catalog.getProductVariant() == null || catalog.getProductVariant().getId() == null) {
                continue;
            }
            if (!hasValidSupplierCatalogPrice(catalog)) {
                continue;
            }
            firstCatalogByVariantId.putIfAbsent(catalog.getProductVariant().getId(), catalog);
        }

        Map<Long, Integer> blockedQuantitiesByVariantId = new LinkedHashMap<>();
        Map<Long, String> blockedMessagesByVariantId = new LinkedHashMap<>();
        Iterator<Map.Entry<ProductVariant, Integer>> missingIterator = missingQuantities.entrySet().iterator();
        while (missingIterator.hasNext()) {
            Map.Entry<ProductVariant, Integer> missingEntry = missingIterator.next();
            ProductVariant variant = missingEntry.getKey();
            if (!firstCatalogByVariantId.containsKey(variant.getId())) {
                blockedQuantitiesByVariantId.put(variant.getId(), missingEntry.getValue());
                blockedMessagesByVariantId.put(
                        variant.getId(),
                        "Chua cau hinh nha cung cap dang hoat dong va gia hop le cho SKU: " + variant.getSku());
                missingIterator.remove();
            }
        }

        if (missingQuantities.isEmpty()) {
            return new AutomaticReplenishmentRequestResult(
                    existingRequests,
                    blockedQuantitiesByVariantId,
                    blockedMessagesByVariantId);
        }

        Map<Long, Supplier> supplierById = new LinkedHashMap<>();
        Map<Long, List<PurchaseRequestItem>> itemsBySupplierId = new LinkedHashMap<>();
        for (Map.Entry<ProductVariant, Integer> missingEntry : missingQuantities.entrySet()) {
            ProductVariant variant = missingEntry.getKey();
            SupplierProductCatalog catalog = firstCatalogByVariantId.get(variant.getId());
            Supplier supplier = catalog.getSupplier();
            if (supplier == null || supplier.getId() == null) {
                throw new BadRequestException("Khong tim thay nha cung cap hop le cho SKU: " + variant.getSku());
            }

            supplierById.putIfAbsent(supplier.getId(), supplier);
            BigDecimal catalogPrice = catalog.getPrice();
            itemsBySupplierId.computeIfAbsent(supplier.getId(), ignored -> new ArrayList<>())
                    .add(PurchaseRequestItem.builder()
                            .productVariant(variant)
                            .requestedQty(missingEntry.getValue())
                            .deliveredQty(0)
                            .acceptedQty(0)
                            .defectiveQty(0)
                            .remainingQty(missingEntry.getValue())
                            .unitPrice(catalogPrice)
                            .note("Tu dong bo sung cho phan don " + referenceCode)
                            .build());
        }

        User creator = getCurrentUser();
        PurchaseRequestStatus initialStatus = canApprovePurchaseRequests()
                ? PurchaseRequestStatus.APPROVED
                : PurchaseRequestStatus.PENDING_APPROVAL;
        LocalDateTime approvedAt = initialStatus == PurchaseRequestStatus.APPROVED ? LocalDateTime.now() : null;
        List<PurchaseRequest> createdRequests = new ArrayList<>();
        for (Map.Entry<Long, List<PurchaseRequestItem>> supplierEntry : itemsBySupplierId.entrySet()) {
            Supplier supplier = supplierById.get(supplierEntry.getKey());
            String code = generateCode();
            while (purchaseRequestRepository.existsByCode(code)) {
                code = generateCode();
            }

            PurchaseRequest purchaseRequest = PurchaseRequest.builder()
                    .code(code)
                    .status(initialStatus)
                    .supplier(supplier)
                    .branch(mainWarehouse)
                    .note(buildAutomaticReplenishmentNote(replenishmentSubOrder))
                    .createdBy(creator)
                    .approvedBy(initialStatus == PurchaseRequestStatus.APPROVED ? creator : null)
                    .approvedAt(approvedAt)
                    .autoReplenishment(true)
                    .linkedSubOrderId(replenishmentSubOrder.getId())
                    .linkedDestinationBranchId(replenishmentSubOrder.getBranch().getId())
                    .linkedReferenceCode(referenceCode)
                    .totalAmount(calculateItemsTotal(supplierEntry.getValue()))
                    .items(new ArrayList<>())
                    .build();

            for (PurchaseRequestItem item : supplierEntry.getValue()) {
                item.setPurchaseRequest(purchaseRequest);
                purchaseRequest.getItems().add(item);
            }

            purchaseRequest = purchaseRequestRepository.save(purchaseRequest);
            if (purchaseRequest.getStatus() == PurchaseRequestStatus.APPROVED
                    && supplier.getEmail() != null && !supplier.getEmail().isBlank()) {
                try {
                    emailService.sendPurchaseRequestToSupplier(purchaseRequest);
                    purchaseRequest.setStatus(PurchaseRequestStatus.SENT_TO_SUPPLIER);
                    purchaseRequest.setSentToSupplierAt(LocalDateTime.now());
                    purchaseRequest = purchaseRequestRepository.save(purchaseRequest);
                } catch (BadRequestException ex) {
                    if (!isMissingResendApiKey(ex)) {
                        throw ex;
                    }
                    purchaseRequest.setNote(appendOperationalNote(
                            purchaseRequest.getNote(),
                            "Auto-approved nhung chua gui NCC vi thieu cau hinh RESEND_API_KEY."));
                    purchaseRequest = purchaseRequestRepository.save(purchaseRequest);
                }
            }

            createdRequests.add(purchaseRequest);
        }

        List<PurchaseRequest> allRequests = new ArrayList<>(existingRequests);
        allRequests.addAll(createdRequests);
        return new AutomaticReplenishmentRequestResult(
                allRequests,
                blockedQuantitiesByVariantId,
                blockedMessagesByVariantId);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 1. TẠO PHIẾU YÊU CẦU MUA
    // ─────────────────────────────────────────────────────────────────────────

    private Map<ProductVariant, Integer> reduceMissingQuantitiesByOpenPurchaseRequests(
            Map<ProductVariant, Integer> missingQuantities,
            List<PurchaseRequest> existingRequests) {
        if (missingQuantities == null || missingQuantities.isEmpty()) {
            return Map.of();
        }

        Map<Long, Integer> coveredByVariantId = buildOpenPurchaseCoverageByVariantId(existingRequests);
        if (coveredByVariantId.isEmpty()) {
            return missingQuantities;
        }

        Map<ProductVariant, Integer> remainingQuantities = new LinkedHashMap<>();
        for (Map.Entry<ProductVariant, Integer> missingEntry : missingQuantities.entrySet()) {
            ProductVariant variant = missingEntry.getKey();
            Long variantId = variant != null ? variant.getId() : null;
            int requestedQty = Objects.requireNonNullElse(missingEntry.getValue(), 0);
            int coveredQty = variantId != null ? Math.max(0, coveredByVariantId.getOrDefault(variantId, 0)) : 0;
            int remainingQty = requestedQty - Math.min(requestedQty, coveredQty);
            if (variantId != null) {
                coveredByVariantId.put(variantId, Math.max(0, coveredQty - requestedQty));
            }
            if (remainingQty > 0) {
                remainingQuantities.put(variant, remainingQty);
            }
        }
        return remainingQuantities;
    }

    private Map<Long, Integer> buildOpenPurchaseCoverageByVariantId(List<PurchaseRequest> existingRequests) {
        Map<Long, Integer> coveredByVariantId = new LinkedHashMap<>();
        for (PurchaseRequest request : existingRequests != null ? existingRequests : List.<PurchaseRequest>of()) {
            for (PurchaseRequestItem item : request.getItems() != null
                    ? request.getItems()
                    : List.<PurchaseRequestItem>of()) {
                Long variantId = item.getProductVariant() != null ? item.getProductVariant().getId() : null;
                if (variantId == null) {
                    continue;
                }

                int remainingQty = Objects.requireNonNullElse(item.getRemainingQty(), 0);
                if (remainingQty <= 0) {
                    int requestedQty = Objects.requireNonNullElse(item.getRequestedQty(), 0);
                    int acceptedQty = Objects.requireNonNullElse(item.getAcceptedQty(), 0);
                    remainingQty = Math.max(0, requestedQty - acceptedQty);
                }
                if (remainingQty > 0) {
                    coveredByVariantId.merge(variantId, remainingQty, Integer::sum);
                }
            }
        }
        return coveredByVariantId;
    }

    @Transactional
    public PurchaseRequestResponse createRequest(PurchaseRequestCreateRequest request) {
        Supplier supplier = supplierRepository.findByCode(request.getSupplierCode())
                .orElseThrow(() -> new NotFoundException("Nhà cung cấp không tồn tại: " + request.getSupplierCode()));

        if (supplier.getStatus() == com.zone.agri.entity.enums.SupplierStatus.INACTIVE) {
            throw new BadRequestException("Nhà cung cấp đang tạm ngừng giao dịch. Không thể tạo phiếu yêu cầu mua.");
        }

        Branch branch = resolveRequestBranch(request);
        assertPurchaseRequestCreatorCanUseBranch(branch);
        warehouseContext.assertAccess(branch.getId());

        String code = generateCode();
        while (purchaseRequestRepository.existsByCode(code)) {
            code = generateCode();
        }

        LocalDateTime expectedDate = null;
        if (request.getExpectedDeliveryDate() != null && !request.getExpectedDeliveryDate().isBlank()) {
            expectedDate = LocalDate.parse(request.getExpectedDeliveryDate()).atStartOfDay();
        }

        // Quy trình 1: Admin tạo → APPROVED ngay (không cần chờ duyệt).
        //              Quản lý kho tổng tạo → PENDING_APPROVAL (chờ Admin duyệt).
        User creator = getCurrentUser();
        PurchaseRequestStatus initialStatus = hasAuthority("PURCHASE_REQUEST_APPROVE")
                ? PurchaseRequestStatus.APPROVED
                : PurchaseRequestStatus.PENDING_APPROVAL;

        LocalDateTime approvedAt = initialStatus == PurchaseRequestStatus.APPROVED ? LocalDateTime.now() : null;

        PurchaseRequest pr = PurchaseRequest.builder()
                .code(code)
                .status(initialStatus)
                .supplier(supplier)
                .branch(branch)
                .expectedDeliveryDate(expectedDate)
                .note(request.getNote())
                .createdBy(creator)
                .approvedBy(initialStatus == PurchaseRequestStatus.APPROVED ? creator : null)
                .approvedAt(approvedAt)
                .items(new ArrayList<>())
                .build();

        pr = purchaseRequestRepository.save(pr);

        // Tạo các dòng hàng
        validateUniqueRequestItems(request.getItems());

        BigDecimal totalAmount = BigDecimal.ZERO;
        for (PurchaseRequestCreateRequest.ItemRequest itemReq : request.getItems()) {
            ProductVariant variant = productVariantRepository.findBySku(itemReq.getProductCode())
                    .orElseThrow(() -> new NotFoundException("SKU không tồn tại: " + itemReq.getProductCode()));

            BigDecimal unitPrice = resolveSupplierCatalogPrice(supplier, variant);

            PurchaseRequestItem item = PurchaseRequestItem.builder()
                    .purchaseRequest(pr)
                    .productVariant(variant)
                    .requestedQty(itemReq.getRequestedQty())
                    .deliveredQty(0)
                    .acceptedQty(0)
                    .defectiveQty(0)
                    .remainingQty(itemReq.getRequestedQty())
                    .unitPrice(unitPrice)
                    .note(itemReq.getNote())
                    .build();

            pr.getItems().add(item);
            totalAmount = totalAmount.add(unitPrice.multiply(BigDecimal.valueOf(itemReq.getRequestedQty())));
        }

        pr.setTotalAmount(totalAmount);
        if (pr.getStatus() == PurchaseRequestStatus.APPROVED) {
            pr.setApprovedBy(getCurrentUser());
            pr.setApprovedAt(LocalDateTime.now());
        }
        pr = purchaseRequestRepository.save(pr);

        if (pr.getStatus() == PurchaseRequestStatus.PENDING_APPROVAL) {
            notificationService.notifyPurchaseRequestNeedsApproval(pr);
        }

        return mapToResponse(pr);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. CẬP NHẬT PHIẾU (chỉ khi DRAFT hoặc PENDING_APPROVAL và chưa có phiếu nhập)
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    public PurchaseRequestResponse updateRequest(Long id, PurchaseRequestCreateRequest request) {
        PurchaseRequest pr = purchaseRequestRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy phiếu yêu cầu mua ID: " + id));
        warehouseContext.assertAccess(pr.getBranch().getId());
        if (false && pr.getStatus() != PurchaseRequestStatus.SENT_TO_SUPPLIER &&
                pr.getStatus() != PurchaseRequestStatus.PARTIALLY_RECEIVED) {
            throw new BadRequestException("Phiáº¿u yĂªu cáº§u mua pháº£i Ä‘Æ°á»£c gá»­i nhĂ  cung cáº¥p trÆ°á»›c khi táº¡o phiáº¿u nháº­p.");
        }
        if (false && pr.getStatus() != PurchaseRequestStatus.SENT_TO_SUPPLIER &&
                pr.getStatus() != PurchaseRequestStatus.PARTIALLY_RECEIVED) {
            throw new BadRequestException("Phiáº¿u yĂªu cáº§u mua pháº£i Ä‘Æ°á»£c gá»­i nhĂ  cung cáº¥p trÆ°á»›c khi táº¡o phiáº¿u nháº­p.");
        }

        if (pr.getStatus() != PurchaseRequestStatus.DRAFT &&
                pr.getStatus() != PurchaseRequestStatus.PENDING_APPROVAL) {
            throw new BadRequestException("Chỉ có thể sửa phiếu ở trạng thái Nháp hoặc Chờ duyệt.");
        }

        // Chặn sửa nếu đã có phiếu nhập liên kết
        long receiptCount = purchaseRequestRepository.countGoodsReceiptsByPrId(id);
        if (receiptCount > 0) {
            throw new BadRequestException("Không thể sửa phiếu đã có phiếu nhập hàng. Hãy điều chỉnh qua phiếu nhập.");
        }

        Supplier supplier = supplierRepository.findByCode(request.getSupplierCode())
                .orElseThrow(() -> new NotFoundException("Nhà cung cấp không tồn tại"));

        if (supplier.getStatus() == com.zone.agri.entity.enums.SupplierStatus.INACTIVE) {
            throw new BadRequestException("Nhà cung cấp đang tạm ngừng giao dịch. Không thể tạo phiếu yêu cầu mua.");
        }
        Branch branch = resolveRequestBranch(request);
        assertPurchaseRequestCreatorCanUseBranch(branch);
        warehouseContext.assertAccess(branch.getId());

        pr.setSupplier(supplier);
        pr.setBranch(branch);
        pr.setNote(request.getNote());

        if (request.getExpectedDeliveryDate() != null && !request.getExpectedDeliveryDate().isBlank()) {
            pr.setExpectedDeliveryDate(LocalDate.parse(request.getExpectedDeliveryDate()).atStartOfDay());
        }

        // Xóa items cũ và tạo lại
        pr.getItems().clear();
        purchaseRequestRepository.flush();

        validateUniqueRequestItems(request.getItems());

        for (PurchaseRequestCreateRequest.ItemRequest itemReq : request.getItems()) {
            ProductVariant variant = productVariantRepository.findBySku(itemReq.getProductCode())
                    .orElseThrow(() -> new NotFoundException("SKU khĂ´ng tá»“n táº¡i: " + itemReq.getProductCode()));
            resolveSupplierCatalogPrice(supplier, variant);
        }

        BigDecimal totalAmount = BigDecimal.ZERO;
        for (PurchaseRequestCreateRequest.ItemRequest itemReq : request.getItems()) {
            ProductVariant variant = productVariantRepository.findBySku(itemReq.getProductCode())
                    .orElseThrow(() -> new NotFoundException("SKU không tồn tại: " + itemReq.getProductCode()));

            BigDecimal unitPrice = resolveSupplierCatalogPrice(supplier, variant);

            PurchaseRequestItem item = PurchaseRequestItem.builder()
                    .purchaseRequest(pr)
                    .productVariant(variant)
                    .requestedQty(itemReq.getRequestedQty())
                    .deliveredQty(0)
                    .acceptedQty(0)
                    .defectiveQty(0)
                    .remainingQty(itemReq.getRequestedQty())
                    .unitPrice(unitPrice)
                    .note(itemReq.getNote())
                    .build();

            pr.getItems().add(item);
            totalAmount = totalAmount.add(unitPrice.multiply(BigDecimal.valueOf(itemReq.getRequestedQty())));
        }

        pr.setTotalAmount(totalAmount);
        return mapToResponse(purchaseRequestRepository.save(pr));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. ĐỌC DỮ LIỆU
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<PurchaseRequestResponse> getAllRequests(Long requestedBranchId) {
        Long branchId = resolvePurchaseRequestListBranchId(requestedBranchId);
        List<PurchaseRequest> list = (branchId == null)
                ? purchaseRequestRepository.findAllWithRelations()
                : purchaseRequestRepository.findAllByBranchWithRelations(branchId);

        return list.stream().map(this::mapToResponseShallow).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public PurchaseRequestResponse getById(Long id) {
        PurchaseRequest pr = purchaseRequestRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy phiếu yêu cầu mua ID: " + id));
        assertPurchaseRequestReadOrApproveAccess(pr);
        return mapToResponse(pr);
    }

    // Lấy các item còn thiếu (remainingQty > 0) để tạo phiếu nhập mới
    @Transactional(readOnly = true)
    public List<PurchaseRequestResponse.ItemResponse> getRemainingItems(Long id) {
        PurchaseRequest pr = purchaseRequestRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy phiếu yêu cầu mua ID: " + id));
        warehouseContext.assertAccess(pr.getBranch().getId());

        if (pr.getStatus() != PurchaseRequestStatus.DELIVERING &&
                pr.getStatus() != PurchaseRequestStatus.PARTIALLY_RECEIVED) {
            throw new BadRequestException("Phiáº¿u yĂªu cáº§u mua pháº£i Ä‘Æ°á»£c gá»­i nhĂ  cung cáº¥p trÆ°á»›c khi táº¡o phiáº¿u nháº­p.");
        }

        if (pr.getStatus() == PurchaseRequestStatus.COMPLETED ||
                pr.getStatus() == PurchaseRequestStatus.CANCELLED ||
                pr.getStatus() == PurchaseRequestStatus.CLOSED) {
            throw new BadRequestException("Phiếu yêu cầu đã đóng/hoàn tất, không thể tạo thêm phiếu nhập.");
        }

        return pr.getItems().stream()
                .filter(i -> i.getRemainingQty() != null && i.getRemainingQty() > 0)
                .map(this::mapItemToResponse)
                .collect(Collectors.toList());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. CHUYỂN TRẠNG THÁI
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    public PurchaseRequestResponse submit(Long id) {
        PurchaseRequest pr = findOrThrow(id);
        warehouseContext.assertAccess(pr.getBranch().getId());
        // Hỗ trợ cả DRAFT (flow cũ) lẫn PENDING_APPROVAL (nếu FE gọi lại submit)
        if (pr.getStatus() != PurchaseRequestStatus.DRAFT
                && pr.getStatus() != PurchaseRequestStatus.PENDING_APPROVAL) {
            throw new BadRequestException("Chỉ có thể gửi duyệt phiếu ở trạng thái Nháp hoặc Chờ duyệt.");
        }
        pr.setStatus(PurchaseRequestStatus.PENDING_APPROVAL);
        return mapToResponseShallow(purchaseRequestRepository.save(pr));
    }

    @Transactional
    public PurchaseRequestResponse approve(Long id) {
        PurchaseRequest pr = findOrThrow(id);
        assertPurchaseRequestReadOrApproveAccess(pr);
        if (pr.getStatus() != PurchaseRequestStatus.PENDING_APPROVAL) {
            throw new BadRequestException("Chỉ có thể duyệt phiếu đang ở trạng thái Chờ duyệt.");
        }
        pr.setStatus(PurchaseRequestStatus.APPROVED);
        pr.setApprovedBy(getCurrentUser());
        pr.setApprovedAt(LocalDateTime.now());
        return mapToResponseShallow(purchaseRequestRepository.save(pr));
    }

    @Transactional
    public PurchaseRequestResponse reject(Long id) {
        PurchaseRequest pr = findOrThrow(id);
        assertPurchaseRequestReadOrApproveAccess(pr);
        if (pr.getStatus() != PurchaseRequestStatus.PENDING_APPROVAL) {
            throw new BadRequestException("Chỉ có thể từ chối phiếu đang ở trạng thái Chờ duyệt.");
        }
        pr.setStatus(PurchaseRequestStatus.DRAFT); // Trả về nháp để sửa lại
        return mapToResponseShallow(purchaseRequestRepository.save(pr));
    }

    @Transactional
    public PurchaseRequestResponse sendToSupplier(Long id) {
        PurchaseRequest pr = findOrThrow(id);
        warehouseContext.assertAccess(pr.getBranch().getId());
        if (pr.getStatus() != PurchaseRequestStatus.APPROVED) {
            throw new BadRequestException("Chỉ có thể gửi NCC phiếu đã được duyệt.");
        }
        if (pr.getSupplier() == null || pr.getSupplier().getEmail() == null || pr.getSupplier().getEmail().isBlank()) {
            throw new BadRequestException("NhĂ  cung cáº¥p chÆ°a cĂ³ email Ä‘á»ƒ gá»­i phiáº¿u yĂªu cáº§u.");
        }
        emailService.sendPurchaseRequestToSupplier(pr);
        pr.setStatus(PurchaseRequestStatus.SENT_TO_SUPPLIER);
        pr.setSentToSupplierAt(LocalDateTime.now());
        return mapToResponseShallow(purchaseRequestRepository.save(pr));
    }

    @Transactional
    public PurchaseRequestResponse resendToSupplier(Long id) {
        PurchaseRequest pr = findOrThrow(id);
        warehouseContext.assertAccess(pr.getBranch().getId());
        if (pr.getStatus() != PurchaseRequestStatus.SENT_TO_SUPPLIER) {
            throw new BadRequestException("Chi co the gui lai email khi phieu da gui nha cung cap.");
        }
        if (pr.getSupplier() == null || pr.getSupplier().getEmail() == null || pr.getSupplier().getEmail().isBlank()) {
            throw new BadRequestException("Nha cung cap chua co email de gui lai phieu yeu cau.");
        }
        emailService.sendPurchaseRequestToSupplier(pr);
        return mapToResponseShallow(pr);
    }

    @Transactional
    public PurchaseRequestResponse confirmSupplier(Long id) {
        PurchaseRequest pr = findOrThrow(id);
        warehouseContext.assertAccess(pr.getBranch().getId());
        if (pr.getStatus() != PurchaseRequestStatus.SENT_TO_SUPPLIER) {
            throw new BadRequestException("Chi co the ghi nhan xac nhan khi phieu da gui nha cung cap.");
        }
        pr.setStatus(PurchaseRequestStatus.SUPPLIER_CONFIRMED);
        return mapToResponseShallow(purchaseRequestRepository.save(pr));
    }

    @Transactional
    public PurchaseRequestResponse markDelivering(Long id) {
        PurchaseRequest pr = findOrThrow(id);
        warehouseContext.assertAccess(pr.getBranch().getId());
        if (pr.getStatus() != PurchaseRequestStatus.SUPPLIER_CONFIRMED) {
            throw new BadRequestException("Chi co the chuyen sang cho giao hang sau khi NCC da xac nhan.");
        }
        pr.setStatus(PurchaseRequestStatus.DELIVERING);
        return mapToResponseShallow(purchaseRequestRepository.save(pr));
    }

    @Transactional
    public PurchaseRequestResponse cancel(Long id) {
        PurchaseRequest pr = findOrThrow(id);
        warehouseContext.assertAccess(pr.getBranch().getId());
        Set<PurchaseRequestStatus> cancellableStatuses = Set.of(
                PurchaseRequestStatus.DRAFT,
                PurchaseRequestStatus.PENDING_APPROVAL,
                PurchaseRequestStatus.APPROVED,
                PurchaseRequestStatus.SENT_TO_SUPPLIER,
                PurchaseRequestStatus.SUPPLIER_CONFIRMED,
                PurchaseRequestStatus.DELIVERING
        );
        if (!cancellableStatuses.contains(pr.getStatus())) {
            throw new BadRequestException("Không thể hủy phiếu ở trạng thái hiện tại. Phiếu đã gửi NCC hoặc đang nhận hàng.");
        }
        long receiptCount = purchaseRequestRepository.countGoodsReceiptsByPrId(id);
        if (receiptCount > 0) {
            throw new BadRequestException("Phiếu đã có phiếu nhập. Không thể hủy, hãy đóng phiếu thay thế.");
        }
        pr.setStatus(PurchaseRequestStatus.CANCELLED);
        return mapToResponseShallow(purchaseRequestRepository.save(pr));
    }

    @Transactional
    public PurchaseRequestResponse close(Long id) {
        PurchaseRequest pr = findOrThrow(id);
        assertPurchaseRequestReadOrApproveAccess(pr);
        Set<PurchaseRequestStatus> closableStatuses = Set.of(
                PurchaseRequestStatus.PARTIALLY_RECEIVED,
                PurchaseRequestStatus.COMPLETED
        );
        if (!closableStatuses.contains(pr.getStatus())) {
            throw new BadRequestException("Chỉ có thể đóng phiếu đang gửi NCC hoặc đang nhận một phần.");
        }
        pr.setStatus(PurchaseRequestStatus.CLOSED);
        pr.setCompletedAt(LocalDateTime.now());
        return mapToResponseShallow(purchaseRequestRepository.save(pr));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 5. CẬP NHẬT SỐ LƯỢNG LŨY KẾ SAU KHI PHIẾU NHẬP ĐƯỢC COMPLETED (QC xong)
    //    Được gọi từ InventoryService.completeReceipt() sau khi cộng kho
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    public void updateCumulativeQtyAfterReceipt(InventoryNote completedNote) {
        if (completedNote.getPurchaseRequest() == null) return;

        Long prId = completedNote.getPurchaseRequest().getId();
        PurchaseRequest pr = purchaseRequestRepository.findById(prId)
                .orElse(null);
        if (pr == null) return;

        // Không cập nhật nếu PR đã hoàn tất/hủy/đóng
        if (pr.getStatus() == PurchaseRequestStatus.COMPLETED ||
                pr.getStatus() == PurchaseRequestStatus.CANCELLED ||
                pr.getStatus() == PurchaseRequestStatus.CLOSED) {
            return;
        }

        // Cập nhật từng item trong phiếu nhập vào PR item tương ứng
        if (completedNote.getDetails() != null) {
            for (InventoryNoteDetail detail : completedNote.getDetails()) {
                ProductVariant variant = detail.getProductVariant();
                if (variant == null) continue;

                // Tìm PR item tương ứng với pessimistic lock
                Optional<PurchaseRequestItem> prItemOpt =
                        purchaseRequestItemRepository.findByPrIdAndVariantWithLock(prId, variant.getId());

                if (prItemOpt.isEmpty()) continue;

                PurchaseRequestItem prItem = prItemOpt.get();

                int deliveredDelta = Objects.requireNonNullElse(detail.getQuantityReal(), 0);
                int acceptedDelta  = Objects.requireNonNullElse(detail.getQuantityAccepted(), 0);
                int defectiveDelta = Objects.requireNonNullElse(detail.getQuantityRejected(), 0);

                prItem.setDeliveredQty(Objects.requireNonNullElse(prItem.getDeliveredQty(), 0) + deliveredDelta);
                prItem.setAcceptedQty(Objects.requireNonNullElse(prItem.getAcceptedQty(), 0) + acceptedDelta);
                prItem.setDefectiveQty(Objects.requireNonNullElse(prItem.getDefectiveQty(), 0) + defectiveDelta);

                int newRemaining = Math.max(0,
                        Objects.requireNonNullElse(prItem.getRequestedQty(), 0)
                                - Objects.requireNonNullElse(prItem.getAcceptedQty(), 0));
                prItem.setRemainingQty(newRemaining);

                purchaseRequestItemRepository.save(prItem);
            }
        }

        // Kiểm tra xem tất cả items đã đủ acceptedQty chưa
        long itemsWithRemaining = purchaseRequestItemRepository.countItemsWithRemainingQty(prId);

        if (itemsWithRemaining == 0) {
            // Tất cả items đã đủ → COMPLETED
            pr.setStatus(PurchaseRequestStatus.COMPLETED);
            pr.setCompletedAt(LocalDateTime.now());
        } else {
            // Còn thiếu → PARTIALLY_RECEIVED (chuyển từ trạng thái chờ hàng sang đã nhận một phần)
            if (pr.getStatus() == PurchaseRequestStatus.DELIVERING) {
                pr.setStatus(PurchaseRequestStatus.PARTIALLY_RECEIVED);
            }
        }

        pr = purchaseRequestRepository.save(pr);
        if (Boolean.TRUE.equals(pr.getAutoReplenishment()) && pr.getLinkedSubOrderId() != null) {
            inventoryTransferService.createMainWarehouseReplenishmentTransferIfPossible(pr.getLinkedSubOrderId());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // MAPPING
    // ─────────────────────────────────────────────────────────────────────────

    private PurchaseRequest findOrThrow(Long id) {
        return purchaseRequestRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy phiếu yêu cầu mua ID: " + id));
    }

    private void validatePurchaseRequestBranch(Branch branch) {
        if (enforceMainWarehousePurchaseRequests()) {
            if (!isMainWarehouseBranch(branch)) {
                throw new BadRequestException("Phieu yeu cau mua NCC chi duoc tao cho kho tong.");
            }
            return;
        }

        String branchType = branch.getBranchType();
        if (branchType == null || !"WAREHOUSE".equalsIgnoreCase(branchType)) {
            throw new BadRequestException("Phiếu yêu cầu mua NCC chỉ được tạo cho kho tổng / chi nhánh loại WAREHOUSE.");
        }
    }

    // Mapping đầy đủ (dùng cho getById - có details và goodsReceipts)
    private void validateUniqueRequestItems(List<PurchaseRequestCreateRequest.ItemRequest> items) {
        Set<String> seenSkus = new HashSet<>();
        for (PurchaseRequestCreateRequest.ItemRequest item : items) {
            String sku = item.getProductCode() != null ? item.getProductCode().trim() : "";
            String normalizedSku = sku.toUpperCase(Locale.ROOT);
            if (!seenSkus.add(normalizedSku)) {
                throw new BadRequestException("SKU " + sku + " bị trùng trong phiếu yêu cầu mua.");
            }
        }
    }

    private BigDecimal resolveSupplierCatalogPrice(Supplier supplier, ProductVariant variant) {
        if (supplier == null || variant == null) {
            throw new BadRequestException("Dữ liệu nhà cung cấp hoặc sản phẩm không hợp lệ. Vui lòng tải lại trang và thử lại.");
        }

        SupplierProductCatalog catalog = supplierProductCatalogRepository.findAvailableBySupplierIdAndProductVariantId(
                        supplier.getId(),
                        variant.getId())
                .orElseThrow(() -> new BadRequestException(
                        "SKU " + variant.getSku() + " không nằm trong catalog đang bán của nhà cung cấp "
                                + supplier.getCode() + ". Vui lòng chọn sản phẩm trong danh sách catalog của nhà cung cấp."));

        if (!hasValidSupplierCatalogPrice(catalog)) {
            throw new BadRequestException("SKU " + variant.getSku()
                    + " chua co gia NCC hop le trong catalog " + supplier.getCode()
                    + ". Vui long cap nhat gia catalog nha cung cap truoc khi tao phieu.");
        }

        return catalog.getPrice();
    }

    private boolean hasValidSupplierCatalogPrice(SupplierProductCatalog catalog) {
        return catalog != null
                && catalog.getPrice() != null
                && catalog.getPrice().compareTo(BigDecimal.ZERO) > 0;
    }

    private BigDecimal calculateItemsTotal(List<PurchaseRequestItem> items) {
        if (items == null || items.isEmpty()) {
            return BigDecimal.ZERO;
        }

        return items.stream()
                .map(item -> Objects.requireNonNullElse(item.getUnitPrice(), BigDecimal.ZERO)
                        .multiply(BigDecimal.valueOf(Objects.requireNonNullElse(item.getRequestedQty(), 0))))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private PurchaseRequestResponse mapToResponse(PurchaseRequest pr) {
        List<PurchaseRequestResponse.ItemResponse> itemResponses = new ArrayList<>();
        if (pr.getItems() != null) {
            itemResponses = pr.getItems().stream()
                    .map(this::mapItemToResponse)
                    .collect(Collectors.toList());
        }

        // Lấy danh sách phiếu nhập liên kết
        List<PurchaseRequestResponse.GoodsReceiptSummary> receiptSummaries = new ArrayList<>();
        List<InventoryNote> goodsReceipts = inventoryNoteRepository.findGoodsReceiptsByPurchaseRequestId(pr.getId());
        if (goodsReceipts != null) {
            receiptSummaries = goodsReceipts.stream()
                    .map(note -> {
                        int totalDelivered = 0;
                        int totalAccepted = 0;
                        int totalDefective = 0;
                        if (note.getDetails() != null) {
                            for (InventoryNoteDetail d : note.getDetails()) {
                                totalDelivered += Objects.requireNonNullElse(d.getQuantityReal(), 0);
                                totalAccepted  += Objects.requireNonNullElse(d.getQuantityAccepted(), 0);
                                totalDefective += Objects.requireNonNullElse(d.getQuantityRejected(), 0);
                            }
                        }
                        return PurchaseRequestResponse.GoodsReceiptSummary.builder()
                                .id(note.getId())
                                .code(note.getCode())
                                .status(note.getStatus() != null ? note.getStatus().name() : "DRAFT")
                                .createdAt(note.getCreatedAt())
                                .createdByName(note.getCreatedBy() != null ? note.getCreatedBy().getFullName() : "")
                                .totalDelivered(totalDelivered)
                                .totalAccepted(totalAccepted)
                                .totalDefective(totalDefective)
                                .build();
                    })
                    .collect(Collectors.toList());
        }

        long totalReceiptCount = receiptSummaries.size();
        long completedReceiptCount = receiptSummaries.stream()
                .filter(r -> "COMPLETED".equals(r.getStatus()))
                .count();

        return PurchaseRequestResponse.builder()
                .id(pr.getId())
                .code(pr.getCode())
                .status(pr.getStatus() != null ? pr.getStatus().name() : "DRAFT")
                .supplierId(pr.getSupplier() != null ? pr.getSupplier().getId() : null)
                .supplierName(pr.getSupplier() != null ? pr.getSupplier().getName() : "")
                .supplierCode(pr.getSupplier() != null ? pr.getSupplier().getCode() : "")
                .branchId(pr.getBranch() != null ? pr.getBranch().getId() : null)
                .branchName(pr.getBranch() != null ? pr.getBranch().getName() : "")
                .createdAt(pr.getCreatedAt())
                .updatedAt(pr.getUpdatedAt())
                .expectedDeliveryDate(pr.getExpectedDeliveryDate())
                .approvedAt(pr.getApprovedAt())
                .sentToSupplierAt(pr.getSentToSupplierAt())
                .completedAt(pr.getCompletedAt())
                .createdByName(pr.getCreatedBy() != null ? pr.getCreatedBy().getFullName() : "")
                .approvedByName(pr.getApprovedBy() != null ? pr.getApprovedBy().getFullName() : "")
                .totalAmount(Objects.requireNonNullElse(pr.getTotalAmount(), BigDecimal.ZERO))
                .note(pr.getNote())
                .totalReceiptCount(totalReceiptCount)
                .completedReceiptCount(completedReceiptCount)
                .items(itemResponses)
                .goodsReceipts(receiptSummaries)
                .build();
    }

    // Mapping nhanh cho danh sách (không load items + receipts chi tiết)
    private PurchaseRequestResponse mapToResponseShallow(PurchaseRequest pr) {
        long totalReceiptCount = purchaseRequestRepository.countGoodsReceiptsByPrId(pr.getId());
        long completedReceiptCount = purchaseRequestRepository.countCompletedGoodsReceiptsByPrId(pr.getId());

        return PurchaseRequestResponse.builder()
                .id(pr.getId())
                .code(pr.getCode())
                .status(pr.getStatus() != null ? pr.getStatus().name() : "DRAFT")
                .supplierId(pr.getSupplier() != null ? pr.getSupplier().getId() : null)
                .supplierName(pr.getSupplier() != null ? pr.getSupplier().getName() : "")
                .supplierCode(pr.getSupplier() != null ? pr.getSupplier().getCode() : "")
                .branchId(pr.getBranch() != null ? pr.getBranch().getId() : null)
                .branchName(pr.getBranch() != null ? pr.getBranch().getName() : "")
                .createdAt(pr.getCreatedAt())
                .updatedAt(pr.getUpdatedAt())
                .expectedDeliveryDate(pr.getExpectedDeliveryDate())
                .approvedAt(pr.getApprovedAt())
                .createdByName(pr.getCreatedBy() != null ? pr.getCreatedBy().getFullName() : "")
                .totalAmount(Objects.requireNonNullElse(pr.getTotalAmount(), BigDecimal.ZERO))
                .note(pr.getNote())
                .totalReceiptCount(totalReceiptCount)
                .completedReceiptCount(completedReceiptCount)
                .build();
    }

    private PurchaseRequestResponse.ItemResponse mapItemToResponse(PurchaseRequestItem item) {
        ProductVariant variant = item.getProductVariant();
        Product product = variant != null ? variant.getProduct() : null;
        return PurchaseRequestResponse.ItemResponse.builder()
                .id(item.getId())
                .productVariantId(variant != null ? variant.getId() : null)
                .productCode(variant != null ? variant.getSku() : "")
                .productName(product != null ? product.getName() : "")
                .imageUrl(resolveItemImageUrl(variant, product))
                .requestedQty(Objects.requireNonNullElse(item.getRequestedQty(), 0))
                .deliveredQty(Objects.requireNonNullElse(item.getDeliveredQty(), 0))
                .acceptedQty(Objects.requireNonNullElse(item.getAcceptedQty(), 0))
                .defectiveQty(Objects.requireNonNullElse(item.getDefectiveQty(), 0))
                .remainingQty(Objects.requireNonNullElse(item.getRemainingQty(), 0))
                .unitPrice(Objects.requireNonNullElse(item.getUnitPrice(), BigDecimal.ZERO))
                .note(item.getNote())
                .build();
    }

    private String resolveItemImageUrl(ProductVariant variant, Product product) {
        String variantImage = firstImageUrl(variant != null ? variant.getImageUrl() : null);
        if (variantImage != null) {
            return variantImage;
        }

        if (product == null || product.getProductImages() == null) {
            return null;
        }

        return product.getProductImages().stream()
                .map(image -> firstImageUrl(image != null ? image.getImageUrl() : null))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private String firstImageUrl(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return null;
        }

        return Arrays.stream(imageUrl.split(","))
                .map(String::trim)
                .filter(url -> !url.isBlank())
                .findFirst()
                .orElse(null);
    }
}

