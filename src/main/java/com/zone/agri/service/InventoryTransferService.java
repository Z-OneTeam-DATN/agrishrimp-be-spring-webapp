package com.zone.agri.service;

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
    public void approveAndShip(Long transferId) {
        InventoryTransfer transfer = transferRepo.findById(transferId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phiếu điều chuyển"));

        if (transfer.getStatus() != InventoryTransferStatus.PENDING) {
            throw new RuntimeException("Chỉ có thể xuất kho phiếu đang ở trạng thái Chờ Xuất!");
        }

        transfer.setStatus(InventoryTransferStatus.SHIPPING);
        transferRepo.save(transfer);
    }

    // ==========================================
    // BƯỚC 3: NHẬN HÀNG (TRỪ KHO XUẤT & CỘNG KHO NHẬP THEO LÔ FIFO)
    // ==========================================
    @Transactional
    public void receiveTransfer(Long id, List<Map<String, Object>> receivedItems) {
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

        for (Map<String, Object> itemData : receivedItems) {
            Long variantId = ((Number) itemData.get("variantId")).longValue();
            Integer qtyReal = ((Number) itemData.get("quantityReal")).intValue();
            String itemNote = itemData.get("note") != null ? itemData.get("note").toString() : "";

            InventoryTransferDetail detail = detailMap.get(variantId);
            if (detail == null) {
                throw new RuntimeException("Sản phẩm ID " + variantId + " không tồn tại trong phiếu này!");
            }

            detail.setQuantityReal(qtyReal);
            detail.setNote(itemNote);

            // LOGIC LÔ HÀNG ĐỘNG: Di chuyển Lô hàng từ Kho A sang Kho B
            if (qtyReal > 0) {
                Long fromBranchId = transfer.getFromBranch().getId();
                Branch toBranch = transfer.getToBranch();

                int remainingToTransfer = qtyReal;

                // 1. Lấy danh sách Lô hàng đang có ở KHO XUẤT (FIFO) và Khóa (Lock) lại
                List<Inventory> sourceBatches = inventoryRepo.findForUpdateFIFO(fromBranchId, variantId);

                for (Inventory sBatch : sourceBatches) {
                    if (remainingToTransfer <= 0) break;
                    int available = Objects.requireNonNullElse(sBatch.getQuantity(), 0);
                    if (available <= 0) continue;

                    int transferAmount = Math.min(available, remainingToTransfer);

                    // A. TRỪ TỒN KHO XUẤT
                    int newSourceQty = available - transferAmount;
                    sBatch.setQuantity(newSourceQty);
                    inventoryRepo.save(sBatch);

                    // Ghi log biến động kho (Xuất đi)
                    transactionRepo.save(InventoryTransaction.builder()
                            .type(TransactionType.TRANSFER_OUT)
                            .quantityChange(-transferAmount)
                            .newBalance(newSourceQty)
                            .referenceCode(transfer.getTransferCode())
                            .reason("Điều chuyển đi (Phiếu: " + transfer.getTransferCode() + ")")
                            .createdAt(LocalDateTime.now())
                            .inventory(sBatch)
                            .build());

                    // B. CỘNG VÀO KHO NHẬN (Sử dụng findExactBatchWithLock để tránh Race Condition)
                    Inventory tBatch = inventoryRepo.findExactBatchWithLock(toBranch, detail.getProductVariant(), sBatch.getBatchNumber(), sBatch.getImportPrice())
                            .orElseGet(() -> {
                                Inventory newBatch = Inventory.builder()
                                        .branch(toBranch)
                                        .productVariant(detail.getProductVariant())
                                        .quantity(0)
                                        .importPrice(sBatch.getImportPrice())
                                        .batchNumber(sBatch.getBatchNumber())
                                        .expiryDate(sBatch.getExpiryDate())
                                        .minStock(0)
                                        .lastCheckedAt(LocalDateTime.now())
                                        .lastReceiptDate(LocalDateTime.now())
                                        .build();
                                return inventoryRepo.save(newBatch);
                            });

                    int oldTargetQty = Objects.requireNonNullElse(tBatch.getQuantity(), 0);
                    int newTargetQty = oldTargetQty + transferAmount;
                    tBatch.setQuantity(newTargetQty);
                    tBatch.setLastReceiptDate(LocalDateTime.now());
                    inventoryRepo.save(tBatch);

                    // Ghi log biến động kho (Nhập đến)
                    transactionRepo.save(InventoryTransaction.builder()
                            .type(TransactionType.TRANSFER_IN)
                            .quantityChange(transferAmount)
                            .newBalance(newTargetQty)
                            .referenceCode(transfer.getTransferCode())
                            .reason("Điều chuyển đến (Phiếu: " + transfer.getTransferCode() + ")")
                            .createdAt(LocalDateTime.now())
                            .inventory(tBatch)
                            .build());

                    remainingToTransfer -= transferAmount;
                }

                if (remainingToTransfer > 0) {
                    throw new RuntimeException("Lỗi đồng bộ: Kho xuất (" + transfer.getFromBranch().getName() +
                            ") không đủ số lượng Lô hàng cho sản phẩm " + detail.getProductVariant().getSku());
                }
            }
        }

        transfer.setStatus(InventoryTransferStatus.COMPLETED);
        transferRepo.save(transfer);
        transferRepo.flush();
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
                        .note(d.getNote())
                        .build()).toList())
                .build();
    }
}
