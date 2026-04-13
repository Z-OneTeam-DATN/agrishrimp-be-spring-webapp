package com.zone.agri.service;

import com.zone.agri.dto.request.transfer.TransferQCRequest;
import com.zone.agri.dto.response.transfer.TransferDetailResponse;
import com.zone.agri.dto.request.transfer.TransferRequest;
import com.zone.agri.dto.request.transfer.TransferItemRequest;
import com.zone.agri.dto.response.transfer.TransferResponse;
import com.zone.agri.entity.*;
import com.zone.agri.entity.enums.InventoryTransferStatus;
import com.zone.agri.entity.enums.TransactionType;
import com.zone.agri.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InventoryTransferService {

    private final InventoryTransferRepository transferRepo;
    private final BranchRepository branchRepo;
    private final ProductVariantRepository variantRepo;
    private final InventoryRepository inventoryRepo;
    private final InventoryTransactionRepository transactionRepo;

    @Transactional
    public InventoryTransfer createTransfer(TransferRequest req) {
        Branch fromBranch = branchRepo.findById(req.getFromBranchId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy Kho xuất"));
        Branch toBranch = branchRepo.findById(req.getToBranchId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy Kho nhận"));

        String newCode = String.format("PDC-%06d", transferRepo.countTotalTransfers() + 1);

        InventoryTransfer transfer = InventoryTransfer.builder()
                .transferCode(newCode)
                .status(InventoryTransferStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .fromBranch(fromBranch)
                .toBranch(toBranch)
                .transferType(req.getTransferType())
                .description(req.getDescription())
                .transporter(req.getTransporter())
                .vehicle(req.getVehicle())
                .dispatchOrder(req.getDispatchOrder())
                .referenceCode(req.getReferenceCode())
                .priority(req.getPriority())
                .transferDate(req.getTransferDate())
                .deadline(req.getDeadline())
                .build();

        List<InventoryTransferDetail> details = new ArrayList<>();
        int totalQty = 0;
        BigDecimal totalValue = BigDecimal.ZERO;

        for (TransferItemRequest itemReq : req.getItems()) {
            ProductVariant variant = variantRepo.findBySku(itemReq.getSku())
                    .orElseThrow(() -> new RuntimeException("Sản phẩm với SKU " + itemReq.getSku() + " không tồn tại"));

            InventoryTransferDetail detail = InventoryTransferDetail.builder()
                    .inventoryTransfer(transfer)
                    .productVariant(variant)
                    .quantity(itemReq.getQuantity())
                    .quantityRequested(itemReq.getQuantity())
                    .quantityReal(0)
                    .note(itemReq.getItemNote())
                    .build();

            details.add(detail);
            totalQty += itemReq.getQuantity();

            // LOGIC LÔ HÀNG ĐỘNG: Ước tính Tổng giá trị phiếu chuyển dựa trên Giá vốn của các lô FIFO ở Kho xuất
            List<Inventory> sourceBatches = inventoryRepo.findByProductVariantId(variant.getId()).stream()
                    .filter(inv -> inv.getBranch().getId().equals(fromBranch.getId()) && inv.getQuantity() != null && inv.getQuantity() > 0)
                    .sorted(Comparator.comparing(Inventory::getId)) // Sắp xếp FIFO
                    .collect(Collectors.toList());

            int reqQty = itemReq.getQuantity();
            BigDecimal itemTotalValue = BigDecimal.ZERO;

            for (Inventory batch : sourceBatches) {
                if (reqQty <= 0) break;
                int take = Math.min(reqQty, batch.getQuantity());
                BigDecimal importPrice = batch.getImportPrice() != null ? batch.getImportPrice() : BigDecimal.ZERO;
                itemTotalValue = itemTotalValue.add(importPrice.multiply(BigDecimal.valueOf(take)));
                reqQty -= take;
            }

            totalValue = totalValue.add(itemTotalValue);
        }

        transfer.setDetails(details);
        transfer.setTotalQuantity(totalQty);
        transfer.setTotalValue(totalValue);

        return transferRepo.save(transfer);
    }

    @Transactional
    public void approveTransfer(Long transferId) {
        InventoryTransfer transfer = transferRepo.findById(transferId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phiếu điều chuyển"));

        if (transfer.getStatus() != InventoryTransferStatus.PENDING) {
            throw new RuntimeException("Chỉ có thể duyệt phiếu đang ở trạng thái Chờ duyệt!");
        }

        transfer.setStatus(InventoryTransferStatus.APPROVED);
        transferRepo.save(transfer);
    }

    @Transactional
    public void approveAndShip(Long transferId) {
        InventoryTransfer transfer = transferRepo.findById(transferId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phiếu điều chuyển"));

        if (transfer.getStatus() != InventoryTransferStatus.APPROVED && transfer.getStatus() != InventoryTransferStatus.PENDING) {
            throw new RuntimeException("Chỉ có thể xuất kho phiếu đang ở trạng thái Chờ duyệt hoặc Đã duyệt!");
        }

        transfer.setStatus(InventoryTransferStatus.SHIPPING);
        transferRepo.save(transfer);
    }

    // ==========================================
    // BƯỚC 3: NHẬN HÀNG (TRỪ KHO XUẤT & CỘNG KHO NHẬP THEO QC)
    // ==========================================
    @Transactional
    public void receiveTransfer(Long id, List<TransferQCRequest> qcItems) {
        InventoryTransfer transfer = transferRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phiếu điều chuyển"));

        if (transfer.getStatus() != InventoryTransferStatus.SHIPPING) {
            throw new RuntimeException("Phiếu phải ở trạng thái Đang vận chuyển mới có thể nhận hàng!");
        }

        Map<Long, InventoryTransferDetail> detailMap = transfer.getDetails().stream()
                .collect(Collectors.toMap(
                        d -> d.getProductVariant().getId(),
                        d -> d,
                        (existing, replacement) -> existing
                ));

        for (TransferQCRequest qcItemReq : qcItems) {
            Long variantId = qcItemReq.getVariantId();
            Integer qtyReal = qcItemReq.getQuantityReal();
            Integer qtyAccepted = qcItemReq.getQuantityAccepted();
            Integer qtyRejected = qcItemReq.getQuantityRejected();
            String itemNote = qcItemReq.getNote();

            InventoryTransferDetail detail = detailMap.get(variantId);
            if (detail == null) continue;

            detail.setQuantityReal(qtyReal);
            detail.setQuantityAccepted(qtyAccepted);
            detail.setQuantityRejected(qtyRejected);
            // Ghi nhận lý do hàng lỗi vào note (Ví dụ: "5 hộp móp méo")
            detail.setNote(itemNote);

            // Xử lý tồn kho: Thực tế bên kia xuất bao nhiêu thì trừ bấy nhiêu, còn bên này nhận bao nhiêu thì cộng bấy nhiêu
            if (qtyReal > 0) {
                Long fromBranchId = transfer.getFromBranch().getId();
                Branch toBranch = transfer.getToBranch();

                // 1. Trừ kho xuất (Trừ theo tổng thực tế đi đường)
                deductSourceStock(transfer, fromBranchId, variantId, qtyReal);

                // 2. Cộng kho nhận (Chỉ cộng hàng Đạt vào Available, hàng Lỗi vào Defective)
                addDestinationStock(transfer, toBranch, detail.getProductVariant(), qtyAccepted, qtyRejected);
            }
        }

        transfer.setStatus(InventoryTransferStatus.COMPLETED);
        transferRepo.save(transfer);
    }

    private void deductSourceStock(InventoryTransfer transfer, Long fromBranchId, Long variantId, int totalToDeduct) {
        int remaining = totalToDeduct;
        List<Inventory> sourceBatches = inventoryRepo.findForUpdateFIFO(fromBranchId, variantId);

        for (Inventory sBatch : sourceBatches) {
            if (remaining <= 0) break;
            int available = Objects.requireNonNullElse(sBatch.getQuantity(), 0);
            if (available <= 0) continue;

            int deduct = Math.min(available, remaining);
            sBatch.setQuantity(available - deduct);
            inventoryRepo.save(sBatch);

            transactionRepo.save(InventoryTransaction.builder()
                    .type(TransactionType.TRANSFER_OUT)
                    .quantityChange(-deduct)
                    .newBalance(Objects.requireNonNullElse(sBatch.getQuantity(), 0) + Objects.requireNonNullElse(sBatch.getDefectiveQuantity(), 0))
                    .referenceCode(transfer.getTransferCode())
                    .reason("Xuất điều chuyển (Phiếu: " + transfer.getTransferCode() + ")")
                    .createdAt(LocalDateTime.now())
                    .inventory(sBatch)
                    .build());

            remaining -= deduct;
        }
    }

    private void addDestinationStock(InventoryTransfer transfer, Branch toBranch, ProductVariant variant, int accepted, int rejected) {
        // Vì điều chuyển thường đi theo lô gốc từ kho xuất, nhưng ở đây để đơn giản ta gộp vào lô DEFAULT của kho nhận 
        // hoặc logic phức tạp hơn là phải mapping từng lô. Ở đây ta giả định nhập vào lô của kho xuất chuyển sang.
        // Tuy nhiên hàm deductSourceStock ở trên chưa trả về info lô. 
        // Để đúng yêu cầu QC: Ta sẽ tìm lô phù hợp ở kho nhận để cộng vào.
        
        if (accepted > 0) {
            updateSingleDestinationBatch(transfer, toBranch, variant, accepted, 0);
        }
        if (rejected > 0) {
            updateSingleDestinationBatch(transfer, toBranch, variant, 0, rejected);
        }
    }

    private void updateSingleDestinationBatch(InventoryTransfer transfer, Branch branch, ProductVariant variant, int accepted, int rejected) {
        // Tìm hoặc tạo lô DEFAULT tại kho nhận để nhận hàng điều chuyển
        Inventory inv = inventoryRepo.findExactBatchWithLock(branch, variant, "TRANSFER", BigDecimal.ZERO)
                .orElseGet(() -> inventoryRepo.save(Inventory.builder()
                        .branch(branch).productVariant(variant).batchNumber("TRANSFER")
                        .importPrice(BigDecimal.ZERO).quantity(0).defectiveQuantity(0).build()));

        inv.setQuantity(Objects.requireNonNullElse(inv.getQuantity(), 0) + accepted);
        inv.setDefectiveQuantity(Objects.requireNonNullElse(inv.getDefectiveQuantity(), 0) + rejected);
        inventoryRepo.save(inv);

        transactionRepo.save(InventoryTransaction.builder()
                .type(TransactionType.TRANSFER_IN)
                .quantityChange(accepted + rejected)
                .newBalance(Objects.requireNonNullElse(inv.getQuantity(), 0) + Objects.requireNonNullElse(inv.getDefectiveQuantity(), 0))
                .referenceCode(transfer.getTransferCode())
                .reason("Nhập điều chuyển QC (Phiếu: " + transfer.getTransferCode() + ")")
                .createdAt(LocalDateTime.now())
                .inventory(inv)
                .build());
    }

    // ==========================================
    // HÀM LẤY CHI TIẾT
    // ==========================================
    public TransferDetailResponse getById(Long id) {
        InventoryTransfer transfer = transferRepo.findById(id).orElseThrow();
        return convertToDetailResponse(transfer);
    }

    // ==========================================
    // HÀM LẤY DANH SÁCH
    // ==========================================
    public Page<TransferResponse> getTransfers(String keyword, String statusStr, Pageable pageable) {
        InventoryTransferStatus status = null;
        if (statusStr != null && !statusStr.isEmpty() && !statusStr.equalsIgnoreCase("all")) {
            try { status = InventoryTransferStatus.valueOf(statusStr.toUpperCase()); } catch (Exception e) {}
        }
        return transferRepo.searchTransfers(keyword, status, pageable);
    }

    @Transactional
    public void rejectTransfer(Long id) {
        InventoryTransfer transfer = transferRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phiếu điều chuyển"));
        if (transfer.getStatus() != InventoryTransferStatus.PENDING) {
            throw new RuntimeException("Chỉ có thể từ chối phiếu đang ở trạng thái Chờ duyệt!");
        }
        transfer.setStatus(InventoryTransferStatus.REJECTED);
        transferRepo.save(transfer);
    }

    @Transactional
    public void cancelTransfer(Long id) {
        InventoryTransfer transfer = transferRepo.findById(id).orElseThrow();
        if (transfer.getStatus() == InventoryTransferStatus.COMPLETED || transfer.getStatus() == InventoryTransferStatus.CANCELLED) {
            throw new RuntimeException("Chỉ có thể hủy phiếu đang ở trạng thái Chờ xuất hoặc Đang vận chuyển!");
        }
        transfer.setStatus(InventoryTransferStatus.CANCELLED);
        transferRepo.save(transfer);
    }

    @Transactional
    public void changeDestination(Long id, Long newBranchId) {
        InventoryTransfer transfer = transferRepo.findById(id).orElseThrow();
        if (transfer.getStatus() == InventoryTransferStatus.COMPLETED || transfer.getStatus() == InventoryTransferStatus.CANCELLED) {
            throw new RuntimeException("Không thể đổi chi nhánh cho phiếu đã chốt hoặc đã hủy!");
        }
        if (transfer.getFromBranch().getId().equals(newBranchId)) throw new RuntimeException("Chi nhánh nhận trùng chi nhánh xuất!");
        Branch newBranch = branchRepo.findById(newBranchId).orElseThrow();
        transfer.setToBranch(newBranch);
        transferRepo.save(transfer);
    }

    @Transactional
    public void deleteTransfer(Long id) {
        InventoryTransfer transfer = transferRepo.findById(id).orElseThrow();
        if (transfer.getStatus() != InventoryTransferStatus.PENDING) {
            throw new RuntimeException("Chỉ có thể xóa hoàn toàn phiếu đang ở trạng thái Chờ xuất (PENDING)!");
        }
        transferRepo.delete(transfer);
    }

    // ==========================================
    // HÀM CONVERT DTO (PRIVATE)
    // ==========================================
    private TransferResponse convertToResponse(InventoryTransfer t) {
        return new TransferResponse(
                t.getId(),
                t.getTransferCode(),
                t.getStatus(),
                t.getCreatedAt(),
                t.getTransferDate(),
                t.getDeadline(),
                t.getFromBranch() != null ? t.getFromBranch().getName() : "N/A",
                t.getToBranch() != null ? t.getToBranch().getName() : "N/A",
                t.getTransporter(),
                t.getPriority(),
                t.getTotalQuantity(),
                t.getDetails() != null ? t.getDetails().size() : 0,
                t.getTotalValue()
        );
    }

    private TransferDetailResponse convertToDetailResponse(InventoryTransfer t) {
        return TransferDetailResponse.builder()
                .id(t.getId())
                .transferCode(t.getTransferCode())
                .transferType(t.getTransferType())
                .status(t.getStatus())
                .description(t.getDescription())
                .vehicle(t.getVehicle())
                .transporter(t.getTransporter())
                .dispatchOrder(t.getDispatchOrder())
                .referenceCode(t.getReferenceCode())
                .createdAt(t.getCreatedAt())
                .fromBranchName(t.getFromBranch() != null ? t.getFromBranch().getName() : "N/A")
                .toBranchName(t.getToBranch() != null ? t.getToBranch().getName() : "N/A")
                .totalQuantity(t.getTotalQuantity())
                .totalValue(t.getTotalValue())
                .items(t.getDetails().stream().map(d -> TransferDetailResponse.ItemDetail.builder()
                        .variantId(d.getProductVariant().getId())
                        .productName(d.getProductVariant().getProduct().getName())
                        .sku(d.getProductVariant().getSku())
                        .unit("Cái")
                        .quantityRequested(d.getQuantityRequested())
                        .quantityReal(d.getQuantityReal())
                        .quantityAccepted(d.getQuantityAccepted())
                        .quantityRejected(d.getQuantityRejected())
                        .note(d.getNote())
                        .build()).toList())
                .build();
    }
}
