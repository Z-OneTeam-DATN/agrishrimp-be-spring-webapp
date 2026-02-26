package com.zone.agri.service;

import com.zone.agri.dto.request.inventory.ExportNoteRequest;
import com.zone.agri.dto.response.InventoryNoteResponse;
import com.zone.agri.dto.response.InventoryNoteDetailResponse;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InventoryNoteService {

    private final InventoryNoteRepository inventoryNoteRepository;
    private final InventoryNoteDetailRepository inventoryNoteDetailRepository;
    private final BranchRepository branchRepository;
    private final ProductVariantRepository productVariantRepository;
    private final SupplierRepository supplierRepository;
    private final UserRepository userRepository;
    private final InventoryRepository inventoryRepository;
    private final com.zone.agri.common.WarehouseContext warehouseContext;

    // ==========================================
    // 1. TẠO LỆNH XUẤT (TRẠNG THÁI PENDING - CHƯA TRỪ KHO)
    // ==========================================
    @Transactional
    public InventoryNoteResponse createExportCommand(ExportNoteRequest request) {
        InventoryNote note = new InventoryNote();
        note.setCode(request.getCode() != null ? request.getCode() : "LXK-" + System.currentTimeMillis());
        note.setType(InventoryNoteType.EXPORT);
        note.setStatus(InventoryNoteStatus.PENDING); // Đang chờ xuất

        // Lưu thông tin địa chỉ, người nhận vào Reason hoặc Deliverer (Tùy entity của bạn)
        note.setDeliverer(request.getSpecificReceiver());
        String fullReason = String.format("Loại: %s | Ref: %s | Đ/c: %s | Lydo: %s",
                request.getExportType(), request.getReferenceCode(), request.getShippingAddress(), request.getNote());
        note.setReason(fullReason);
        note.setNote(request.getNote());

        if (request.getExpectedDate() != null) {
            note.setEntryDate(request.getExpectedDate().atStartOfDay());
        }

        Branch sourceBranch = branchRepository.findById(request.getBranchId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy kho xuất"));

        if ("RETURN".equals(request.getExportType()) && !"WAREHOUSE".equalsIgnoreCase(sourceBranch.getBranchType())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Lỗi: Chỉ có KHO TỔNG mới được phép xuất trả hàng cho Nhà cung cấp.");
        }

        note.setBranch(sourceBranch);

        if (request.getCreatedById() != null) {
            userRepository.findById(request.getCreatedById()).ifPresent(note::setCreatedBy);
        }

        if ("RETURN".equals(request.getExportType()) && request.getSupplierId() != null) {
            note.setSupplier(supplierRepository.findById(request.getSupplierId()).orElse(null));
        } else if ("INTERNAL".equals(request.getExportType()) && request.getTargetBranchId() != null) {
            note.setPartnerBranch(branchRepository.findById(request.getTargetBranchId()).orElse(null));
        }

        note.setCreatedAt(LocalDateTime.now());
        InventoryNote savedNote = inventoryNoteRepository.save(note);
        final BigDecimal[] totalAmount = {BigDecimal.ZERO};

        List<InventoryNoteDetail> details = request.getDetails().stream().map(reqDetail -> {
            InventoryNoteDetail detail = new InventoryNoteDetail();
            detail.setInventoryNote(savedNote);
            ProductVariant variant = productVariantRepository.findById(reqDetail.getProductVariantId())
                    .orElseThrow(() -> new RuntimeException("Sản phẩm không tồn tại ID: " + reqDetail.getProductVariantId()));
            detail.setProductVariant(variant);

            // KIỂM TRA TỒN KHO CỦA KHO XUẤT TRƯỚC KHI TẠO LỆNH
            Inventory inv = inventoryRepository.findByBranchAndProductVariant(sourceBranch, variant)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sản phẩm " + variant.getSku() + " không có trong kho " + sourceBranch.getName()));
            if (inv.getQuantity() < reqDetail.getRequestedQuantity()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Kho " + sourceBranch.getName() + " chỉ còn " + inv.getQuantity() + " sản phẩm " + variant.getSku());
            }

            detail.setQuantityRequested(reqDetail.getRequestedQuantity());
            detail.setQuantityReal(reqDetail.getRequestedQuantity());
            detail.setQuantity(reqDetail.getRequestedQuantity());
            detail.setPrice(reqDetail.getPrice());

            if (reqDetail.getPrice() != null && reqDetail.getRequestedQuantity() != null) {
                totalAmount[0] = totalAmount[0].add(reqDetail.getPrice().multiply(new BigDecimal(reqDetail.getRequestedQuantity())));
            }
            return detail;
        }).collect(Collectors.toList());

        inventoryNoteDetailRepository.saveAll(details);
        savedNote.setTotalAmount(totalAmount[0]);
        savedNote.setDebtAmount(BigDecimal.ZERO); // Mặc định xuất kho không nợ
        savedNote.setPaymentAmount(BigDecimal.ZERO);
        savedNote.setDetails(details);

        return mapToResponse(inventoryNoteRepository.save(savedNote));
    }

    // ==========================================
    // 2. CHỐT PHIẾU XUẤT (CẬP NHẬT TỒN KHO)
    // ==========================================
    @Transactional
    public InventoryNoteResponse completeExportCommand(Long id) {
        InventoryNote note = inventoryNoteRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy lệnh xuất ID: " + id));

        if (note.getStatus() == InventoryNoteStatus.COMPLETED) {
            throw new RuntimeException("Lệnh xuất này đã hoàn thành trước đó.");
        }

        note.setStatus(InventoryNoteStatus.COMPLETED);
        boolean isInternal = note.getPartnerBranch() != null;

        for (InventoryNoteDetail detail : note.getDetails()) {
            updateStockBalance(note.getBranch(), detail.getProductVariant(), -detail.getQuantityReal());

            if (isInternal) {
                updateStockBalance(note.getPartnerBranch(), detail.getProductVariant(), detail.getQuantityReal());
            }
        }
        return mapToResponse(inventoryNoteRepository.save(note));
    }

    private void updateStockBalance(Branch branch, ProductVariant variant, Integer quantityChange) {
        Inventory inv = inventoryRepository.findByBranchAndProductVariant(branch, variant)
                .orElseGet(() -> Inventory.builder().branch(branch).productVariant(variant).quantity(0).minStock(0).build());

        int newQuantity = inv.getQuantity() + quantityChange;
        if (newQuantity < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Lỗi âm kho khi cập nhật: " + branch.getName() + " - " + variant.getSku());
        }
        inv.setQuantity(newQuantity);
        inv.setLastReceiptDate(LocalDateTime.now());
        inventoryRepository.save(inv);
    }

    // ==========================================
    // CẬP NHẬT LỆNH XUẤT (CHỈ ÁP DỤNG KHI STATUS = PENDING)
    // ==========================================
    @Transactional
    public InventoryNoteResponse updateExportCommand(Long id, ExportNoteRequest request) {
        // 1. Tìm phiếu xuất cũ
        InventoryNote note = inventoryNoteRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy lệnh xuất ID: " + id));

        // 2. Kiểm tra trạng thái: Chỉ cho sửa khi đang chờ xử lý (PENDING)
        if (note.getStatus() != InventoryNoteStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chỉ có thể chỉnh sửa lệnh xuất đang chờ xử lý.");
        }

        // 3. Cập nhật thông tin chung
        note.setDeliverer(request.getSpecificReceiver());
        String fullReason = String.format("Loại: %s | Ref: %s | Đ/c: %s | Lydo: %s",
                request.getExportType(), request.getReferenceCode(), request.getShippingAddress(), request.getNote());
        note.setReason(fullReason);
        note.setNote(request.getNote());

        if (request.getExpectedDate() != null) {
            note.setEntryDate(request.getExpectedDate().atStartOfDay());
        }

        // 4. Xác định kho xuất mới (để kiểm tra tồn kho)
        Branch sourceBranch = branchRepository.findById(request.getBranchId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy kho xuất"));

        if ("RETURN".equals(request.getExportType()) && !"WAREHOUSE".equalsIgnoreCase(sourceBranch.getBranchType())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Lỗi: Chỉ có KHO TỔNG mới được phép xuất trả hàng cho Nhà cung cấp.");
        }

        note.setBranch(sourceBranch);

        // 5. Cập nhật đối tác nhận (Xóa đối tác cũ tùy theo loại xuất mới)
        if ("RETURN".equals(request.getExportType()) && request.getSupplierId() != null) {
            note.setSupplier(supplierRepository.findById(request.getSupplierId()).orElse(null));
            note.setPartnerBranch(null);
        } else if ("INTERNAL".equals(request.getExportType()) && request.getTargetBranchId() != null) {
            note.setPartnerBranch(branchRepository.findById(request.getTargetBranchId()).orElse(null));
            note.setSupplier(null);
        }

        // Xóa chi tiết sản phẩm cũ bằng cách clear collection để Hibernate tự quản lý (Tránh lỗi Hibernate 500)
        note.getDetails().clear();
        inventoryNoteDetailRepository.flush(); // Ép xóa ngay lập tức dưới Database

        final BigDecimal[] totalAmount = {BigDecimal.ZERO};

        // Tạo danh sách sản phẩm mới
        List<InventoryNoteDetail> newDetails = request.getDetails().stream().map(reqDetail -> {
            InventoryNoteDetail detail = new InventoryNoteDetail();
            detail.setInventoryNote(note);

            ProductVariant variant = productVariantRepository.findById(reqDetail.getProductVariantId())
                    .orElseThrow(() -> new RuntimeException("Sản phẩm không tồn tại ID: " + reqDetail.getProductVariantId()));
            detail.setProductVariant(variant);

            // KIỂM TRA LẠI TỒN KHO
            Inventory inv = inventoryRepository.findByBranchAndProductVariant(sourceBranch, variant)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Sản phẩm " + variant.getSku() + " không có trong kho " + sourceBranch.getName()));

            if (inv.getQuantity() < reqDetail.getRequestedQuantity()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Kho " + sourceBranch.getName() + " chỉ còn " + inv.getQuantity() + " sản phẩm " + variant.getSku());
            }

            detail.setQuantityRequested(reqDetail.getRequestedQuantity());
            detail.setQuantityReal(reqDetail.getRequestedQuantity());
            detail.setQuantity(reqDetail.getRequestedQuantity());
            detail.setPrice(reqDetail.getPrice());

            if (reqDetail.getPrice() != null && reqDetail.getRequestedQuantity() != null) {
                totalAmount[0] = totalAmount[0].add(reqDetail.getPrice().multiply(new BigDecimal(reqDetail.getRequestedQuantity())));
            }
            return detail;
        }).collect(Collectors.toList());

        // Thêm lại vào collection đang được Hibernate quản lý
        note.getDetails().addAll(newDetails);
        note.setTotalAmount(totalAmount[0]);

        return mapToResponse(inventoryNoteRepository.save(note));
    }

    // ==========================================
    // 3. LẤY DANH SÁCH (THÊM @Transactional ĐỂ FIX LỖI LAZY LOAD)
    // ==========================================
    @Transactional(readOnly = true)
    public List<InventoryNoteResponse> getAllExportCommands() {
        return getNotesByStatus(InventoryNoteStatus.PENDING);
    }

    @Transactional(readOnly = true)
    public List<InventoryNoteResponse> getAllExportReceipts() {
        return getNotesByStatus(InventoryNoteStatus.COMPLETED);
    }

    private List<InventoryNoteResponse> getNotesByStatus(InventoryNoteStatus status) {
        Long warehouseId = warehouseContext.resolveWarehouseId();
        List<InventoryNote> notes;

        if (warehouseId == null) {
            notes = inventoryNoteRepository.findAllByTypeAndStatusWithPartners(InventoryNoteType.EXPORT, status);
        } else {
            notes = inventoryNoteRepository.findAllByTypeAndStatusAndBranchWithPartners(InventoryNoteType.EXPORT, status, warehouseId);
        }

        return notes.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // ==========================================
    // 4. XÓA PHIẾU
    // ==========================================
    @Transactional
    public void deleteExportCommand(Long id) {
        InventoryNote note = inventoryNoteRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy lệnh xuất."));

        if (note.getStatus() == InventoryNoteStatus.COMPLETED) {
            boolean isInternal = note.getPartnerBranch() != null;
            for (InventoryNoteDetail detail : note.getDetails()) {
                updateStockBalance(note.getBranch(), detail.getProductVariant(), detail.getQuantityReal());
                if (isInternal) updateStockBalance(note.getPartnerBranch(), detail.getProductVariant(), -detail.getQuantityReal());
            }
        }
        inventoryNoteDetailRepository.deleteByInventoryNoteId(id);
        inventoryNoteRepository.delete(note);
    }

    // ==========================================
    // MAPPER (CÓ FALLBACK CHO DỮ LIỆU CŨ)
    // ==========================================
    private InventoryNoteResponse mapToResponse(InventoryNote entity) {
        if (entity == null) return null;
        boolean isInternal = entity.getPartnerBranch() != null;

        String partnerName = "N/A";

        if (isInternal) {
            partnerName = "[Nội bộ] " + entity.getPartnerBranch().getName();
        } else if (entity.getSupplier() != null) {
            partnerName = "[Trả NCC] " + entity.getSupplier().getName();
        } else if (entity.getDeliverer() != null && !entity.getDeliverer().isEmpty()) {
            partnerName = entity.getDeliverer();
        }

        return InventoryNoteResponse.builder()
                .id(entity.getId())
                .code(entity.getCode())
                .type(entity.getType() != null ? entity.getType().name() : "EXPORT")
                .exportType(isInternal ? "INTERNAL" : "RETURN")
                .status(entity.getStatus() != null ? entity.getStatus().name() : "PENDING")
                .reason(entity.getReason())
                .note(entity.getNote())
                .deliverer(entity.getDeliverer())
                .totalAmount(entity.getTotalAmount() != null ? entity.getTotalAmount() : BigDecimal.ZERO)
                .paymentAmount(entity.getPaymentAmount() != null ? entity.getPaymentAmount() : BigDecimal.ZERO)
                .debtAmount(entity.getDebtAmount() != null ? entity.getDebtAmount() : BigDecimal.ZERO)
                .createdAt(entity.getCreatedAt())
                .entryDate(entity.getEntryDate() != null ? entity.getEntryDate().format(DateTimeFormatter.ISO_LOCAL_DATE) : "")
                .branchId(entity.getBranch() != null ? entity.getBranch().getId() : null)
                .branchName(entity.getBranch() != null ? entity.getBranch().getName() : "N/A")
                .partnerBranchId(isInternal ? entity.getPartnerBranch().getId() : null)
                .partnerBranchName(isInternal ? entity.getPartnerBranch().getName() : null)
                .supplierId(entity.getSupplier() != null ? entity.getSupplier().getId() : null)
                .supplierName(entity.getSupplier() != null ? entity.getSupplier().getName() : null)
                .supplierCode(entity.getSupplier() != null ? entity.getSupplier().getCode() : null)
                .displayPartnerName(partnerName)
                .creatorName(entity.getCreatedBy() != null ? entity.getCreatedBy().getFullName() : "Hệ thống")
                .details(entity.getDetails() != null ? entity.getDetails().stream().map(d -> {
                    String img = d.getProductVariant() != null ? d.getProductVariant().getImageUrl() : null;
                    return InventoryNoteDetailResponse.builder()
                            .id(d.getId())
                            .productVariantId(d.getProductVariant() != null ? d.getProductVariant().getId() : null)
                            .sku(d.getProductVariant() != null ? d.getProductVariant().getSku() : "N/A")
                            .productName(d.getProductVariant() != null && d.getProductVariant().getProduct() != null ? d.getProductVariant().getProduct().getName() : "N/A")
                            .unit("Cái")
                            .quantityRequested(d.getQuantityRequested() != null ? d.getQuantityRequested() : 0)
                            .price(d.getPrice() != null ? d.getPrice() : BigDecimal.ZERO)
                            .imageUrl(img)
                            .build();
                }).collect(Collectors.toList()) : new ArrayList<>())
                .build();
    }

    @Transactional(readOnly = true)
    public InventoryNoteResponse getExportCommandById(Long id) {
        InventoryNote note = inventoryNoteRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy lệnh xuất."));
        return mapToResponse(note);
    }
}