
package com.zone.agri.service;

import com.zone.agri.dto.request.inventory.ExportNoteRequest;
import com.zone.agri.dto.response.InventoryNoteResponse;
import com.zone.agri.dto.response.InventoryNoteDetailResponse;
import com.zone.agri.entity.InventoryNote;
import com.zone.agri.entity.InventoryNoteDetail;
import com.zone.agri.entity.enums.InventoryNoteStatus;
import com.zone.agri.entity.enums.InventoryNoteType;
import com.zone.agri.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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

    @Transactional
    public InventoryNoteResponse createExportCommand(ExportNoteRequest request) {
        InventoryNote note = new InventoryNote();

        note.setCode(request.getCode() != null ? request.getCode() : "LXK-" + System.currentTimeMillis());
        note.setType(InventoryNoteType.EXPORT);
        note.setStatus(InventoryNoteStatus.PENDING);

        String fullReason = String.format("Loại xuất: %s | Tham chiếu: %s | Người nhận: %s | Địa chỉ: %s | Ghi chú: %s",
                request.getExportType(),
                request.getReferenceCode() != null ? request.getReferenceCode() : "Không",
                request.getSpecificReceiver() != null ? request.getSpecificReceiver() : "Không",
                request.getShippingAddress() != null ? request.getShippingAddress() : "Không",
                request.getNote() != null ? request.getNote() : ""
        );
        note.setReason(fullReason);

        note.setBranch(branchRepository.findById(request.getBranchId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy kho xuất")));

        if (request.getCreatedById() != null) {
            userRepository.findById(request.getCreatedById()).ifPresent(note::setCreatedBy);
        }

        if ("RETURN".equals(request.getExportType()) && request.getSupplierId() != null) {
            note.setSupplier(supplierRepository.findById(request.getSupplierId()).orElse(null));
        } else if ("INTERNAL".equals(request.getExportType()) && request.getTargetBranchId() != null) {
            note.setPartnerBranch(branchRepository.findById(request.getTargetBranchId()).orElse(null));
        }

        note.setCreatedAt(LocalDateTime.now());
        note.setTotalAmount(BigDecimal.ZERO);

        InventoryNote savedNote = inventoryNoteRepository.save(note);
        final BigDecimal[] totalAmount = {BigDecimal.ZERO};

        List<InventoryNoteDetail> details = request.getDetails().stream().map(reqDetail -> {
            InventoryNoteDetail detail = new InventoryNoteDetail();
            detail.setInventoryNote(savedNote);
            detail.setProductVariant(productVariantRepository.findById(reqDetail.getProductVariantId())
                    .orElseThrow(() -> new RuntimeException("Sản phẩm không tồn tại ID: " + reqDetail.getProductVariantId())));

            detail.setQuantityRequested(reqDetail.getRequestedQuantity());
            detail.setQuantityReal(0);
            detail.setQuantity(reqDetail.getRequestedQuantity());
            detail.setPrice(reqDetail.getPrice());

            if (reqDetail.getPrice() != null && reqDetail.getRequestedQuantity() != null) {
                BigDecimal lineTotal = reqDetail.getPrice().multiply(new BigDecimal(reqDetail.getRequestedQuantity()));
                totalAmount[0] = totalAmount[0].add(lineTotal);
            }
            return detail;
        }).collect(Collectors.toList());

        inventoryNoteDetailRepository.saveAll(details);

        savedNote.setTotalAmount(totalAmount[0]);
        savedNote.setDetails(details);
        inventoryNoteRepository.save(savedNote);

        return mapToResponse(savedNote);
    }

    // 1. CHỈ LẤY CÁC LỆNH ĐANG CHỜ XỬ LÝ (Tab Lệnh chờ xuất)
    public List<InventoryNoteResponse> getAllExportCommands() {
        return inventoryNoteRepository.findAll().stream()
                .filter(note -> note.getStatus() == InventoryNoteStatus.PENDING) // Thêm điều kiện lọc
                .map(this::mapToResponse)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    // 2. CHỈ LẤY CÁC PHIẾU ĐÃ HOÀN THÀNH (Tab Lịch sử xuất kho)
    public List<InventoryNoteResponse> getAllExportReceipts() {
        return inventoryNoteRepository.findAll().stream()
                .filter(note -> note.getStatus() == InventoryNoteStatus.COMPLETED) // Thêm điều kiện lọc
                .map(this::mapToResponse)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private InventoryNoteResponse mapToResponse(InventoryNote entity) {
        if (entity == null) return null;

        return InventoryNoteResponse.builder()
                .id(entity.getId())
                .code(entity.getCode())
                .type(entity.getType() != null ? entity.getType().name() : "UNKNOWN")
                .status(entity.getStatus() != null ? entity.getStatus().name() : "UNKNOWN")
                .reason(entity.getReason() != null ? entity.getReason() : "")
                .totalAmount(entity.getTotalAmount() != null ? entity.getTotalAmount() : BigDecimal.ZERO)
                .createdAt(entity.getCreatedAt())
                .branchName(entity.getBranch() != null ? entity.getBranch().getName() : "Không xác định")
                .creatorName(entity.getCreatedBy() != null ? entity.getCreatedBy().getFullName() : "Hệ thống")
                .supplierName(entity.getSupplier() != null ? entity.getSupplier().getName() : null)
                .partnerBranchName(entity.getPartnerBranch() != null ? entity.getPartnerBranch().getName() : null)
                .details(entity.getDetails() != null ? entity.getDetails().stream().map(d -> {
                    if (d == null) return null;
                    String sku = "N/A";
                    String productName = "Sản phẩm không xác định";
                    if (d.getProductVariant() != null) {
                        sku = d.getProductVariant().getSku();
                        if (d.getProductVariant().getProduct() != null) {
                            productName = d.getProductVariant().getProduct().getName();
                        }
                    }
                    return InventoryNoteDetailResponse.builder()
                            .id(d.getId())
                            .sku(sku)
                            .productName(productName)
                            .quantityRequested(d.getQuantityRequested() != null ? d.getQuantityRequested() : 0)
                            .price(d.getPrice() != null ? d.getPrice() : BigDecimal.ZERO)
                            .build();
                }).filter(Objects::nonNull).collect(Collectors.toList()) : new ArrayList<>())
                .build();
    }

    @Transactional
    public void deleteExportCommand(Long id) {
        // 1. Kiểm tra lệnh có tồn tại không
        InventoryNote note = inventoryNoteRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy lệnh xuất ID: " + id));

        // 2. Kiểm tra trạng thái (Tùy chọn: Chỉ cho xóa khi lệnh ở trạng thái PENDING hoặc CANCELLED)
        if (note.getStatus() == InventoryNoteStatus.COMPLETED) {
            throw new RuntimeException("Không thể xóa lệnh đã hoàn thành xuất kho.");
        }

        // 3. Xóa các dòng chi tiết trước (Xóa con trước)
        inventoryNoteDetailRepository.deleteByInventoryNoteId(id);

        // 4. Xóa lệnh chính (Xóa cha sau)
        inventoryNoteRepository.delete(note);
    }
}