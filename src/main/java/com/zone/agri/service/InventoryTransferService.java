package com.zone.agri.service;

import com.zone.agri.dto.transfer.TransferDetailResponse;
import com.zone.agri.dto.transfer.TransferRequest;
import com.zone.agri.dto.transfer.TransferItemRequest;
import com.zone.agri.dto.transfer.TransferResponse;
import com.zone.agri.entity.*;
import com.zone.agri.entity.enums.InventoryTransferStatus;
import com.zone.agri.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InventoryTransferService {

    private final InventoryTransferRepository transferRepo;
    private final BranchRepository branchRepo;
    private final ProductVariantRepository variantRepo;
    private final InventoryRepository inventoryRepo;

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

            if (variant.getPrice() != null) {
                BigDecimal lineTotal = variant.getPrice().multiply(new BigDecimal(itemReq.getQuantity()));
                totalValue = totalValue.add(lineTotal);
            }
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
            String note = itemData.get( "note") != null ? itemData.get("note").toString() : "";

            InventoryTransferDetail detail = detailMap.get(variantId);
            if (detail == null) {
                throw new RuntimeException("Sản phẩm ID " + variantId + " không tồn tại trong phiếu này!");
            }

            detail.setQuantityReal(qtyReal);
            detail.setNote(note);

            if (qtyReal > 0) {
                Long fromBranchId = transfer.getFromBranch().getId();
                Long toBranchId = transfer.getToBranch().getId();

                updateInventoryQuantity(fromBranchId, variantId, -qtyReal);
                updateInventoryQuantity(toBranchId, variantId, qtyReal);
            }
        }

        transfer.setStatus(InventoryTransferStatus.COMPLETED);
        transferRepo.save(transfer);
        transferRepo.flush();
    }

    public TransferDetailResponse getById(Long id) {
        InventoryTransfer transfer = transferRepo.findById(id).orElseThrow();
        return convertToDetailResponse(transfer);
    }

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
    // LOGIC ĐỒNG BỘ: ÁP DỤNG LÔ HÀNG VÀ FIFO
    // ==========================================
    private void updateInventoryQuantity(Long branchId, Long variantId, Integer quantityChange) {
        Branch branch = branchRepo.findById(branchId).orElseThrow();
        ProductVariant variant = variantRepo.findById(variantId).orElseThrow();

        if (quantityChange < 0) {
            // XUẤT: Áp dụng thuật toán trừ kho FIFO
            List<Inventory> batches = inventoryRepo.findAvailableBatchesForVariant(branchId, variantId);
            int remaining = -quantityChange;
            for (Inventory batch : batches) {
                if (remaining <= 0) break;
                int deduct = Math.min(batch.getQuantity(), remaining);
                batch.setQuantity(batch.getQuantity() - deduct);
                inventoryRepo.save(batch);
                remaining -= deduct;
            }
            if (remaining > 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Kho " + branch.getName() + " không đủ số lượng để xuất!");
            }
        } else if (quantityChange > 0) {
            // NHẬP: Vì không chỉ định lô trên UI, gán vào lô mặc định tên là TRANSFER
            Inventory inventory = inventoryRepo.findExactBatch(branch, variant, "TRANSFER", BigDecimal.ZERO)
                    .orElseGet(() -> {
                        Inventory newInv = new Inventory();
                        newInv.setBranch(branch);
                        newInv.setProductVariant(variant);
                        newInv.setBatchNumber("TRANSFER");
                        newInv.setImportPrice(BigDecimal.ZERO);
                        newInv.setQuantity(0);
                        return newInv;
                    });

            inventory.setQuantity(inventory.getQuantity() + quantityChange);
            inventory.setLastReceiptDate(LocalDateTime.now());
            inventoryRepo.save(inventory);
        }
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