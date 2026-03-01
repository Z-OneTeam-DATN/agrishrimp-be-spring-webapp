package com.zone.agri.service;

import com.zone.agri.dto.inventory.InventoryReceiptRequest;
import com.zone.agri.dto.inventory.InventoryReceiptResponse;
import com.zone.agri.entity.*;
import com.zone.agri.entity.enums.InventoryNoteStatus;
import com.zone.agri.entity.enums.InventoryNoteType;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryNoteRepository noteRepository;
    private final InventoryRepository inventoryRepository;
    private final ProductVariantRepository variantRepository;
    private final BranchRepository branchRepository;
    private final SupplierRepository supplierRepository;
    private final com.zone.agri.common.WarehouseContext warehouseContext;

    // --- 1. TẠO PHIẾU MỚI ---
    @Transactional
    public InventoryReceiptResponse createReceipt(InventoryReceiptRequest request) {
        Branch destBranch = branchRepository.findByName(request.getBranchName())
                .orElseThrow(() -> new RuntimeException("Chi nhánh không tồn tại: " + request.getBranchName()));

        if ("SUPPLIER".equals(request.getImportType()) && !"WAREHOUSE".equalsIgnoreCase(destBranch.getBranchType())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Lỗi: Chỉ có KHO TỔNG mới được phép nhập hàng trực tiếp từ NCC.");
        }

        InventoryNote noteEntity = new InventoryNote();
        noteEntity.setCode(request.getReceiptCode());
        noteEntity.setType(InventoryNoteType.IMPORT);
        noteEntity.setStatus(request.getImportStatus().equals("IMPORTED") ? InventoryNoteStatus.COMPLETED : InventoryNoteStatus.PENDING);
        noteEntity.setBranch(destBranch);
        noteEntity.setNote(request.getNote());
        noteEntity.setDeliverer(request.getDeliverer());
        noteEntity.setPaymentAmount(request.getPaymentAmount() != null ? request.getPaymentAmount() : BigDecimal.ZERO);
        noteEntity.setCreatedAt(LocalDateTime.now());

        if (request.getEntryDate() != null && !request.getEntryDate().isEmpty()) {
            noteEntity.setEntryDate(LocalDate.parse(request.getEntryDate()).atStartOfDay());
        }

        if (request.getTags() != null && !request.getTags().isEmpty()) {
            noteEntity.setTags(String.join(",", request.getTags()));
        }

        if ("INTERNAL".equals(request.getImportType())) {
            Branch sourceBranch = branchRepository.findById(request.getSourceBranchId())
                    .orElseThrow(() -> new RuntimeException("Kho xuất đi không tồn tại"));
            noteEntity.setPartnerBranch(sourceBranch);
        } else {
            Supplier supplier = supplierRepository.findByCode(request.getSupplierCode())
                    .orElseThrow(() -> new RuntimeException("Nhà cung cấp không tồn tại"));
            noteEntity.setSupplier(supplier);
        }

        noteEntity.setDetails(new ArrayList<>());
        processItemsAndStock(noteEntity, request.getItems(), request.getImportType());

        return mapToResponse(noteRepository.save(noteEntity));
    }

    // --- 2. CẬP NHẬT PHIẾU ---
    @Transactional
    public InventoryReceiptResponse updateReceipt(Long id, InventoryReceiptRequest request) {
        InventoryNote existingNote = noteRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phiếu ID: " + id));

        if (existingNote.getStatus() == InventoryNoteStatus.COMPLETED) {
            throw new RuntimeException("Phiếu đã nhập kho thành công, không thể sửa đổi.");
        }

        Branch destBranch = branchRepository.findByName(request.getBranchName())
                .orElseThrow(() -> new RuntimeException("Chi nhánh không tồn tại: " + request.getBranchName()));

        existingNote.setBranch(destBranch);
        existingNote.setNote(request.getNote());
        existingNote.setDeliverer(request.getDeliverer());
        existingNote.setPaymentAmount(request.getPaymentAmount() != null ? request.getPaymentAmount() : BigDecimal.ZERO);

        if (request.getEntryDate() != null && !request.getEntryDate().isEmpty()) {
            existingNote.setEntryDate(LocalDate.parse(request.getEntryDate()).atStartOfDay());
        }

        existingNote.setTags(request.getTags() != null ? String.join(",", request.getTags()) : null);

        if ("INTERNAL".equals(request.getImportType())) {
            existingNote.setPartnerBranch(branchRepository.findById(request.getSourceBranchId()).orElse(null));
            existingNote.setSupplier(null);
        } else {
            existingNote.setSupplier(supplierRepository.findByCode(request.getSupplierCode()).orElse(null));
            existingNote.setPartnerBranch(null);
        }

        if ("IMPORTED".equals(request.getImportStatus())) {
            existingNote.setStatus(InventoryNoteStatus.COMPLETED);
        }

        existingNote.getDetails().clear();
        noteRepository.flush();

        processItemsAndStock(existingNote, request.getItems(), request.getImportType());
        return mapToResponse(noteRepository.save(existingNote));
    }

    // --- 3. XÓA PHIẾU (ROLLBACK TỒN KHO NẾU ĐÃ NHẬP) ---
    @Transactional
    public void deleteReceipt(Long id) {
        InventoryNote note = noteRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phiếu ID: " + id));

        if (note.getStatus() == InventoryNoteStatus.COMPLETED) {
            boolean isInternal = note.getPartnerBranch() != null;
            for (InventoryNoteDetail detail : note.getDetails()) {
                // Đảo ngược quá trình: Trừ kho đích
                updateStockBalanceExactBatch(note.getBranch(), detail.getProductVariant(), detail.getBatchNumber(), detail.getPrice(), detail.getExpiryDate(), -detail.getQuantityReal());

                if (isInternal) {
                    // Nếu là nội bộ, hoàn lại hàng vào kho xuất (Đưa vào lô mặc định TRANSFER_RETURN vì ta không lưu vết lô cũ lúc trừ FIFO)
                    updateStockBalanceExactBatch(note.getPartnerBranch(), detail.getProductVariant(), "TRANSFER_RETURN", detail.getPrice(), detail.getExpiryDate(), detail.getQuantityReal());
                }
            }
        }
        noteRepository.delete(note);
    }

    // --- LOGIC DÙNG CHUNG: XỬ LÝ ITEMS & CỘNG KHO ---
    private void processItemsAndStock(InventoryNote note, List<InventoryReceiptRequest.ItemRequest> items, String importType) {
        BigDecimal totalAmount = BigDecimal.ZERO;

        for (InventoryReceiptRequest.ItemRequest itemDTO : items) {
            ProductVariant variant = variantRepository.findBySku(itemDTO.getProductCode())
                    .orElseThrow(() -> new RuntimeException("SKU không tồn tại: " + itemDTO.getProductCode()));

            BigDecimal itemSubtotal = itemDTO.getImportPrice().multiply(BigDecimal.valueOf(itemDTO.getPlannedQuantity()));
            totalAmount = totalAmount.add(itemSubtotal);

            LocalDateTime expiry = (itemDTO.getExpiryDate() != null && !itemDTO.getExpiryDate().isEmpty())
                    ? LocalDate.parse(itemDTO.getExpiryDate()).atStartOfDay() : null;

            String batch = (itemDTO.getLotNumber() == null || itemDTO.getLotNumber().isEmpty()) ? "DEFAULT" : itemDTO.getLotNumber();

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
                updateStockBalanceExactBatch(note.getBranch(), variant, batch, detailEntity.getPrice(), expiry, itemDTO.getPlannedQuantity());

                // 2. Trừ kho xuất (NẾU NHẬP NỘI BỘ -> PHẢI TRỪ FIFO)
                if ("INTERNAL".equals(importType) && note.getPartnerBranch() != null) {
                    deductStockFifo(note.getPartnerBranch(), variant, itemDTO.getPlannedQuantity());
                }
            }
        }
        note.setTotalAmount(totalAmount);
        note.setDebtAmount(note.getPartnerBranch() != null ? BigDecimal.ZERO : totalAmount.subtract(note.getPaymentAmount()));
    }

    // Hàm cập nhật đích danh một Lô (Dùng cho nhập kho)
    private void updateStockBalanceExactBatch(Branch branch, ProductVariant variant, String batchNumber, BigDecimal importPrice, LocalDateTime expiryDate, Integer quantityChange) {
        Inventory inv = inventoryRepository.findExactBatch(branch, variant, batchNumber, importPrice)
                .orElseGet(() -> Inventory.builder()
                        .branch(branch)
                        .productVariant(variant)
                        .batchNumber(batchNumber)
                        .importPrice(importPrice)
                        .expiryDate(expiryDate)
                        .quantity(0)
                        .build());

        int newQuantity = inv.getQuantity() + quantityChange;
        if (newQuantity < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Kho " + branch.getName() + " không đủ hàng ở lô " + batchNumber);
        }
        inv.setQuantity(newQuantity);
        inv.setLastReceiptDate(LocalDateTime.now());
        inventoryRepository.save(inv);
    }

    // Hàm trừ kho theo nguyên tắc FIFO (Dùng khi xuất kho/chuyển nội bộ)
    private void deductStockFifo(Branch branch, ProductVariant variant, int quantityToDeduct) {
        // 1. Kiểm tra tổng tồn kho
        Integer totalStock = inventoryRepository.sumQuantityByBranchAndVariant(branch.getId(), variant.getId());
        if (totalStock == null || totalStock < quantityToDeduct) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Kho " + branch.getName() + " chỉ còn " + (totalStock == null ? 0 : totalStock) + " sản phẩm " + variant.getSku() + ", không đủ để điều chuyển.");
        }

        // 2. Lấy danh sách lô theo thứ tự cũ xuất trước
        List<Inventory> batches = inventoryRepository.findAvailableBatchesForVariant(branch.getId(), variant.getId());
        int remaining = quantityToDeduct;

        for (Inventory batch : batches) {
            if (remaining <= 0) break;

            int deduct = Math.min(batch.getQuantity(), remaining);
            batch.setQuantity(batch.getQuantity() - deduct);
            inventoryRepository.save(batch);

            remaining -= deduct;
        }

        if (remaining > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Lỗi đồng bộ: Kho " + branch.getName() + " bị hụt hàng trong lúc xử lý.");
        }
    }

    // --- 4 & 5. LẤY DANH SÁCH & CHI TIẾT ---
    @Transactional(readOnly = true)
    public List<InventoryReceiptResponse> getAllReceipts() {
        Long warehouseId = warehouseContext.resolveWarehouseId();
        List<InventoryNote> notes = (warehouseId == null) ? noteRepository.findAll() : noteRepository.findAllByBranchId(warehouseId);

        return notes.stream()
                .filter(note -> note.getType() == InventoryNoteType.IMPORT)
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public InventoryReceiptResponse getReceiptById(Long id) {
        return mapToResponse(noteRepository.findById(id).orElseThrow());
    }

    private InventoryReceiptResponse mapToResponse(InventoryNote entity) {
        if (entity == null) return null;
        boolean isInternal = entity.getPartnerBranch() != null;
        List<String> tagList = new ArrayList<>();
        if (entity.getTags() != null && !entity.getTags().isEmpty()) tagList = Arrays.asList(entity.getTags().split(","));

        // Lấy trước ID của kho xuất để dùng bên trong vòng lặp map items
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
                .totalAmount(entity.getTotalAmount() != null ? entity.getTotalAmount() : BigDecimal.ZERO)
                .paymentAmount(entity.getPaymentAmount() != null ? entity.getPaymentAmount() : BigDecimal.ZERO)
                .debtAmount(entity.getDebtAmount() != null ? entity.getDebtAmount() : BigDecimal.ZERO)
                .deliverer(entity.getDeliverer())
                .note(entity.getNote())
                .tags(tagList)
                .createdAt(entity.getCreatedAt())
                .entryDate(entity.getEntryDate() != null ? entity.getEntryDate().format(DateTimeFormatter.ISO_LOCAL_DATE) : "")
                .items(entity.getDetails() == null ? new ArrayList<>() : entity.getDetails().stream().map(d -> {

                    // 1. Tính tồn kho xuất (Chỉ áp dụng nếu là phiếu INTERNAL)
                    Integer currentStock = 0;
                    if (isInternal && partnerBranchId != null && d.getProductVariant() != null) {
                        // Gọi repository để tính tổng tồn kho hiện tại của chi nhánh xuất
                        currentStock = inventoryRepository.sumQuantityByBranchAndVariant(partnerBranchId, d.getProductVariant().getId());
                        if (currentStock == null) {
                            currentStock = 0;
                        }
                    }

                    // 2. Map vào ItemResponse
                    return InventoryReceiptResponse.ItemResponse.builder()
                            .productCode(d.getProductVariant() != null ? d.getProductVariant().getSku() : "")
                            .productName(d.getProductVariant() != null && d.getProductVariant().getProduct() != null ? d.getProductVariant().getProduct().getName() : "")
                            .quantity(d.getQuantity() != null ? d.getQuantity() : 0)

                            // 👇 GÁN maxQuantity VÀO ĐÂY
                            .maxQuantity(currentStock)

                            .price(d.getPrice() != null ? d.getPrice() : BigDecimal.ZERO)
                            .newSellingPrice(d.getNewSellingPrice())
                            .lotNumber(d.getBatchNumber())
                            .expiryDate(d.getExpiryDate() != null ? d.getExpiryDate().format(DateTimeFormatter.ISO_LOCAL_DATE) : "")
                            .imageUrl(d.getProductVariant() != null ? d.getProductVariant().getImageUrl() : null)
                            .build();
                }).collect(Collectors.toList()))
                .build();
    }
}