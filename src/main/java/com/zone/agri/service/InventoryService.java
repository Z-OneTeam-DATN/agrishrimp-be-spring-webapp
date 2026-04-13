package com.zone.agri.service;

import com.zone.agri.dto.request.inventory.InventoryQCRequest;
import com.zone.agri.dto.request.inventory.InventoryReceiptRequest;
import com.zone.agri.dto.response.inventory.InventoryReceiptResponse;
import com.zone.agri.entity.*;
import com.zone.agri.entity.enums.InventoryNoteStatus;
import com.zone.agri.entity.enums.InventoryNoteType;
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

    // --- 1. TẠO PHIẾU MỚI ---
    @Transactional
    public InventoryReceiptResponse createReceipt(InventoryReceiptRequest request) {
        Branch destBranch = branchRepository.findByName(request.getBranchName())
                .orElseThrow(() -> new NotFoundException("Chi nhánh không tồn tại: " + request.getBranchName()));

        if ("SUPPLIER".equals(request.getImportType()) && request.getSupplierCode() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Lỗi: Nhập từ NCC phải có mã nhà cung cấp.");
        }

        InventoryNote noteEntity = new InventoryNote();
        noteEntity.setCode(request.getReceiptCode());
        noteEntity.setType(InventoryNoteType.IMPORT);
        noteEntity.setCreatedAt(LocalDateTime.now());
        noteEntity.setDetails(new ArrayList<>());

        updateMetadata(noteEntity, request, destBranch);
        
        // LUỒNG MỚI: Nếu là Admin (có quyền IMPORT_APPROVE) tạo thì APPROVED luôn, ngược lại PENDING
        if (hasAuthority("IMPORT_APPROVE")) {
            noteEntity.setStatus(InventoryNoteStatus.APPROVED);
        } else {
            noteEntity.setStatus(InventoryNoteStatus.PENDING);
        }

        noteEntity = noteRepository.save(noteEntity);
        processItemsAndStock(noteEntity, request.getItems(), request.getImportType());

        return mapToResponse(noteRepository.save(noteEntity));
    }

    // --- 2. CẬP NHẬT PHIẾU ---
    @Transactional
    public InventoryReceiptResponse updateReceipt(Long id, InventoryReceiptRequest request) {
        InventoryNote existingNote = noteRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy phiếu ID: " + id));

        if (existingNote.getStatus() == InventoryNoteStatus.COMPLETED) {
            throw new BadRequestException("Phiếu đã nhập kho thành công, không thể sửa đổi.");
        }

        Branch destBranch = branchRepository.findByName(request.getBranchName())
                .orElseThrow(() -> new NotFoundException("Chi nhánh không tồn tại: " + request.getBranchName()));

        updateMetadata(existingNote, request, destBranch);

        // Chặn không cho phép cập nhật status thành COMPLETED qua API update thông thường
        if (existingNote.getStatus() == InventoryNoteStatus.COMPLETED) {
             existingNote.setStatus(InventoryNoteStatus.APPROVED);
        }

        // Clear and re-process items
        existingNote.getDetails().clear();
        noteRepository.flush();

        processItemsAndStock(existingNote, request.getItems(), request.getImportType());
        return mapToResponse(noteRepository.save(existingNote));
    }

    // --- 2.1. DUYỆT PHIẾU (Cho Admin) ---
    @Transactional
    public InventoryReceiptResponse approveReceipt(Long id) {
        InventoryNote note = noteRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy phiếu ID: " + id));
        if (note.getStatus() != InventoryNoteStatus.PENDING) {
            throw new BadRequestException("Chỉ có thể duyệt phiếu đang ở trạng thái Chờ duyệt.");
        }
        note.setStatus(InventoryNoteStatus.APPROVED);
        return mapToResponse(noteRepository.save(note));
    }

    @Transactional
    public InventoryReceiptResponse rejectReceipt(Long id) {
        InventoryNote note = noteRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy phiếu ID: " + id));
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
                .orElseThrow(() -> new NotFoundException("Không tìm thấy phiếu ID: " + id));

        if (note.getStatus() != InventoryNoteStatus.APPROVED && note.getStatus() != InventoryNoteStatus.PENDING) {
            throw new BadRequestException("Phiếu phải ở trạng thái Đã duyệt hoặc Chờ duyệt mới có thể nhập kho.");
        }

        // Cập nhật thông tin kiểm đếm thực tế
        Map<String, InventoryQCRequest.ItemQCRequest> qcMap = qcRequest.getItems().stream()
                .collect(Collectors.toMap(InventoryQCRequest.ItemQCRequest::getProductCode, i -> i));

        BigDecimal totalAmount = BigDecimal.ZERO;
        for (InventoryNoteDetail detail : note.getDetails()) {
            InventoryQCRequest.ItemQCRequest qcItem = qcMap.get(detail.getProductVariant().getSku());
            if (qcItem != null) {
                // Yêu cầu từ người dùng: Hàng lỗi = Yêu cầu - Thực nhận
                int plannedQty = Objects.requireNonNullElse(detail.getQuantityRequested(), 0);
                int realGoodQty = Objects.requireNonNullElse(qcItem.getQuantityReal(), 0);
                int rejectedQty = Math.max(0, plannedQty - realGoodQty);
                
                detail.setQuantityReal(realGoodQty);
                detail.setQuantityAccepted(realGoodQty);
                detail.setQuantityRejected(rejectedQty);
                
                // CẬP NHẬT SỐ LÔ VÀ HẠN DÙNG (Nếu có thay đổi lúc kiểm đếm)
                if (qcItem.getLotNumber() != null && !qcItem.getLotNumber().isBlank()) {
                    detail.setBatchNumber(qcItem.getLotNumber());
                }
                if (qcItem.getExpiryDate() != null && !qcItem.getExpiryDate().isBlank()) {
                    detail.setExpiryDate(LocalDate.parse(qcItem.getExpiryDate()).atStartOfDay());
                }
                
                if (qcItem.getNote() != null) {
                    detail.setNote(qcItem.getNote());
                }
                
                // Cập nhật tồn kho một lần duy nhất cho cả hàng tốt và hàng lỗi
                updateStockWithQC(note, detail);
            }
            // Tổng tiền nợ NCC tính trên số lượng hàng tốt thực nhận (hoặc tùy nghiệp vụ có tính cả hàng lỗi không)
            // Ở đây tính trên số lượng thực tế nhập kho (Accepted)
            BigDecimal itemTotal = (detail.getPrice() != null ? detail.getPrice() : BigDecimal.ZERO)
                    .multiply(BigDecimal.valueOf(detail.getQuantityAccepted()));
            totalAmount = totalAmount.add(itemTotal);
        }

        note.setTotalAmount(totalAmount);
        // Cập nhật lại số nợ thực tế dựa trên hàng tốt thực nhận
        note.setDebtAmount(totalAmount.subtract(Objects.requireNonNullElse(note.getPaymentAmount(), BigDecimal.ZERO)));
        
        note.setStatus(InventoryNoteStatus.COMPLETED);
        return mapToResponse(noteRepository.save(note));
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
        Inventory inv = inventoryRepository.findExactBatchWithLock(branch, variant, batchNumber, importPrice)
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

        // Log biến động kho (chỉ log phần thay đổi)
        int change = aQty + rQty;
        transactionRepository.save(InventoryTransaction.builder()
                .type(type)
                .quantityChange(change)
                .newBalance(Objects.requireNonNullElse(inv.getQuantity(), 0) + Objects.requireNonNullElse(inv.getDefectiveQuantity(), 0))
                .referenceCode(note != null ? note.getCode() : null)
                .reason(note != null ? "Nhập kho QC (Phiếu: " + note.getCode() + ")" : "Cập nhật QC")
                .createdAt(LocalDateTime.now())
                .inventory(inv)
                .inventoryNote(note)
                .build());
    }

    private void updateMetadata(InventoryNote note, InventoryReceiptRequest request, Branch destBranch) {
        note.setBranch(destBranch);
        note.setNote(request.getNote());
        note.setDeliverer(request.getDeliverer());
        note.setCreatedBy(getCurrentUser()); // Set Auditor
        note.setPaymentAmount(Objects.requireNonNullElse(request.getPaymentAmount(), BigDecimal.ZERO));

        if (request.getEntryDate() != null && !request.getEntryDate().isBlank()) {
            note.setEntryDate(LocalDate.parse(request.getEntryDate()).atStartOfDay());
        }

        if (request.getTags() != null && !request.getTags().isEmpty()) {
            note.setTags(String.join(",", request.getTags()));
        } else {
            note.setTags(null);
        }

        // CHỈ CHO PHÉP TƯƠNG TÁC VỚI NHÀ CUNG CẤP (SUPPLIER)
        Supplier supplier = supplierRepository.findByCode(request.getSupplierCode())
                .orElseThrow(() -> new NotFoundException("Nhà cung cấp không tồn tại"));
        note.setSupplier(supplier);
        note.setPartnerBranch(null);
    }

    // --- 3. XÓA PHIẾU (CHẶN NẾU ĐÃ NHẬP KHO) ---
    @Transactional
    public void deleteReceipt(Long id) {
        InventoryNote note = noteRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy phiếu ID: " + id));

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

            BigDecimal itemSubtotal = itemDTO.getImportPrice().multiply(BigDecimal.valueOf(itemDTO.getPlannedQuantity()));
            totalAmount = totalAmount.add(itemSubtotal);

            LocalDateTime expiry = (itemDTO.getExpiryDate() != null && !itemDTO.getExpiryDate().isBlank())
                    ? LocalDate.parse(itemDTO.getExpiryDate()).atStartOfDay() : null;

            String batch = (itemDTO.getLotNumber() == null || itemDTO.getLotNumber().isBlank()) ? "DEFAULT" : itemDTO.getLotNumber();

            InventoryNoteDetail detailEntity = InventoryNoteDetail.builder()
                    .inventoryNote(note)
                    .productVariant(variant)
                    .quantity(itemDTO.getPlannedQuantity())
                    .quantityRequested(itemDTO.getPlannedQuantity())
                    .quantityReal(itemDTO.getQuantityReal() != null ? itemDTO.getQuantityReal() : 0)
                    .quantityAccepted(itemDTO.getQuantityAccepted() != null ? itemDTO.getQuantityAccepted() : 0)
                    .quantityRejected(itemDTO.getQuantityRejected() != null ? itemDTO.getQuantityRejected() : 0)
                    .price(itemDTO.getImportPrice())
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
        note.setDebtAmount(totalAmount.subtract(note.getPaymentAmount()));
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
                    .newBalance(newQty)
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

    // --- 4 & 5. LẤY DANH SÁCH & CHI TIẾT ---
    @Transactional(readOnly = true)
    public List<InventoryReceiptResponse> getAllReceipts() {
        Long warehouseId = warehouseContext.resolveWarehouseId();
        List<InventoryNote> notes = (warehouseId == null) 
                ? noteRepository.findAllByTypeWithPartners(InventoryNoteType.IMPORT)
                : noteRepository.findAllByTypeAndBranchWithPartners(InventoryNoteType.IMPORT, warehouseId);

        return notes.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public InventoryReceiptResponse getReceiptById(Long id) {
        return noteRepository.findById(id)
                .map(this::mapToResponse)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy phiếu ID: " + id));
    }

    @Transactional(readOnly = true)
    public List<com.zone.agri.dto.response.inventory.InventorySearchResponse> getDefectiveItemsBySupplier(Long supplierId, Long explicitBranchId) {
        // Ưu tiên branchId truyền từ FE lên, nếu không có mới dùng context
        Long branchId = (explicitBranchId != null) ? explicitBranchId : warehouseContext.resolveWarehouseId();

        if (branchId == null) {
            return new ArrayList<>();
        }

        // Chỉ tìm hàng lỗi tại đúng chi nhánh đã xác định
        List<Inventory> defectiveInventories = inventoryRepository.findAllByBranchIdAndDefectiveQuantityGreaterThan(branchId, 0);
        // Lọc theo Nhà cung cấp
        return defectiveInventories.stream()
                .filter(inv -> {
                    // Tìm giao dịch nhập (IMPORT) của lô hàng này tại chi nhánh này
                    return transactionRepository.findFirstByInventoryAndTypeOrderByCreatedAtAsc(inv, com.zone.agri.entity.enums.TransactionType.IMPORT)
                            .map(t -> t.getInventoryNote() != null &&
                                     t.getInventoryNote().getSupplier() != null &&
                                     t.getInventoryNote().getSupplier().getId().equals(supplierId) &&
                                     t.getInventoryNote().getBranch().getId().equals(branchId)) // Đảm bảo đúng chi nhánh nhập
                            .orElse(false);
                })

                .map(inv -> {
                    ProductVariant variant = inv.getProductVariant();
                    // Tìm lại thông tin chi tiết từ phiếu nhập (Số lượng yêu cầu và Lý do lỗi gốc)
                    var originalDetail = transactionRepository.findFirstByInventoryAndTypeOrderByCreatedAtAsc(inv, com.zone.agri.entity.enums.TransactionType.IMPORT)
                            .flatMap(t -> t.getInventoryNote() != null ? t.getInventoryNote().getDetails().stream()
                                    .filter(d -> d.getProductVariant().getId().equals(variant.getId()) && Objects.equals(d.getBatchNumber(), inv.getBatchNumber()))
                                    .findFirst() : Optional.empty());

                    Integer originalPlannedQty = originalDetail.map(d -> d.getQuantity()).orElse(0);
                    String originalReason = originalDetail.map(d -> d.getNote()).orElse("");

                    return com.zone.agri.dto.response.inventory.InventorySearchResponse.builder()
                            .variantId(variant.getId())
                            .sku(variant.getSku())
                            .productName(variant.getProduct().getName())
                            .variantName(variant.getCustomSpecs())
                            .batchNumber(inv.getBatchNumber())
                            .quantity(inv.getQuantity()) 
                            .defectiveQuantity(inv.getDefectiveQuantity()) 
                            .plannedQuantity(originalPlannedQty) 
                            .reason(originalReason) // Gán lý do lỗi gốc
                            .importPrice(inv.getImportPrice())
                            .expiryDate(inv.getExpiryDate())
                            .imageUrl(variant.getImageUrl())
                            .unit("Cái")
                            .build();
                })
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<com.zone.agri.dto.response.inventory.InventorySearchResponse> searchInventoryForCheck(String keyword) {
        Long branchId = warehouseContext.resolveWarehouseId();
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
        BigDecimal debtAmount = totalAmount.subtract(paymentAmount);

        List<InventoryReceiptResponse.ItemResponse> itemResponses = new ArrayList<>();
        if (entity.getDetails() != null) {
            itemResponses = entity.getDetails().stream().map(d -> {
                ProductVariant variant = d.getProductVariant();
                return InventoryReceiptResponse.ItemResponse.builder()
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
                .supplierName(entity.getSupplier() != null ? entity.getSupplier().getName() : "")
                .supplierCode(entity.getSupplier() != null ? entity.getSupplier().getCode() : "")
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
}
