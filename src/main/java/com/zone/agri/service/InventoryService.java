package com.zone.agri.service;

import com.zone.agri.dto.request.inventory.InventoryQCRequest;
import com.zone.agri.dto.request.inventory.InventoryReceiptRequest;
import com.zone.agri.dto.response.inventory.InventoryReceiptResponse;
import com.zone.agri.common.AuthUtils;
import com.zone.agri.common.InventoryExpiryPolicy;
import com.zone.agri.common.RoleUtils;
import com.zone.agri.entity.*;
import com.zone.agri.entity.enums.InventoryNoteStatus;
import com.zone.agri.entity.enums.InventoryNoteType;
import com.zone.agri.entity.enums.PurchaseRequestStatus;
import com.zone.agri.entity.enums.TransactionType;
import com.zone.agri.exception.BadRequestException;
import com.zone.agri.exception.NotFoundException;
import com.zone.agri.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

// Lazy injection để tránh circular dependency
import org.springframework.context.ApplicationContext;

@Service
@RequiredArgsConstructor
public class InventoryService {
    private final InventoryNoteRepository noteRepository;
    private final InventoryNoteDetailRepository noteDetailRepository; // Bổ sung
    private final InventoryRepository inventoryRepository;
    private final ProductVariantRepository variantRepository;
    private final BranchRepository branchRepository;
    private final SupplierRepository supplierRepository;
    private final UserRepository userRepository;
    private final InventoryTransactionRepository transactionRepository;
    private final BackorderService backorderService;
    private final com.zone.agri.common.WarehouseContext warehouseContext;
    private final PurchaseRequestRepository purchaseRequestRepository;
    private final InventoryCheckGuardService inventoryCheckGuardService;
    private final ApplicationContext applicationContext; // Dùng để lazy-get PurchaseRequestService tránh circular dep

    private Long resolveImportListBranchId() {
        return canReadImportsAcrossBranches() ? null : warehouseContext.resolveWarehouseId();
    }

    private void assertImportReadAccess(InventoryNote note) {
        if (!canReadImportsAcrossBranches()) {
            warehouseContext.assertAccess(note.getBranch().getId());
        }
    }

    private void assertImportReadOrApproveAccess(InventoryNote note) {
        if (!canApproveImportsAcrossBranches()) {
            warehouseContext.assertAccess(note.getBranch().getId());
        }
    }

    private boolean canApproveImportsAcrossBranches() {
        return hasAuthority("IMPORT_APPROVE")
                && RoleUtils.hasAdminLikeAuthority(AuthUtils.getAuthorities());
    }

    private boolean canReadImportsAcrossBranches() {
        com.zone.agri.dto.response.user.UserDetail user = AuthUtils.getUserDetail();
        String roleSlug = user != null && user.getRole() != null ? user.getRole().getSlug() : null;
        return RoleUtils.isAdminLikeRole(roleSlug)
                || RoleUtils.hasAdminLikeAuthority(AuthUtils.getAuthorities());
    }
    private static final Set<InventoryNoteStatus> OPEN_RECEIPT_STATUSES = Set.of(
            InventoryNoteStatus.PENDING,
            InventoryNoteStatus.APPROVED
    );

    private User getCurrentUser() {
        org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return null; // Tránh throw exception nếu flow không bắt buộc login (tùy config)
        }
        return userRepository.findByEmail(auth.getName()).orElse(null);
    }

    private boolean hasAuthority(String authority) {
        org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(authority));
    }

    private boolean isWarehouseBranch(Branch branch) {
        return branch != null
                && branch.getBranchType() != null
                && "WAREHOUSE".equalsIgnoreCase(branch.getBranchType());
    }

    private void validateWarehouseBranch(Branch branch, String message) {
        if (!isWarehouseBranch(branch)) {
            throw new BadRequestException(message);
        }
    }

    // --- 1. TẠO PHIẾU MỚI ---
    @Transactional
    public InventoryReceiptResponse createReceipt(InventoryReceiptRequest request) {
        PurchaseRequest linkedPurchaseRequest = resolveLinkedPurchaseRequestForCreate(request);
        Branch destBranch = resolveDestinationBranch(request, linkedPurchaseRequest);
        warehouseContext.assertAccess(destBranch.getId());
        inventoryCheckGuardService.assertNoOpenCheckForBranch(destBranch.getId(), "tạo phiếu nhập kho");

        if ("SUPPLIER".equals(request.getImportType()) && resolveSupplierCode(request, linkedPurchaseRequest) == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Lỗi: Nhập từ NCC phải có mã nhà cung cấp.");
        }

        InventoryNote noteEntity = new InventoryNote();
        noteEntity.setCode(request.getReceiptCode());
        noteEntity.setType(InventoryNoteType.IMPORT);
        noteEntity.setCreatedAt(LocalDateTime.now());
        noteEntity.setDetails(new ArrayList<>());

        Supplier supplier = resolveSupplier(request, linkedPurchaseRequest);
        updateMetadata(noteEntity, request, destBranch, supplier);
        noteEntity.setPurchaseRequest(linkedPurchaseRequest);

        // Quy trình 2: GR luôn bắt đầu ở PENDING_GR_APPROVAL (= PENDING).
        // Kể cả Admin tạo cũng phải PENDING để có bước kiểm tra trước khi duyệt.
        // Bước duyệt chỉ xác nhận chứng từ; tồn kho và công nợ được chốt ở bước QC/complete.
        boolean autoApprovedOnCreate =
                "IMPORTED".equalsIgnoreCase(request.getImportStatus()) || hasAuthority("IMPORT_APPROVE");
        noteEntity.setStatus(autoApprovedOnCreate ? InventoryNoteStatus.APPROVED : InventoryNoteStatus.PENDING);

        noteEntity = noteRepository.save(noteEntity);
        validateReceiptItemsAgainstPurchaseRequest(linkedPurchaseRequest, request.getItems(), noteEntity.getId());
        processItemsAndStock(noteEntity, request.getItems(), request.getImportType());

        noteEntity = noteRepository.save(noteEntity);
        if (noteEntity.getStatus() == InventoryNoteStatus.APPROVED && hasStoredQcDecision(noteEntity)) {
            noteEntity = finalizeReceiptCompletion(noteEntity);
        }

        return mapToResponse(noteEntity);
    }

    // --- 2. CẬP NHẬT PHIẾU ---
    @Transactional
    public InventoryReceiptResponse updateReceipt(Long id, InventoryReceiptRequest request) {
        InventoryNote existingNote = noteRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy phiếu ID: " + id));
        warehouseContext.assertAccess(existingNote.getBranch().getId());
        inventoryCheckGuardService.assertNoOpenCheckForBranch(existingNote.getBranch().getId(), "cập nhật phiếu nhập kho");

        if (existingNote.getStatus() == InventoryNoteStatus.COMPLETED) {
            throw new BadRequestException("Phiếu đã nhập kho thành công, không thể sửa đổi.");
        }

        PurchaseRequest linkedPurchaseRequest = resolveLinkedPurchaseRequestForUpdate(existingNote, request);
        Branch destBranch = resolveDestinationBranch(request, linkedPurchaseRequest);
        warehouseContext.assertAccess(destBranch.getId());
        Supplier supplier = resolveSupplier(request, linkedPurchaseRequest);

        updateMetadata(existingNote, request, destBranch, supplier);
        existingNote.setPurchaseRequest(linkedPurchaseRequest);

        // Clear and re-process items
        existingNote.getDetails().clear();
        noteRepository.flush();

        validateReceiptItemsAgainstPurchaseRequest(linkedPurchaseRequest, request.getItems(), existingNote.getId());
        processItemsAndStock(existingNote, request.getItems(), request.getImportType());
        return mapToResponse(noteRepository.save(existingNote));
    }

    // DUYET PHIEU NHAP (Giai doan 2): chi xac nhan chung tu.
    // Ton kho va cong no NCC chi duoc chot khi completeReceipt hoan tat QC.
    @Transactional
    public InventoryReceiptResponse approveReceipt(Long id) {
        InventoryNote note = noteRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new NotFoundException("Khong tim thay phieu ID: " + id));
        assertImportReadOrApproveAccess(note);

        if (note.getStatus() != InventoryNoteStatus.PENDING) {
            throw new BadRequestException("Chi co the duyet phieu dang o trang thai Cho duyet (PENDING).");
        }
        if (note.getType() != InventoryNoteType.IMPORT) {
            throw new BadRequestException("Endpoint nay chi dung de duyet phieu nhap kho (IMPORT).");
        }

        note.setStatus(InventoryNoteStatus.APPROVED);
        return mapToResponse(noteRepository.save(note));
    }
    @Transactional
    public InventoryReceiptResponse rejectReceipt(Long id) {
        InventoryNote note = noteRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy phiếu ID: " + id));
        assertImportReadOrApproveAccess(note);
        if (note.getStatus() != InventoryNoteStatus.PENDING) {
            throw new BadRequestException("Chỉ có thể từ chối phiếu đang ở trạng thái Chờ duyệt.");
        }
        note.setStatus(InventoryNoteStatus.REJECTED);
        return mapToResponse(noteRepository.save(note));
    }

    // --- 2.2. HOÀN TẤT NHẬP KHO (Sau khi kiểm đếm - QC) ---
    @Transactional
    public InventoryReceiptResponse completeReceipt(Long id, InventoryQCRequest qcRequest) {
        InventoryNote note = noteRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Khong tim thay phieu ID: " + id));
        assertImportReadOrApproveAccess(note);

        inventoryCheckGuardService.assertStockMutationAllowed(
                note.getBranch().getId(),
                note.getDetails().stream().map(detail -> detail.getProductVariant().getId()).toList(),
                "xac nhan nhap kho"
        );
        if (note.getStatus() != InventoryNoteStatus.APPROVED) {
            throw new BadRequestException("Phieu phai o trang thai Da duyet moi co the hoan tat nhap kho.");
        }

        Map<String, InventoryQCRequest.ItemQCRequest> qcMap = qcRequest.getItems().stream()
                .collect(Collectors.toMap(
                        InventoryQCRequest.ItemQCRequest::getProductCode,
                        i -> i,
                        (first, duplicate) -> {
                            throw new BadRequestException("Danh sach QC khong duoc chua SKU trung lap: " + first.getProductCode());
                        }));

        for (InventoryNoteDetail detail : note.getDetails()) {
            String sku = detail.getProductVariant().getSku();
            InventoryQCRequest.ItemQCRequest qcItem = qcMap.get(sku);
            if (qcItem == null) {
                throw new BadRequestException("Thieu du lieu QC cho SKU " + sku + ".");
            }

            int acceptedQty = Objects.requireNonNullElse(qcItem.getQuantityReal(), 0);
            int defectiveQty = Objects.requireNonNullElse(qcItem.getQuantityRejected(), 0);
            int deliveredQty = resolveDeliveredQty(qcItem, acceptedQty, defectiveQty);

            detail.setQuantityReal(deliveredQty);
            detail.setQuantityAccepted(acceptedQty);
            detail.setQuantityRejected(defectiveQty);
            validateReceiptDetailQuantities(note, detail);

            if (qcItem.getLotNumber() != null && !qcItem.getLotNumber().isBlank()) {
                detail.setBatchNumber(qcItem.getLotNumber());
            }
            if (qcItem.getExpiryDate() != null && !qcItem.getExpiryDate().isBlank()) {
                detail.setExpiryDate(parseExpiryDate(qcItem.getExpiryDate()));
            }
            if (qcItem.getNote() != null) {
                detail.setNote(qcItem.getNote());
            }
            InventoryExpiryPolicy.assertRequiredForReceipt(
                    detail.getProductVariant(),
                    detail.getBatchNumber(),
                    detail.getExpiryDate());
        }

        return mapToResponse(finalizeReceiptCompletion(note));
    }
    private boolean hasStoredQcDecision(InventoryNote note) {
        return note.getDetails() != null && note.getDetails().stream().anyMatch(detail ->
                Objects.requireNonNullElse(detail.getQuantityAccepted(), 0) > 0
                        || Objects.requireNonNullElse(detail.getQuantityRejected(), 0) > 0
                        || Objects.requireNonNullElse(detail.getQuantityReal(), 0) > 0
        );
    }

    private InventoryNote finalizeReceiptCompletion(InventoryNote note) {
        BigDecimal totalAmount = BigDecimal.ZERO;

        for (InventoryNoteDetail detail : note.getDetails()) {
            int acceptedQty = Objects.requireNonNullElse(detail.getQuantityAccepted(), 0);
            int defectiveQty = Objects.requireNonNullElse(detail.getQuantityRejected(), 0);
            int deliveredQty = Objects.requireNonNullElse(detail.getQuantityReal(), acceptedQty + defectiveQty);

            detail.setQuantityReal(deliveredQty);
            validateReceiptDetailQuantities(note, detail);
            updateStockWithQC(note, detail);

            if (acceptedQty > 0) {
                backorderService.fulfillBackordersOnStockReceive(
                        note.getBranch().getId(),
                        detail.getProductVariant().getId(),
                        acceptedQty);
            }

            BigDecimal itemTotal = Objects.requireNonNullElse(detail.getPrice(), BigDecimal.ZERO)
                    .multiply(BigDecimal.valueOf(acceptedQty));
            totalAmount = totalAmount.add(itemTotal);
        }

        note.setTotalAmount(totalAmount);
        note.setDebtAmount(totalAmount.subtract(Objects.requireNonNullElse(note.getPaymentAmount(), BigDecimal.ZERO)));
        note.setStatus(InventoryNoteStatus.COMPLETED);

        InventoryNote savedNote = noteRepository.save(note);
        if (savedNote.getPurchaseRequest() != null) {
            PurchaseRequestService prService = applicationContext.getBean(PurchaseRequestService.class);
            prService.updateCumulativeQtyAfterReceipt(savedNote);
        }

        return savedNote;
    }

    private void updateStockWithQC(InventoryNote note, InventoryNoteDetail detail) {
        ProductVariant variant = detail.getProductVariant();
        String batch = detail.getBatchNumber();
        BigDecimal price = detail.getPrice();
        LocalDateTime expiry = detail.getExpiryDate();

        int accepted = Objects.requireNonNullElse(detail.getQuantityAccepted(), 0);
        int rejected = Objects.requireNonNullElse(detail.getQuantityRejected(), 0);

        if (accepted > 0 || rejected > 0) {
            updateStockBalanceExactBatch(note, note.getBranch(), variant, batch, price, expiry, accepted, rejected, TransactionType.IMPORT);
        }
    }

    private void updateStockBalanceExactBatch(InventoryNote note, Branch branch, ProductVariant variant, String batchNumber, 
                                            BigDecimal importPrice, LocalDateTime expiryDate, 
                                            Integer acceptedQty, Integer rejectedQty, TransactionType type) {
        Inventory inv = inventoryRepository.findExactBatchWithLock(branch, variant, batchNumber, importPrice, expiryDate)
                .orElseGet(() -> {
                    Inventory newInv = Inventory.builder()
                        .branch(branch)
                        .productVariant(variant)
                        .batchNumber(batchNumber)
                        .importPrice(importPrice)
                        .expiryDate(expiryDate)
                        .quantity(0)
                        .defectiveQuantity(0)
                        .build();
                    return inventoryRepository.save(newInv);
                });

        int aQty = Objects.requireNonNullElse(acceptedQty, 0);
        int rQty = Objects.requireNonNullElse(rejectedQty, 0);

        if (aQty > 0) {
            inv.setQuantity(Objects.requireNonNullElse(inv.getQuantity(), 0) + aQty);
        }
        if (rQty > 0) {
            inv.setDefectiveQuantity(Objects.requireNonNullElse(inv.getDefectiveQuantity(), 0) + rQty);
        }
        
        inv.setLastReceiptDate(LocalDateTime.now());
        inv = inventoryRepository.save(inv);

        int balanceAfterAccepted = physicalBalance(inv) - rQty;
        if (aQty > 0) {
            transactionRepository.save(InventoryTransaction.builder()
                    .type(type)
                    .quantityChange(aQty)
                    .newBalance(balanceAfterAccepted)
                    .referenceCode(note != null ? note.getCode() : null)
                    .reason(note != null ? "Nhap kho hang dat QC (Phieu: " + note.getCode() + ")" : "Nhap kho hang dat QC")
                    .createdAt(LocalDateTime.now())
                    .inventory(inv)
                    .inventoryNote(note)
                    .build());
        }
        if (rQty > 0) {
            transactionRepository.save(InventoryTransaction.builder()
                    .type(type)
                    .quantityChange(rQty)
                    .newBalance(physicalBalance(inv))
                    .referenceCode(note != null ? note.getCode() : null)
                    .reason(note != null ? "Nhap kho hang loi QC (Phieu: " + note.getCode() + ")" : "Nhap kho hang loi QC")
                    .createdAt(LocalDateTime.now())
                    .inventory(inv)
                    .inventoryNote(note)
                    .build());
        }
    }

    private void updateMetadata(InventoryNote note, InventoryReceiptRequest request, Branch destBranch, Supplier supplier) {
        if ("SUPPLIER".equals(request.getImportType())) {
            validateWarehouseBranch(destBranch,
                    "Chỉ các chi nhánh loại kho mới được phép thực hiện nghiệp vụ nhập hàng từ nhà cung cấp.");
        }

        note.setBranch(destBranch);
        note.setNote(request.getNote());
        note.setDeliverer(request.getDeliverer());
        note.setCreatedBy(getCurrentUser()); // Set Auditor
        if (note.getPaymentAmount() == null) {
            note.setPaymentAmount(BigDecimal.ZERO);
        }
        if (note.getDebtAmount() == null) {
            note.setDebtAmount(BigDecimal.ZERO);
        }

        if (request.getEntryDate() != null && !request.getEntryDate().isBlank()) {
            note.setEntryDate(LocalDate.parse(request.getEntryDate()).atStartOfDay());
        }

        if (request.getTags() != null && !request.getTags().isEmpty()) {
            note.setTags(String.join(",", request.getTags()));
        } else {
            note.setTags(null);
        }

        note.setSupplier(supplier);
        note.setPartnerBranch(null);
    }

    // --- 3. XÓA PHIẾU (CHẶN NẾU ĐÃ NHẬP KHO) ---
    @Transactional
    public void deleteReceipt(Long id) {
        InventoryNote note = noteRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy phiếu ID: " + id));
        warehouseContext.assertAccess(note.getBranch().getId());

        if (note.getStatus() == InventoryNoteStatus.COMPLETED) {
            throw new BadRequestException("Không thể xóa phiếu đã hoàn thành nhập kho. Vui lòng sử dụng phiếu xuất trả hoặc điều chỉnh kho để đảm bảo tính nhất quán dữ liệu.");
        }
        noteRepository.delete(note);
    }

    // --- LOGIC DÙNG CHUNG: XỬ LÝ ITEMS & CỘNG KHO ---
    private void processItemsAndStock(InventoryNote note, List<InventoryReceiptRequest.ItemRequest> items, String importType) {
        if (items == null) return;
        
        BigDecimal totalAmount = BigDecimal.ZERO;

        for (InventoryReceiptRequest.ItemRequest itemDTO : items) {
            ProductVariant variant = variantRepository.findBySku(itemDTO.getProductCode())
                    .orElseThrow(() -> new NotFoundException("SKU không tồn tại: " + itemDTO.getProductCode()));

            BigDecimal importPrice = Objects.requireNonNullElse(itemDTO.getImportPrice(), BigDecimal.ZERO);
            int deliveredQty = Objects.requireNonNullElse(itemDTO.getQuantityReal(), 0);
            int acceptedQty = Objects.requireNonNullElse(itemDTO.getQuantityAccepted(), 0);
            int rejectedQty = Objects.requireNonNullElse(itemDTO.getQuantityRejected(), 0);
            validateReceiptItemQuantities(itemDTO.getProductCode(), itemDTO.getPlannedQuantity(), deliveredQty, acceptedQty, rejectedQty);
            BigDecimal itemSubtotal = importPrice.multiply(BigDecimal.valueOf(acceptedQty));
            totalAmount = totalAmount.add(itemSubtotal);

            LocalDateTime expiry = parseExpiryDate(itemDTO.getExpiryDate());
            String requestedBatch = normalizeBatchNumber(itemDTO.getLotNumber());
            InventoryExpiryPolicy.assertRequiredForReceipt(variant, requestedBatch, expiry);
            String batch = requestedBatch != null ? requestedBatch : "DEFAULT";

            InventoryNoteDetail detailEntity = InventoryNoteDetail.builder()
                    .inventoryNote(note)
                    .productVariant(variant)
                    .quantity(itemDTO.getPlannedQuantity())
                    .quantityRequested(itemDTO.getPlannedQuantity())
                    .quantityReal(deliveredQty)
                    .quantityAccepted(acceptedQty)
                    .quantityRejected(rejectedQty)
                    .price(importPrice)
                    .batchNumber(batch)
                    .expiryDate(expiry)
                    .newSellingPrice(itemDTO.getNewSellingPrice())
                    .build();

            note.getDetails().add(detailEntity);

            // Chỉ cộng kho nếu trạng thái là COMPLETED (Sau khi đã QC)
            if (note.getStatus() == InventoryNoteStatus.COMPLETED) {

                updateStockWithQC(note, detailEntity);

            }
        }
        note.setTotalAmount(totalAmount);
        note.setDebtAmount(totalAmount.subtract(Objects.requireNonNullElse(note.getPaymentAmount(), BigDecimal.ZERO)));
    }

    // Hàm cập nhật đích danh một Lô (Dùng cho nhập kho) - Giữ lại version cũ cho các hàm khác nếu cần
    private void updateStockBalanceExactBatch(InventoryNote note, Branch branch, ProductVariant variant, String batchNumber, BigDecimal importPrice, LocalDateTime expiryDate, Integer quantityChange, TransactionType type) {
        updateStockBalanceExactBatch(note, branch, variant, batchNumber, importPrice, expiryDate, quantityChange, 0, type);
    }

    // Hàm trừ kho theo nguyên tắc FIFO (Dùng khi xuất kho/chuyển nội bộ)
    private void deductStockFifo(InventoryNote note, Branch branch, ProductVariant variant, int quantityToDeduct) {
        // Dùng findForUpdateFIFO (đã có @Lock) để tránh Race Condition
        List<Inventory> batches = inventoryRepository.findForUpdateFIFO(branch.getId(), variant.getId());
        
        int totalAvailable = batches.stream().mapToInt(i -> Objects.requireNonNullElse(i.getQuantity(), 0)).sum();
        if (totalAvailable < quantityToDeduct) {
            throw new BadRequestException("Kho " + branch.getName() + " không đủ hàng để điều chuyển sản phẩm " + variant.getSku());
        }

        int remaining = quantityToDeduct;
        for (Inventory batch : batches) {
            if (remaining <= 0) break;
            int deduct = Math.min(batch.getQuantity(), remaining);
            int oldQty = batch.getQuantity();
            int newQty = oldQty - deduct;
            
            batch.setQuantity(newQty);
            inventoryRepository.save(batch);

            // Ghi log biến động kho cho từng lô
            InventoryTransaction tx = InventoryTransaction.builder()
                    .type(TransactionType.TRANSFER_OUT)
                    .quantityChange(-deduct)
                    .newBalance(physicalBalance(batch))
                    .referenceCode(note != null ? note.getCode() : null)
                    .reason(note != null ? "Xuất kho FIFO từ phiếu: " + note.getCode() : "Xuất kho FIFO")
                    .createdAt(LocalDateTime.now())
                    .inventory(batch)
                    .inventoryNote(note)
                    .build();
            transactionRepository.save(tx);

            remaining -= deduct;
        }

        if (remaining > 0) {
            throw new BadRequestException("Lỗi hụt hàng trong lúc xử lý tại kho " + branch.getName());
        }
    }

    private int physicalBalance(Inventory inventory) {
        return Objects.requireNonNullElse(inventory.getQuantity(), 0)
                + Objects.requireNonNullElse(inventory.getDefectiveQuantity(), 0);
    }

    private String normalizeBatchNumber(String batchNumber) {
        if (batchNumber == null || batchNumber.isBlank()) {
            return null;
        }
        return batchNumber.trim();
    }

    private LocalDateTime parseExpiryDate(String expiryDate) {
        if (expiryDate == null || expiryDate.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(expiryDate.trim()).atStartOfDay();
        } catch (Exception ex) {
            throw new BadRequestException("Hạn sử dụng không hợp lệ: " + expiryDate);
        }
    }

    // --- 4 & 5. LẤY DANH SÁCH & CHI TIẾT ---
    @Transactional(readOnly = true)
    public List<InventoryReceiptResponse> getAllReceipts(Long branchId) {
        Long warehouseId = (branchId != null) ? branchId : resolveImportListBranchId();
        List<InventoryNote> notes = (warehouseId == null) 
                ? noteRepository.findAllByTypeWithPartners(InventoryNoteType.IMPORT)
                : noteRepository.findAllByTypeAndBranchWithPartners(InventoryNoteType.IMPORT, warehouseId);

        return notes.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public InventoryReceiptResponse getReceiptById(Long id) {
        InventoryNote note = noteRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy phiếu ID: " + id));
        assertImportReadAccess(note);
        return mapToResponse(note);
    }

    @Transactional(readOnly = true)
    public List<com.zone.agri.dto.response.inventory.InventorySearchResponse> getDefectiveItemsBySupplier(Long supplierId, Long explicitBranchId) {
        // Ưu tiên branchId truyền từ FE lên, nếu không có mới dùng context
        Long branchId = (explicitBranchId != null) ? explicitBranchId : warehouseContext.resolveWarehouseId();

        if (branchId == null) {
            return new ArrayList<>();
        }

        // 1. Tìm tất cả các lô có hàng lỗi tại chi nhánh này
        List<Inventory> defectiveInventories = inventoryRepository.findAllByBranchIdAndDefectiveQuantityGreaterThan(branchId, 0);
        
        // 2. Lọc theo Nhà cung cấp bằng cách truy vết ngược về phiếu nhập gốc (IMPORT) 
        // dựa trên SKU và BatchNumber (vì BatchNumber đã được giữ nguyên khi điều chuyển)
        return defectiveInventories.stream()
                .filter(inv -> {
                    List<InventoryNoteDetail> importDetails = noteDetailRepository.findOriginalImportDetailBySkuAndBatch(
                            inv.getProductVariant().getSku(), 
                            inv.getBatchNumber()
                    );
                    
                    return importDetails.stream()
                            .anyMatch(d -> d.getInventoryNote().getSupplier() != null && 
                                          d.getInventoryNote().getSupplier().getId().equals(supplierId));
                })
                .map(inv -> {
                    ProductVariant variant = inv.getProductVariant();
                    // Lấy thông tin từ phiếu nhập đầu tiên tìm được
                    List<InventoryNoteDetail> importDetails = noteDetailRepository.findOriginalImportDetailBySkuAndBatch(
                            variant.getSku(), 
                            inv.getBatchNumber()
                    );
                    
                    var originalDetail = importDetails.stream()
                            .filter(d -> d.getInventoryNote().getSupplier() != null &&
                                    d.getInventoryNote().getSupplier().getId().equals(supplierId))
                            .findFirst();

                    Integer originalPlannedQty = originalDetail.map(d -> d.getQuantity()).orElse(0);
                    String originalReason = originalDetail.map(d -> d.getNote()).orElse("Hàng lỗi chờ xử lý");

                    Long receiptId = originalDetail.map(d -> d.getInventoryNote().getId()).orElse(null);
                    String receiptCode = originalDetail.map(d -> d.getInventoryNote().getCode()).orElse(null);
                    LocalDateTime receiptDate = originalDetail.map(d -> d.getInventoryNote().getCreatedAt()).orElse(null);

                    return com.zone.agri.dto.response.inventory.InventorySearchResponse.builder()
                            .variantId(variant.getId())
                            .sku(variant.getSku())
                            .productName(variant.getProduct().getName())
                            .variantName(variant.getCustomSpecs())
                            .batchNumber(inv.getBatchNumber())
                            .quantity(inv.getQuantity()) 
                            .defectiveQuantity(inv.getDefectiveQuantity()) 
                            .plannedQuantity(originalPlannedQty) 
                            .reason(originalReason)
                            .importPrice(inv.getImportPrice())
                            .expiryDate(inv.getExpiryDate())
                            .imageUrl(variant.getImageUrl())
                            .unit("Cái")
                            .supplierId(supplierId)
                            .receiptId(receiptId)
                            .receiptCode(receiptCode)
                            .receiptDate(receiptDate)
                            .branchName(inv.getBranch() != null ? inv.getBranch().getName() : null)
                            .build();
                })
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<com.zone.agri.dto.response.inventory.InventorySearchResponse> getAllDefectiveItemsWithSuppliers() {
        return getAllDefectiveItemsWithSuppliers(null);
    }

    @Transactional(readOnly = true)
    public List<com.zone.agri.dto.response.inventory.InventorySearchResponse> getAllDefectiveItemsWithSuppliers(Long explicitBranchId) {
        Long branchId = explicitBranchId != null ? explicitBranchId : warehouseContext.resolveWarehouseId();
        List<Inventory> defectiveInventories;
        
        if (branchId == null) {
            defectiveInventories = inventoryRepository.findAllByDefectiveQuantityGreaterThan(0);
        } else {
            defectiveInventories = inventoryRepository.findAllByBranchIdAndDefectiveQuantityGreaterThan(branchId, 0);
        }

        return defectiveInventories.stream()
                .map(inv -> {
                    ProductVariant variant = inv.getProductVariant();
                    List<InventoryNoteDetail> importDetails = noteDetailRepository.findOriginalImportDetailBySkuAndBatch(
                            variant.getSku(), 
                            inv.getBatchNumber()
                    );
                    
                    var originalDetail = importDetails.isEmpty() ? Optional.<InventoryNoteDetail>empty() : Optional.of(importDetails.get(0));
                    Supplier supplier = originalDetail.map(d -> d.getInventoryNote().getSupplier()).orElse(null);

                    return com.zone.agri.dto.response.inventory.InventorySearchResponse.builder()
                            .variantId(variant.getId())
                            .sku(variant.getSku())
                            .productName(variant.getProduct().getName())
                            .variantName(variant.getCustomSpecs())
                            .batchNumber(inv.getBatchNumber())
                            .quantity(inv.getQuantity()) 
                            .defectiveQuantity(inv.getDefectiveQuantity()) 
                            .importPrice(inv.getImportPrice())
                            .expiryDate(inv.getExpiryDate())
                            .imageUrl(variant.getImageUrl())
                            .unit("Cái")
                            .supplierId(supplier != null ? supplier.getId() : null)
                            .supplierName(supplier != null ? supplier.getName() : "Không xác định")
                            .branchName(inv.getBranch().getName())
                            .build();
                })
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<com.zone.agri.dto.response.inventory.InventorySearchResponse> searchInventoryForCheck(String keyword, Long explicitBranchId) {
        Long branchId = explicitBranchId != null ? explicitBranchId : warehouseContext.resolveWarehouseId();
        warehouseContext.assertAccess(branchId);
        return inventoryRepository.searchInventoryForCheck(keyword, branchId);
    }

    private InventoryReceiptResponse mapToResponse(InventoryNote entity) {
        if (entity == null) return null;
        boolean isInternal = entity.getPartnerBranch() != null;
        Long partnerBranchId = isInternal ? entity.getPartnerBranch().getId() : null;
        
        List<String> tagList = (entity.getTags() != null && !entity.getTags().isBlank()) 
                ? Arrays.asList(entity.getTags().split(",")) 
                : new ArrayList<>();

        String fullName = (entity.getCreatedBy() != null) ? entity.getCreatedBy().getFullName() : "Hệ thống";

        BigDecimal totalAmount = Objects.requireNonNullElse(entity.getTotalAmount(), BigDecimal.ZERO);
        BigDecimal paymentAmount = Objects.requireNonNullElse(entity.getPaymentAmount(), BigDecimal.ZERO);
        BigDecimal debtAmount = Objects.requireNonNullElse(
                entity.getDebtAmount(),
                totalAmount.subtract(paymentAmount)
        );

        List<InventoryReceiptResponse.ItemResponse> itemResponses = new ArrayList<>();
        if (entity.getDetails() != null) {
            itemResponses = entity.getDetails().stream().map(d -> {
                ProductVariant variant = d.getProductVariant();
                    return InventoryReceiptResponse.ItemResponse.builder()
                        .productVariantId(variant != null ? variant.getId() : null)
                        .variantId(variant != null ? variant.getId() : null)
                        .productCode(variant != null ? variant.getSku() : "")
                        .productName(variant != null && variant.getProduct() != null ? variant.getProduct().getName() : "")
                        .quantity(Objects.requireNonNullElse(d.getQuantity(), 0))
                        .quantityReal(Objects.requireNonNullElse(d.getQuantityReal(), 0))
                        .quantityAccepted(Objects.requireNonNullElse(d.getQuantityAccepted(), 0))
                        .quantityRejected(Objects.requireNonNullElse(d.getQuantityRejected(), 0))
                        .maxQuantity(0)
                        .price(Objects.requireNonNullElse(d.getPrice(), BigDecimal.ZERO))
                        .newSellingPrice(d.getNewSellingPrice())
                        .lotNumber(d.getBatchNumber())
                        .expiryDate(d.getExpiryDate() != null ? d.getExpiryDate().format(DateTimeFormatter.ISO_LOCAL_DATE) : "")
                        .imageUrl(variant != null ? variant.getImageUrl() : null)
                        .note(d.getNote())
                        .build();
            }).collect(Collectors.toList());
        }

        return InventoryReceiptResponse.builder()
                .id(entity.getId())
                .code(entity.getCode())
                .importType(isInternal ? "INTERNAL" : "SUPPLIER")
                .sourceBranchId(partnerBranchId)
                .status(entity.getStatus() != null ? entity.getStatus().name() : "PENDING")
                .purchaseRequestId(entity.getPurchaseRequest() != null ? entity.getPurchaseRequest().getId() : null)
                .purchaseRequestCode(entity.getPurchaseRequest() != null ? entity.getPurchaseRequest().getCode() : null)
                .supplierName(entity.getSupplier() != null ? entity.getSupplier().getName() : "")
                .supplierCode(entity.getSupplier() != null ? entity.getSupplier().getCode() : "")
                .branchId(entity.getBranch() != null ? entity.getBranch().getId() : null)
                .branchName(entity.getBranch() != null ? entity.getBranch().getName() : "N/A")
                .totalAmount(totalAmount)
                .paymentAmount(paymentAmount)
                .debtAmount(debtAmount)
                .deliverer(entity.getDeliverer())
                .creatorName(fullName)
                .createdByName(fullName)
                .note(entity.getNote())
                .tags(tagList)
                .createdAt(entity.getCreatedAt())
                .entryDate(entity.getEntryDate() != null ? entity.getEntryDate().format(DateTimeFormatter.ISO_LOCAL_DATE) : "")
                .items(itemResponses)
                .build();
    }

    private PurchaseRequest resolveLinkedPurchaseRequestForCreate(InventoryReceiptRequest request) {
        if (request.getPurchaseRequestId() == null) {
            throw new BadRequestException("Phiếu nhập nhà cung cấp bắt buộc phải liên kết với phiếu yêu cầu mua.");
        }
        PurchaseRequest pr = purchaseRequestRepository.findByIdWithDetails(request.getPurchaseRequestId())
                .orElseThrow(() -> new NotFoundException("Không tìm thấy phiếu yêu cầu mua ID: " + request.getPurchaseRequestId()));
        warehouseContext.assertAccess(pr.getBranch().getId());
        ensurePurchaseRequestReceivable(pr);
        return pr;
    }

    private PurchaseRequest resolveLinkedPurchaseRequestForUpdate(InventoryNote existingNote, InventoryReceiptRequest request) {
        if (existingNote.getPurchaseRequest() != null) {
            Long incomingId = request.getPurchaseRequestId();
            if (incomingId != null && !incomingId.equals(existingNote.getPurchaseRequest().getId())) {
                throw new BadRequestException("Không được đổi phiếu yêu cầu mua của phiếu nhập đã liên kết.");
            }
            PurchaseRequest pr = purchaseRequestRepository.findByIdWithDetails(existingNote.getPurchaseRequest().getId())
                    .orElseThrow(() -> new NotFoundException("Không tìm thấy phiếu yêu cầu mua ID: " + existingNote.getPurchaseRequest().getId()));
            warehouseContext.assertAccess(pr.getBranch().getId());
            ensurePurchaseRequestReceivable(pr);
            return pr;
        }
        return resolveLinkedPurchaseRequestForCreate(request);
    }

    private void ensurePurchaseRequestReceivable(PurchaseRequest pr) {
        Set<PurchaseRequestStatus> receivableStatuses = Set.of(
                PurchaseRequestStatus.DELIVERING,
                PurchaseRequestStatus.PARTIALLY_RECEIVED
        );
        if (!receivableStatuses.contains(pr.getStatus())) {
            throw new BadRequestException("Phiếu yêu cầu mua không ở trạng thái cho phép nhận hàng (trạng thái hiện tại: " + pr.getStatus() + ").");
        }
    }

    private Branch resolveDestinationBranch(InventoryReceiptRequest request, PurchaseRequest linkedPurchaseRequest) {
        if (linkedPurchaseRequest != null) {
            if (request.getBranchName() != null && !request.getBranchName().isBlank()
                    && !linkedPurchaseRequest.getBranch().getName().equals(request.getBranchName())) {
                throw new BadRequestException("Chi nhánh nhập của phiếu nhập phải trùng với chi nhánh trên phiếu yêu cầu mua.");
            }
            return linkedPurchaseRequest.getBranch();
        }

        return branchRepository.findByName(request.getBranchName())
                .orElseThrow(() -> new NotFoundException("Chi nhánh không tồn tại: " + request.getBranchName()));
    }

    private Supplier resolveSupplier(InventoryReceiptRequest request, PurchaseRequest linkedPurchaseRequest) {
        String supplierCode = resolveSupplierCode(request, linkedPurchaseRequest);
        if (!"SUPPLIER".equals(request.getImportType()) && linkedPurchaseRequest == null) {
            return null;
        }
        if (supplierCode == null || supplierCode.isBlank()) {
            throw new NotFoundException("Nhà cung cấp không tồn tại");
        }

        Supplier supplier = supplierRepository.findByCode(supplierCode)
                .orElseThrow(() -> new NotFoundException("Nhà cung cấp không tồn tại"));

        if (linkedPurchaseRequest != null && !supplier.getId().equals(linkedPurchaseRequest.getSupplier().getId())) {
            throw new BadRequestException("Nhà cung cấp của phiếu nhập phải trùng với nhà cung cấp trên phiếu yêu cầu mua.");
        }

        return supplier;
    }

    private String resolveSupplierCode(InventoryReceiptRequest request, PurchaseRequest linkedPurchaseRequest) {
        if (request.getSupplierCode() != null && !request.getSupplierCode().isBlank()) {
            return request.getSupplierCode();
        }
        return linkedPurchaseRequest != null && linkedPurchaseRequest.getSupplier() != null
                ? linkedPurchaseRequest.getSupplier().getCode()
                : null;
    }

    private void validateReceiptItemsAgainstPurchaseRequest(PurchaseRequest pr, List<InventoryReceiptRequest.ItemRequest> items, Long excludeNoteId) {
        if (pr == null || items == null) {
            return;
        }

        Map<String, PurchaseRequestItem> prItemsBySku = pr.getItems().stream()
                .filter(i -> i.getProductVariant() != null)
                .collect(Collectors.toMap(i -> i.getProductVariant().getSku(), i -> i, (a, b) -> a));

        Set<String> seenSkus = new HashSet<>();
        for (InventoryReceiptRequest.ItemRequest item : items) {
            String sku = item.getProductCode();
            if (!seenSkus.add(sku)) {
                throw new BadRequestException("Phiếu nhập không được chứa SKU trùng lặp: " + sku);
            }

            PurchaseRequestItem prItem = prItemsBySku.get(sku);
            if (prItem == null) {
                throw new BadRequestException("SKU " + sku + " không tồn tại trên phiếu yêu cầu mua.");
            }

            int remainingQty = Objects.requireNonNullElse(prItem.getRemainingQty(), 0);
            long reservedQty = noteRepository.sumReservedQtyByPurchaseRequestAndVariantAndStatuses(
                    pr.getId(),
                    prItem.getProductVariant().getId(),
                    OPEN_RECEIPT_STATUSES,
                    excludeNoteId
            );
            long availableQty = Math.max(0, remainingQty - reservedQty);

            if (item.getPlannedQuantity() > availableQty) {
                throw new BadRequestException("SKU " + sku + " vượt quá số lượng còn có thể lập phiếu nhập. Còn lại: " + availableQty + ".");
            }
        }
    }

    private void validateReceiptDetailQuantities(InventoryNote note, InventoryNoteDetail detail) {
        String sku = detail.getProductVariant().getSku();
        int plannedQty = Objects.requireNonNullElse(detail.getQuantityRequested(), 0);
        int acceptedQty = Objects.requireNonNullElse(detail.getQuantityAccepted(), 0);
        int defectiveQty = Objects.requireNonNullElse(detail.getQuantityRejected(), 0);
        int deliveredQty = Objects.requireNonNullElse(detail.getQuantityReal(), acceptedQty + defectiveQty);

        if (deliveredQty < 0 || acceptedQty < 0 || defectiveQty < 0) {
            throw new BadRequestException("SKU " + sku + ": so luong giao, dat va loi khong duoc am.");
        }
        if (acceptedQty + defectiveQty != deliveredQty) {
            throw new BadRequestException("SKU " + sku + ": so luong dat va loi phai cong lai dung bang so NCC giao.");
        }
        if (deliveredQty > plannedQty) {
            throw new BadRequestException("SKU " + sku + ": so NCC giao khong duoc vuot so luong da lap tren phieu nhap.");
        }
        if (note.getPurchaseRequest() != null) {
            validateCompletedQtyAgainstPurchaseRequest(
                    note.getPurchaseRequest().getId(),
                    detail.getProductVariant().getId(),
                    acceptedQty,
                    note.getId());
        }
    }

    private void validateReceiptItemQuantities(String sku, Integer plannedQuantity, int deliveredQty, int acceptedQty, int defectiveQty) {
        int plannedQty = Objects.requireNonNullElse(plannedQuantity, 0);
        if (deliveredQty < 0 || acceptedQty < 0 || defectiveQty < 0) {
            throw new BadRequestException("SKU " + sku + ": so luong giao, dat va loi khong duoc am.");
        }
        if (acceptedQty + defectiveQty != deliveredQty) {
            throw new BadRequestException("SKU " + sku + ": so luong dat va loi phai cong lai dung bang so NCC giao.");
        }
        if (deliveredQty > plannedQty) {
            throw new BadRequestException("SKU " + sku + ": so NCC giao khong duoc vuot so luong da lap tren phieu nhap.");
        }
    }

    private int resolveDeliveredQty(InventoryQCRequest.ItemQCRequest qcItem, int acceptedQty, int defectiveQty) {
        if (qcItem.getQuantityDelivered() != null) {
            return qcItem.getQuantityDelivered();
        }
        return acceptedQty + defectiveQty;
    }

    private void validateCompletedQtyAgainstPurchaseRequest(Long purchaseRequestId, Long variantId, int acceptedQty, Long currentNoteId) {
        PurchaseRequestItem prItem = purchaseRequestRepository.findByIdWithDetails(purchaseRequestId)
                .flatMap(pr -> pr.getItems().stream()
                        .filter(i -> i.getProductVariant() != null && i.getProductVariant().getId().equals(variantId))
                        .findFirst())
                .orElseThrow(() -> new BadRequestException("Sản phẩm không tồn tại trên phiếu yêu cầu mua."));

        int remainingQty = Objects.requireNonNullElse(prItem.getRemainingQty(), 0);
        if (acceptedQty > remainingQty) {
            throw new BadRequestException("Số lượng đạt QC vượt quá phần còn thiếu của phiếu yêu cầu.");
        }
    }
}
