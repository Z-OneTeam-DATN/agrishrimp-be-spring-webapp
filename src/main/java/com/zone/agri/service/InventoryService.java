package com.zone.agri.service;

import com.zone.agri.dto.inventory.InventoryReceiptRequest;
import com.zone.agri.dto.inventory.InventoryReceiptResponse;
import com.zone.agri.entity.*;
import com.zone.agri.entity.enums.InventoryNoteStatus;
import com.zone.agri.entity.enums.InventoryNoteType;
import com.zone.agri.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        // Lấy kho đích (Kho nhập vào)
        Branch destBranch = branchRepository.findByName(request.getBranchName())
                .orElseThrow(() -> new RuntimeException("Chi nhánh không tồn tại: " + request.getBranchName()));

        InventoryNote noteEntity = new InventoryNote();
        noteEntity.setCode(request.getReceiptCode());
        noteEntity.setType(InventoryNoteType.IMPORT);
        noteEntity.setStatus(request.getImportStatus().equals("IMPORTED") ? InventoryNoteStatus.COMPLETED : InventoryNoteStatus.PENDING);
        noteEntity.setBranch(destBranch); // Kho nhận
        noteEntity.setNote(request.getNote());
        noteEntity.setDeliverer(request.getDeliverer());
        noteEntity.setPaymentAmount(request.getPaymentAmount() != null ? request.getPaymentAmount() : BigDecimal.ZERO);

        // Thời gian tạo và thời gian giao hàng
        noteEntity.setCreatedAt(LocalDateTime.now());
        if (request.getEntryDate() != null && !request.getEntryDate().isEmpty()) {
            noteEntity.setEntryDate(LocalDate.parse(request.getEntryDate()).atStartOfDay());
        }

        // Xử lý Tags (List -> String "tag1,tag2")
        if (request.getTags() != null && !request.getTags().isEmpty()) {
            noteEntity.setTags(String.join(",", request.getTags()));
        }

        // RẼ NHÁNH NGUỒN NHẬP
        if ("INTERNAL".equals(request.getImportType())) {
            Branch sourceBranch = branchRepository.findById(request.getSourceBranchId())
                    .orElseThrow(() -> new RuntimeException("Kho xuất đi không tồn tại"));
            noteEntity.setPartnerBranch(sourceBranch); // Ghi nhận kho chuyển đến
        } else {
            Supplier supplier = supplierRepository.findByCode(request.getSupplierCode())
                    .orElseThrow(() -> new RuntimeException("Nhà cung cấp không tồn tại"));
            noteEntity.setSupplier(supplier);
        }

        // Khởi tạo List details
        noteEntity.setDetails(new ArrayList<>());

        // Xử lý tính toán tiền và Cộng/Trừ kho
        processItemsAndStock(noteEntity, request.getItems(), request.getImportType());

        InventoryNote saved = noteRepository.save(noteEntity);
        return mapToResponse(saved);
    }

    // --- 2. CẬP NHẬT PHIẾU (CHỈ KHI PENDING) ---
    @Transactional
    public InventoryReceiptResponse updateReceipt(Long id, InventoryReceiptRequest request) {
        InventoryNote existingNote = noteRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phiếu nhập kho ID: " + id));

        if (existingNote.getStatus() == InventoryNoteStatus.COMPLETED) {
            throw new RuntimeException("Phiếu đã nhập kho thành công, không thể sửa đổi.");
        }

        // Cập nhật thông tin Header
        existingNote.setNote(request.getNote());
        existingNote.setDeliverer(request.getDeliverer());
        existingNote.setPaymentAmount(request.getPaymentAmount() != null ? request.getPaymentAmount() : BigDecimal.ZERO);

        if (request.getEntryDate() != null && !request.getEntryDate().isEmpty()) {
            existingNote.setEntryDate(LocalDate.parse(request.getEntryDate()).atStartOfDay());
        }

        if (request.getTags() != null && !request.getTags().isEmpty()) {
            existingNote.setTags(String.join(",", request.getTags()));
        } else {
            existingNote.setTags(null);
        }

        // Nếu người dùng bấm lưu và chuyển thành "Đã nhập kho"
        if ("IMPORTED".equals(request.getImportStatus())) {
            existingNote.setStatus(InventoryNoteStatus.COMPLETED);
        }

        // Xóa chi tiết cũ và tạo chi tiết mới
        existingNote.getDetails().clear();
        processItemsAndStock(existingNote, request.getItems(), request.getImportType());

        return mapToResponse(noteRepository.save(existingNote));
    }

    // --- 3. XÓA PHIẾU (ROLLBACK TỒN KHO NẾU ĐÃ NHẬP) ---
    @Transactional
    public void deleteReceipt(Long id) {
        InventoryNote note = noteRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phiếu nhập kho ID: " + id));

        if (note.getStatus() == InventoryNoteStatus.COMPLETED) {
            boolean isInternal = note.getPartnerBranch() != null;

            for (InventoryNoteDetail detail : note.getDetails()) {
                // Đảo ngược: Trừ kho nhận
                updateStockBalance(note.getBranch(), detail.getProductVariant(), -detail.getQuantity());

                // Đảo ngược: Cộng lại kho xuất (nếu nội bộ)
                if (isInternal) {
                    updateStockBalance(note.getPartnerBranch(), detail.getProductVariant(), detail.getQuantity());
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

            InventoryNoteDetail detailEntity = InventoryNoteDetail.builder()
                    .inventoryNote(note)
                    .productVariant(variant)
                    .quantity(itemDTO.getPlannedQuantity()) // Slg cần nhập
                    .quantityReal(itemDTO.getPlannedQuantity()) // Slg thực nhập (tạm thời bằng nhau)
                    .price(itemDTO.getImportPrice())
                    .batchNumber(itemDTO.getLotNumber())
                    .expiryDate(expiry)
                    .newSellingPrice(itemDTO.getNewSellingPrice())
                    .build();

            note.getDetails().add(detailEntity);

            // CHỈ CẬP NHẬT KHO KHI TRẠNG THÁI LÀ COMPLETED
            if (note.getStatus() == InventoryNoteStatus.COMPLETED) {
                // 1. CỘNG kho nhận
                updateStockBalance(note.getBranch(), variant, itemDTO.getPlannedQuantity());

                // 2. TRỪ kho xuất (Nếu là nguồn Nội Bộ)
                if ("INTERNAL".equals(importType) && note.getPartnerBranch() != null) {
                    updateStockBalance(note.getPartnerBranch(), variant, -itemDTO.getPlannedQuantity());
                }
            }
        }

        note.setTotalAmount(totalAmount);
        note.setDebtAmount(totalAmount.subtract(note.getPaymentAmount()));
    }

    // Hàm cập nhật tồn kho (Có thể xử lý cộng số dương hoặc trừ số âm)
    private void updateStockBalance(Branch branch, ProductVariant variant, Integer quantityChange) {
        Inventory inv = inventoryRepository.findByBranchAndProductVariant(branch, variant)
                .orElseGet(() -> Inventory.builder()
                        .branch(branch)
                        .productVariant(variant)
                        .quantity(0)
                        .minStock(0)
                        .build());

        int newQuantity = inv.getQuantity() + quantityChange;

        // Bạn có thể mở comment dòng dưới nếu muốn ném lỗi khi kho xuất không đủ hàng
        // if (newQuantity < 0) {
        //    throw new RuntimeException("Kho " + branch.getName() + " không đủ số lượng cho sản phẩm " + variant.getSku());
        // }

        inv.setQuantity(newQuantity);
        inv.setLastReceiptDate(LocalDateTime.now());
        inventoryRepository.save(inv);
    }

    // --- 4 & 5. LẤY DANH SÁCH & CHI TIẾT ---
    @Transactional(readOnly = true)
    public List<InventoryReceiptResponse> getAllReceipts() {
        Long warehouseId = warehouseContext.resolveWarehouseId();
        List<InventoryNote> notes = (warehouseId == null)
                ? noteRepository.findAll()
                : noteRepository.findAllByBranchId(warehouseId);

        return notes.stream()
                .filter(note -> note.getType() == InventoryNoteType.IMPORT)
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public InventoryReceiptResponse getReceiptById(Long id) {
        InventoryNote note = noteRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phiếu nhập kho ID: " + id));
        warehouseContext.assertAccess(note.getBranch().getId());
        return mapToResponse(note);
    }

    // MAPPER TRẢ VỀ FRONTEND
    private InventoryReceiptResponse mapToResponse(InventoryNote entity) {
        if (entity == null) return null;

        boolean isInternal = entity.getPartnerBranch() != null;

        // Xử lý parse Tags từ String về List
        List<String> tagList = new ArrayList<>();
        if (entity.getTags() != null && !entity.getTags().isEmpty()) {
            tagList = Arrays.asList(entity.getTags().split(","));
        }

        return InventoryReceiptResponse.builder()
                .id(entity.getId())
                .code(entity.getCode())
                // Xác định importType để FE binding lại Select Option
                .importType(isInternal ? "INTERNAL" : "SUPPLIER")
                .sourceBranchId(isInternal ? entity.getPartnerBranch().getId() : null)

                .status(entity.getStatus() != null ? entity.getStatus().name() : "PENDING")
                .supplierName(entity.getSupplier() != null ? entity.getSupplier().getName() : "")
                .supplierCode(entity.getSupplier() != null ? entity.getSupplier().getCode() : "")
                .branchName(entity.getBranch() != null ? entity.getBranch().getName() : "N/A")
                .totalAmount(entity.getTotalAmount() != null ? entity.getTotalAmount() : BigDecimal.ZERO)
                .paymentAmount(entity.getPaymentAmount() != null ? entity.getPaymentAmount() : BigDecimal.ZERO)
                .debtAmount(entity.getDebtAmount() != null ? entity.getDebtAmount() : BigDecimal.ZERO)
                .deliverer(entity.getDeliverer())
                .note(entity.getNote())
                .tags(tagList) // Trả List tags
                .createdAt(entity.getCreatedAt())
                .entryDate(entity.getEntryDate() != null ? entity.getEntryDate().format(DateTimeFormatter.ISO_LOCAL_DATE) : "")
                .items(entity.getDetails() == null ? new ArrayList<>() : entity.getDetails().stream().map(d -> {
                    String sku = (d.getProductVariant() != null) ? d.getProductVariant().getSku() : "ERR-SKU";
                    String pName = (d.getProductVariant() != null && d.getProductVariant().getProduct() != null)
                            ? d.getProductVariant().getProduct().getName() : "Sản phẩm không tồn tại";

                    return InventoryReceiptResponse.ItemResponse.builder()
                            .productCode(sku)
                            .productName(pName)
                            .unit("")
                            .quantity(d.getQuantity() != null ? d.getQuantity() : 0)
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