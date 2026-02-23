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

    // --- 1. TẠO PHIẾU MỚI ---
    @Transactional
    public InventoryReceiptResponse createReceipt(InventoryReceiptRequest requestDTO) {
        Branch branch = branchRepository.findByName(requestDTO.getBranchName())
                .orElseThrow(() -> new RuntimeException("Chi nhánh không tồn tại: " + requestDTO.getBranchName()));
        Supplier supplier = supplierRepository.findByCode(requestDTO.getSupplierCode())
                .orElseThrow(() -> new RuntimeException("Nhà cung cấp không tồn tại: " + requestDTO.getSupplierCode()));

        InventoryNote noteEntity = InventoryNote.builder()
                .code(requestDTO.getReceiptCode())
                .type(InventoryNoteType.IMPORT)
                .status(requestDTO.getImportStatus().equals("IMPORTED") ? InventoryNoteStatus.COMPLETED : InventoryNoteStatus.PENDING)
                .branch(branch)
                .supplier(supplier)
                .note(requestDTO.getNote())
                .deliverer(requestDTO.getDeliverer())
                .paymentAmount(requestDTO.getPaymentAmount() != null ? requestDTO.getPaymentAmount() : BigDecimal.ZERO)
                .createdAt(LocalDateTime.now())
                .details(new ArrayList<>())
                .build();

        processItemsAndStock(noteEntity, requestDTO.getItems());

        InventoryNote saved = noteRepository.save(noteEntity);
        return mapToResponse(saved);
    }

    // --- 2. CẬP NHẬT PHIẾU (CHỈ KHI PENDING) ---
    @Transactional
    public InventoryReceiptResponse updateReceipt(Long id, InventoryReceiptRequest requestDTO) {
        InventoryNote existingNote = noteRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phiếu nhập kho ID: " + id));

        // Nếu phiếu đã hoàn tất (COMPLETED), không cho phép sửa thông tin sản phẩm/số lượng
        if (existingNote.getStatus() == InventoryNoteStatus.COMPLETED) {
            throw new RuntimeException("Phiếu đã nhập kho thành công, không thể sửa đổi.");
        }

        // Cập nhật thông tin Header
        existingNote.setNote(requestDTO.getNote());
        existingNote.setDeliverer(requestDTO.getDeliverer());
        existingNote.setPaymentAmount(requestDTO.getPaymentAmount());

        // Cập nhật trạng thái nếu UI yêu cầu nhập kho ngay lúc sửa
        if (requestDTO.getImportStatus().equals("IMPORTED")) {
            existingNote.setStatus(InventoryNoteStatus.COMPLETED);
        }

        // Xóa chi tiết cũ và cập nhật chi tiết mới
        existingNote.getDetails().clear();
        processItemsAndStock(existingNote, requestDTO.getItems());

        return mapToResponse(noteRepository.save(existingNote));
    }

    // --- 3. XÓA PHIẾU (HỖ TRỢ HOÀN KHO) ---
    @Transactional
    public void deleteReceipt(Long id) {
        InventoryNote note = noteRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phiếu nhập kho ID: " + id));

        // Nếu phiếu đã nhập kho (COMPLETED), phải trừ tồn kho ngược lại trước khi xóa
        if (note.getStatus() == InventoryNoteStatus.COMPLETED) {
            for (InventoryNoteDetail detail : note.getDetails()) {
                updateStockBalance(note.getBranch(), detail.getProductVariant(), -detail.getQuantity());
            }
        }

        noteRepository.delete(note);
    }

    // --- LOGIC DÙNG CHUNG: XỬ LÝ ITEMS & CỘNG KHO ---
    private void processItemsAndStock(InventoryNote note, List<InventoryReceiptRequest.ItemRequest> items) {
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
                    .quantity(itemDTO.getPlannedQuantity())
                    .price(itemDTO.getImportPrice())
                    .batchNumber(itemDTO.getLotNumber())
                    .expiryDate(expiry)
                    .newSellingPrice(itemDTO.getNewSellingPrice())
                    .build();

            note.getDetails().add(detailEntity);

            // Chỉ cập nhật bảng inventories nếu trạng thái phiếu là COMPLETED
            if (note.getStatus() == InventoryNoteStatus.COMPLETED) {
                updateStockBalance(note.getBranch(), variant, itemDTO.getPlannedQuantity());
            }
        }
        note.setTotalAmount(totalAmount);
        note.setDebtAmount(totalAmount.subtract(note.getPaymentAmount()));
    }

    private void updateStockBalance(Branch branch, ProductVariant variant, Integer addedQty) {
        Inventory inv = inventoryRepository.findByBranchAndProductVariant(branch, variant)
                .orElse(Inventory.builder()
                        .branch(branch)
                        .productVariant(variant)
                        .quantity(0)
                        .minStock(0)
                        .build());

        inv.setQuantity(inv.getQuantity() + addedQty);
        inv.setLastReceiptDate(LocalDateTime.now());
        inventoryRepository.save(inv);
    }
    // --- 4. LẤY DANH SÁCH PHIẾU NHẬP ---
    @Transactional(readOnly = true)
    public List<InventoryReceiptResponse> getAllReceipts() {
        return noteRepository.findAll().stream()
                .filter(note -> note.getType() == InventoryNoteType.IMPORT) // Chỉ lấy phiếu nhập
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // --- 5. LẤY CHI TIẾT PHIẾU THEO ID ---
    @Transactional(readOnly = true)
    public InventoryReceiptResponse getReceiptById(Long id) {
        InventoryNote note = noteRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phiếu nhập kho ID: " + id));
        return mapToResponse(note);
    }

    private InventoryReceiptResponse mapToResponse(InventoryNote entity) {
        return InventoryReceiptResponse.builder()
                .id(entity.getId())
                .code(entity.getCode())
                .status(entity.getStatus().name())
                .supplierName(entity.getSupplier().getName())
                .branchName(entity.getBranch().getName())
                .totalAmount(entity.getTotalAmount())
                .paymentAmount(entity.getPaymentAmount())
                .debtAmount(entity.getDebtAmount())
                .deliverer(entity.getDeliverer())
                .createdAt(entity.getCreatedAt())
                .items(entity.getDetails().stream().map(d -> InventoryReceiptResponse.ItemResponse.builder()
                        .productCode(d.getProductVariant().getSku())
                        .productName(d.getProductVariant().getProduct().getName())
                        .unit(d.getProductVariant().getUnit())
                        .quantity(d.getQuantity())
                        .price(d.getPrice())
                        .newSellingPrice(d.getNewSellingPrice())
                        .lotNumber(d.getBatchNumber())
                        .expiryDate(d.getExpiryDate() != null ? d.getExpiryDate().format(DateTimeFormatter.ISO_LOCAL_DATE) : "")
                        .imageUrl(d.getProductVariant().getImageUrl())
                        .build()).collect(Collectors.toList()))
                .build();
    }
}