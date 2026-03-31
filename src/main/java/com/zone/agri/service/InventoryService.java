package com.zone.agri.service;

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
    private final InventoryRepository inventoryRepository;
    private final ProductVariantRepository variantRepository;
    private final BranchRepository branchRepository;
    private final SupplierRepository supplierRepository;
    private final InventoryTransactionRepository transactionRepository;
    private final com.zone.agri.common.WarehouseContext warehouseContext;

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
        noteEntity = noteRepository.save(noteEntity); // Lưu trước để lấy ID, tránh lỗi TransientObjectException khi lưu Transaction
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

        // Clear and re-process items
        existingNote.getDetails().clear();
        noteRepository.flush();

        processItemsAndStock(existingNote, request.getItems(), request.getImportType());
        return mapToResponse(noteRepository.save(existingNote));
    }

    private void updateMetadata(InventoryNote note, InventoryReceiptRequest request, Branch destBranch) {
        note.setBranch(destBranch);
        note.setNote(request.getNote());
        note.setDeliverer(request.getDeliverer());
        note.setPaymentAmount(Objects.requireNonNullElse(request.getPaymentAmount(), BigDecimal.ZERO));
        note.setStatus("IMPORTED".equals(request.getImportStatus()) ? InventoryNoteStatus.COMPLETED : InventoryNoteStatus.PENDING);

        if (request.getEntryDate() != null && !request.getEntryDate().isBlank()) {
            note.setEntryDate(LocalDate.parse(request.getEntryDate()).atStartOfDay());
        }

        if (request.getTags() != null && !request.getTags().isEmpty()) {
            note.setTags(String.join(",", request.getTags()));
        } else {
            note.setTags(null);
        }

        if ("INTERNAL".equals(request.getImportType())) {
            Branch sourceBranch = branchRepository.findById(request.getSourceBranchId())
                    .orElseThrow(() -> new NotFoundException("Kho xuất đi không tồn tại"));
            note.setPartnerBranch(sourceBranch);
            note.setSupplier(null);
        } else {
            Supplier supplier = supplierRepository.findByCode(request.getSupplierCode())
                    .orElseThrow(() -> new NotFoundException("Nhà cung cấp không tồn tại"));
            note.setSupplier(supplier);
            note.setPartnerBranch(null);
        }
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
                    .quantityReal(itemDTO.getPlannedQuantity())
                    .price(itemDTO.getImportPrice())
                    .batchNumber(batch)
                    .expiryDate(expiry)
                    .newSellingPrice(itemDTO.getNewSellingPrice())
                    .build();

            note.getDetails().add(detailEntity);

            if (note.getStatus() == InventoryNoteStatus.COMPLETED) {
                // 1. Nhập vào ĐÚNG lô tại kho nhận
                updateStockBalanceExactBatch(note, note.getBranch(), variant, batch, detailEntity.getPrice(), expiry, itemDTO.getPlannedQuantity(), TransactionType.IMPORT);

                // 2. Trừ kho xuất (NẾU NHẬP NỘI BỘ -> PHẢI TRỪ FIFO)
                if ("INTERNAL".equals(importType) && note.getPartnerBranch() != null) {
                    deductStockFifo(note, note.getPartnerBranch(), variant, itemDTO.getPlannedQuantity());
                }
            }
        }
        note.setTotalAmount(totalAmount);
        note.setDebtAmount(note.getPartnerBranch() != null ? BigDecimal.ZERO : totalAmount.subtract(note.getPaymentAmount()));
    }

    // Hàm cập nhật đích danh một Lô (Dùng cho nhập kho)
    private void updateStockBalanceExactBatch(InventoryNote note, Branch branch, ProductVariant variant, String batchNumber, BigDecimal importPrice, LocalDateTime expiryDate, Integer quantityChange, TransactionType type) {
        // Dùng findExactBatchWithLock để tránh Race Condition
        Inventory inv = inventoryRepository.findExactBatchWithLock(branch, variant, batchNumber, importPrice)
                .orElseGet(() -> {
                    Inventory newInv = Inventory.builder()
                        .branch(branch)
                        .productVariant(variant)
                        .batchNumber(batchNumber)
                        .importPrice(importPrice)
                        .expiryDate(expiryDate)
                        .quantity(0)
                        .build();
                    return inventoryRepository.save(newInv); // Lưu trước để có ID cho Transaction
                });

        int oldQuantity = Objects.requireNonNullElse(inv.getQuantity(), 0);
        int newQuantity = oldQuantity + quantityChange;
        if (newQuantity < 0) {
            throw new BadRequestException("Kho " + branch.getName() + " không đủ hàng ở lô " + batchNumber);
        }
        inv.setQuantity(newQuantity);
        inv.setLastReceiptDate(LocalDateTime.now());
        inv = inventoryRepository.save(inv);

        // Ghi log biến động kho
        InventoryTransaction tx = InventoryTransaction.builder()
                .type(type)
                .quantityChange(quantityChange)
                .newBalance(newQuantity)
                .referenceCode(note != null ? note.getCode() : null)
                .reason(note != null ? "Phiếu kho: " + note.getCode() : "Cập nhật hệ thống")
                .createdAt(LocalDateTime.now())
                .inventory(inv)
                .inventoryNote(note)
                .build();
        transactionRepository.save(tx);
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
    public List<com.zone.agri.dto.response.inventory.InventorySearchResponse> searchInventoryForCheck(String keyword) {
        Long branchId = warehouseContext.resolveWarehouseId();
        return inventoryRepository.searchInventoryForCheck(keyword, branchId);
    }

    private InventoryReceiptResponse mapToResponse(InventoryNote entity) {
        if (entity == null) return null;
        boolean isInternal = entity.getPartnerBranch() != null;
        List<String> tagList = (entity.getTags() != null && !entity.getTags().isBlank()) 
                ? Arrays.asList(entity.getTags().split(",")) 
                : new ArrayList<>();

        Long partnerBranchId = isInternal ? entity.getPartnerBranch().getId() : null;

        return InventoryReceiptResponse.builder()
                .id(entity.getId())
                .code(entity.getCode())
                .importType(isInternal ? "INTERNAL" : "SUPPLIER")
                .sourceBranchId(partnerBranchId)
                .status(entity.getStatus() != null ? entity.getStatus().name() : "PENDING")
                .supplierName(entity.getSupplier() != null ? entity.getSupplier().getName() : "")
                .supplierCode(entity.getSupplier() != null ? entity.getSupplier().getCode() : "")
                .branchName(entity.getBranch() != null ? entity.getBranch().getName() : "N/A")
                .totalAmount(Objects.requireNonNullElse(entity.getTotalAmount(), BigDecimal.ZERO))
                .paymentAmount(Objects.requireNonNullElse(entity.getPaymentAmount(), BigDecimal.ZERO))
                .debtAmount(Objects.requireNonNullElse(entity.getDebtAmount(), BigDecimal.ZERO))
                .deliverer(entity.getDeliverer())
                .note(entity.getNote())
                .tags(tagList)
                .createdAt(entity.getCreatedAt())
                .entryDate(entity.getEntryDate() != null ? entity.getEntryDate().format(DateTimeFormatter.ISO_LOCAL_DATE) : "")
                .items(entity.getDetails() == null ? new ArrayList<>() : entity.getDetails().stream().map(d -> {
                    ProductVariant variant = d.getProductVariant();
                    Integer currentStock = 0;
                    if (isInternal && partnerBranchId != null && variant != null) {
                        currentStock = Objects.requireNonNullElse(inventoryRepository.sumQuantityByBranchAndVariant(partnerBranchId, variant.getId()), 0);
                    }

                    return InventoryReceiptResponse.ItemResponse.builder()
                            .productCode(variant != null ? variant.getSku() : "")
                            .productName(variant != null && variant.getProduct() != null ? variant.getProduct().getName() : "")
                            .quantity(Objects.requireNonNullElse(d.getQuantity(), 0))
                            .maxQuantity(currentStock)
                            .price(Objects.requireNonNullElse(d.getPrice(), BigDecimal.ZERO))
                            .newSellingPrice(d.getNewSellingPrice())
                            .lotNumber(d.getBatchNumber())
                            .expiryDate(d.getExpiryDate() != null ? d.getExpiryDate().format(DateTimeFormatter.ISO_LOCAL_DATE) : "")
                            .imageUrl(variant != null ? variant.getImageUrl() : null)
                            .build();
                }).collect(Collectors.toList()))
                .build();
    }
}