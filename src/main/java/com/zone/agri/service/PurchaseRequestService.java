package com.zone.agri.service;

import com.zone.agri.common.WarehouseContext;
import com.zone.agri.dto.request.purchase.PurchaseRequestCreateRequest;
import com.zone.agri.dto.response.purchase.PurchaseRequestResponse;
import com.zone.agri.entity.*;
import com.zone.agri.entity.enums.PurchaseRequestStatus;
import com.zone.agri.exception.BadRequestException;
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

    // ─────────────────────────────────────────────────────────────────────────
    // 1. TẠO PHIẾU YÊU CẦU MUA
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    public PurchaseRequestResponse createRequest(PurchaseRequestCreateRequest request) {
        Supplier supplier = supplierRepository.findByCode(request.getSupplierCode())
                .orElseThrow(() -> new NotFoundException("Nhà cung cấp không tồn tại: " + request.getSupplierCode()));

        if (supplier.getStatus() == com.zone.agri.entity.enums.SupplierStatus.INACTIVE) {
            throw new BadRequestException("Nhà cung cấp đang tạm ngừng giao dịch. Không thể tạo phiếu yêu cầu mua.");
        }

        Branch branch = resolveRequestBranch(request);
        validatePurchaseRequestBranch(branch);
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

            validateSupplierCatalogContainsVariant(supplier, variant);
            BigDecimal unitPrice = Objects.requireNonNullElse(itemReq.getUnitPrice(), BigDecimal.ZERO);

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
        validatePurchaseRequestBranch(branch);
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
            validateSupplierCatalogContainsVariant(supplier, variant);
        }

        BigDecimal totalAmount = BigDecimal.ZERO;
        for (PurchaseRequestCreateRequest.ItemRequest itemReq : request.getItems()) {
            ProductVariant variant = productVariantRepository.findBySku(itemReq.getProductCode())
                    .orElseThrow(() -> new NotFoundException("SKU không tồn tại: " + itemReq.getProductCode()));

            BigDecimal unitPrice = Objects.requireNonNullElse(itemReq.getUnitPrice(), BigDecimal.ZERO);

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
    public List<PurchaseRequestResponse> getAllRequests() {
        Long branchId = warehouseContext.resolveWarehouseId();
        List<PurchaseRequest> list = (branchId == null)
                ? purchaseRequestRepository.findAllWithRelations()
                : purchaseRequestRepository.findAllByBranchWithRelations(branchId);

        return list.stream().map(this::mapToResponseShallow).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public PurchaseRequestResponse getById(Long id) {
        PurchaseRequest pr = purchaseRequestRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy phiếu yêu cầu mua ID: " + id));
        warehouseContext.assertAccess(pr.getBranch().getId());
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
        warehouseContext.assertAccess(pr.getBranch().getId());
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
        warehouseContext.assertAccess(pr.getBranch().getId());
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
        warehouseContext.assertAccess(pr.getBranch().getId());
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

        purchaseRequestRepository.save(pr);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // MAPPING
    // ─────────────────────────────────────────────────────────────────────────

    private PurchaseRequest findOrThrow(Long id) {
        return purchaseRequestRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy phiếu yêu cầu mua ID: " + id));
    }

    private void validatePurchaseRequestBranch(Branch branch) {
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

    private void validateSupplierCatalogContainsVariant(Supplier supplier, ProductVariant variant) {
        if (supplier == null || variant == null) {
            throw new BadRequestException("Dữ liệu nhà cung cấp hoặc sản phẩm không hợp lệ. Vui lòng tải lại trang và thử lại.");
        }

        boolean isAvailableForSupplier = supplierProductCatalogRepository.existsAvailableBySupplierIdAndProductVariantId(
                supplier.getId(),
                variant.getId());

        if (!isAvailableForSupplier) {
            throw new BadRequestException(
                    "SKU " + variant.getSku() + " không nằm trong catalog đang bán của nhà cung cấp "
                            + supplier.getCode() + ". Vui lòng chọn sản phẩm trong danh sách catalog của nhà cung cấp.");
        }
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
        return PurchaseRequestResponse.ItemResponse.builder()
                .id(item.getId())
                .productVariantId(variant != null ? variant.getId() : null)
                .productCode(variant != null ? variant.getSku() : "")
                .productName(variant != null && variant.getProduct() != null ? variant.getProduct().getName() : "")
                .imageUrl(variant != null ? variant.getImageUrl() : null)
                .requestedQty(Objects.requireNonNullElse(item.getRequestedQty(), 0))
                .deliveredQty(Objects.requireNonNullElse(item.getDeliveredQty(), 0))
                .acceptedQty(Objects.requireNonNullElse(item.getAcceptedQty(), 0))
                .defectiveQty(Objects.requireNonNullElse(item.getDefectiveQty(), 0))
                .remainingQty(Objects.requireNonNullElse(item.getRemainingQty(), 0))
                .unitPrice(Objects.requireNonNullElse(item.getUnitPrice(), BigDecimal.ZERO))
                .note(item.getNote())
                .build();
    }
}

