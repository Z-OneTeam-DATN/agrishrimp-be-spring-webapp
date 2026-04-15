package com.zone.agri.service;

import com.zone.agri.dto.request.transfer.TransferQCRequest;
import com.zone.agri.dto.response.transfer.TransferDetailResponse;
import com.zone.agri.dto.request.transfer.TransferRequest;
import com.zone.agri.dto.request.transfer.TransferItemRequest;
import com.zone.agri.dto.response.transfer.TransferResponse;
import com.zone.agri.entity.*;
import com.zone.agri.entity.enums.InventoryTransferStatus;
import com.zone.agri.entity.enums.TransactionType;
import com.zone.agri.entity.enums.OrderStatus;
import com.zone.agri.entity.enums.TransferBusinessType;
import com.zone.agri.entity.enums.TransferSettlementStatus;
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
    private final BackorderService backorderService;

    @Transactional
    public InventoryTransfer createTransfer(TransferRequest req) {
        Branch fromBranch = branchRepo.findById(req.getFromBranchId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy Kho xuất"));
        Branch toBranch = branchRepo.findById(req.getToBranchId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy Kho nhận"));

        // Xác định loại nghiệp vụ (mặc định STOCK_TRANSFER nếu không truyền)
        TransferBusinessType businessType = TransferBusinessType.STOCK_TRANSFER;
        if ("INTERNAL_SALE".equalsIgnoreCase(req.getTransferBusinessType())) {
            businessType = TransferBusinessType.INTERNAL_SALE;
        }

        // Validate: INTERNAL_SALE bắt buộc mỗi dòng phải có unitTransferPrice > 0
        if (businessType == TransferBusinessType.INTERNAL_SALE) {
            for (TransferItemRequest itemReq : req.getItems()) {
                if (itemReq.getUnitTransferPrice() == null || itemReq.getUnitTransferPrice().compareTo(BigDecimal.ZERO) <= 0) {
                    throw new RuntimeException(
                            "Phiếu bán nội bộ yêu cầu đơn giá điều chuyển > 0 cho từng mặt hàng (SKU: " + itemReq.getSku() + ")");
                }
            }
        }

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
                .transferBusinessType(businessType)
                // INTERNAL_SALE: khởi tạo trạng thái nợ nội bộ = UNPAID
                .settlementStatus(businessType == TransferBusinessType.INTERNAL_SALE ? TransferSettlementStatus.UNPAID : null)
                .build();

        List<InventoryTransferDetail> details = new ArrayList<>();
        int totalQty = 0;
        BigDecimal totalValue = BigDecimal.ZERO;     // Tổng theo giá vốn FIFO (quản trị kho)
        BigDecimal transferAmount = BigDecimal.ZERO; // Tổng theo giá bán nội bộ (chỉ INTERNAL_SALE)

        for (TransferItemRequest itemReq : req.getItems()) {
            ProductVariant variant = variantRepo.findBySku(itemReq.getSku())
                    .orElseThrow(() -> new RuntimeException("Sản phẩm với SKU " + itemReq.getSku() + " không tồn tại"));

            // Tính giá bán nội bộ cho dòng này (INTERNAL_SALE)
            BigDecimal unitPrice = BigDecimal.ZERO;
            BigDecimal lineTotalTransferPrice = BigDecimal.ZERO;
            if (businessType == TransferBusinessType.INTERNAL_SALE && itemReq.getUnitTransferPrice() != null) {
                unitPrice = itemReq.getUnitTransferPrice();
                lineTotalTransferPrice = unitPrice.multiply(BigDecimal.valueOf(itemReq.getQuantity()));
                transferAmount = transferAmount.add(lineTotalTransferPrice);
            }

            InventoryTransferDetail detail = InventoryTransferDetail.builder()
                    .inventoryTransfer(transfer)
                    .productVariant(variant)
                    .quantity(itemReq.getQuantity())
                    .quantityRequested(itemReq.getQuantity())
                    .quantityReal(0)
                    .note(itemReq.getItemNote())
                    .unitTransferPrice(businessType == TransferBusinessType.INTERNAL_SALE ? unitPrice : null)
                    .totalTransferPrice(businessType == TransferBusinessType.INTERNAL_SALE ? lineTotalTransferPrice : null)
                    .build();

            details.add(detail);
            totalQty += itemReq.getQuantity();

            // LOGIC LÔ HÀNG ĐỘNG: Ước tính Tổng giá trị phiếu chuyển dựa trên Giá vốn của
            // các lô FIFO ở Kho xuất
            List<Inventory> sourceBatches = inventoryRepo.findByProductVariantId(variant.getId()).stream()
                    .filter(inv -> inv.getBranch().getId().equals(fromBranch.getId()) && inv.getQuantity() != null
                            && inv.getQuantity() > 0)
                    .sorted(Comparator.comparing(Inventory::getId)) // Sắp xếp FIFO
                    .collect(Collectors.toList());

            int reqQty = itemReq.getQuantity();
            BigDecimal itemTotalValue = BigDecimal.ZERO;

            for (Inventory batch : sourceBatches) {
                if (reqQty <= 0)
                    break;
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

        // INTERNAL_SALE: gán tổng thành tiền nội bộ và công nợ nội bộ 2 phía
        if (businessType == TransferBusinessType.INTERNAL_SALE) {
            transfer.setTransferAmount(transferAmount);
            transfer.setSourceReceivableAmount(transferAmount); // Kho xuất: phải thu nội bộ
            transfer.setDestPayableAmount(transferAmount);       // Kho nhận: phải trả nội bộ
        }

        return transferRepo.save(transfer);
    }

    @Transactional
    public List<InventoryTransfer> createReplenishmentTransfersForSubOrder(SubOrder subOrder) {
        if (subOrder.getStatus() != OrderStatus.AWAITING_REPLENISHMENT) {
            throw new RuntimeException("Chỉ có thể tạo điều chuyển bổ sung cho phần đơn đang chờ điều chuyển");
        }

        String referenceCode = subOrder.getOrder().getCode() + "-SUB-" + subOrder.getId();
        if (transferRepo.existsByReferenceCodeAndStatusIn(referenceCode,
                List.of(InventoryTransferStatus.PENDING, InventoryTransferStatus.SHIPPING))) {
            throw new RuntimeException("Phần đơn này đã có lệnh điều chuyển đang xử lý");
        }

        Map<Long, Map<String, Integer>> transferPlanBySourceBranch = new LinkedHashMap<>();

        // Lấy tọa độ chi nhánh đích (nơi nhận hàng)
        Branch toBranch = subOrder.getBranch();
        if (toBranch.getLat() == null || toBranch.getLng() == null) {
            throw new RuntimeException("Chi nhánh nhận hàng chưa có tọa độ địa lý");
        }

        List<SubOrderItem> subOrderItems = subOrder.getItems() != null ? subOrder.getItems() : List.of();
        for (SubOrderItem item : subOrderItems) {
            int missingQty = Objects.requireNonNullElse(item.getMissingQuantity(), 0);
            if (missingQty <= 0 || item.getProductVariant() == null) {
                continue;
            }

            Map<Long, Integer> availableByBranch = inventoryRepo
                    .findByProductVariantId(item.getProductVariant().getId()).stream()
                    .filter(inv -> inv.getBranch() != null
                            && !inv.getBranch().getId().equals(subOrder.getBranch().getId())
                            && Objects.requireNonNullElse(inv.getQuantity(), 0) > 0)
                    .collect(Collectors.groupingBy(inv -> inv.getBranch().getId(),
                            LinkedHashMap::new,
                            Collectors.summingInt(inv -> Objects.requireNonNullElse(inv.getQuantity(), 0))));

            // Sắp xếp chi nhánh theo khoảng cách gần nhất (để tiết kiệm chi phí vận chuyển)
            int remaining = missingQty;
            for (Map.Entry<Long, Integer> candidate : availableByBranch.entrySet().stream()
                    .sorted((e1, e2) -> {
                        Branch branch1 = branchRepo.findById(e1.getKey()).orElse(null);
                        Branch branch2 = branchRepo.findById(e2.getKey()).orElse(null);
                        if (branch1 == null || branch1.getLat() == null || branch1.getLng() == null)
                            return 1;
                        if (branch2 == null || branch2.getLat() == null || branch2.getLng() == null)
                            return -1;

                        double dist1 = calculateHaversineDistance(toBranch.getLat(), toBranch.getLng(),
                                branch1.getLat(), branch1.getLng());
                        double dist2 = calculateHaversineDistance(toBranch.getLat(), toBranch.getLng(),
                                branch2.getLat(), branch2.getLng());
                        return Double.compare(dist1, dist2);
                    })
                    .toList()) {
                if (remaining <= 0) {
                    break;
                }

                int quantityToTransfer = Math.min(remaining, candidate.getValue());
                transferPlanBySourceBranch
                        .computeIfAbsent(candidate.getKey(), key -> new LinkedHashMap<>())
                        .merge(item.getProductVariant().getSku(), quantityToTransfer, Integer::sum);
                remaining -= quantityToTransfer;
            }
        }

        if (transferPlanBySourceBranch.isEmpty()) {
            throw new RuntimeException("Không tìm thấy chi nhánh nào có tồn kho để điều chuyển bổ sung");
        }

        List<InventoryTransfer> transfers = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        for (Map.Entry<Long, Map<String, Integer>> entry : transferPlanBySourceBranch.entrySet()) {
            TransferRequest request = new TransferRequest();
            request.setFromBranchId(entry.getKey());
            request.setToBranchId(subOrder.getBranch().getId());
            request.setTransferType("ORDER_REPLENISHMENT");
            request.setDescription("Điều chuyển hàng cho đơn " + subOrder.getOrder().getCode());
            request.setReferenceCode(referenceCode);
            request.setPriority("HIGH");
            request.setTransferDate(now);
            request.setDeadline(now.plusDays(1));

            List<TransferItemRequest> requestItems = entry.getValue().entrySet().stream()
                    .map(itemEntry -> {
                        TransferItemRequest itemRequest = new TransferItemRequest();
                        itemRequest.setSku(itemEntry.getKey());
                        itemRequest.setQuantity(itemEntry.getValue());
                        itemRequest.setItemNote("Bổ sung cho phần đơn " + referenceCode);
                        return itemRequest;
                    })
                    .toList();

            request.setItems(requestItems);
            transfers.add(createTransfer(request));
        }

        return transfers;
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

        if (transfer.getStatus() != InventoryTransferStatus.APPROVED
                && transfer.getStatus() != InventoryTransferStatus.PENDING) {
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
                        (existing, replacement) -> existing));

        for (TransferQCRequest qcItemReq : qcItems) {
            Long variantId = qcItemReq.getVariantId();
            Integer qtyReal = qcItemReq.getQuantityReal();
            Integer qtyAccepted = qcItemReq.getQuantityAccepted();
            Integer qtyRejected = qcItemReq.getQuantityRejected();
            String itemNote = qcItemReq.getNote();

            InventoryTransferDetail detail = detailMap.get(variantId);
            if (detail == null)
                continue;

            detail.setQuantityReal(qtyReal);
            detail.setQuantityAccepted(qtyAccepted);
            detail.setQuantityRejected(qtyRejected);
            // Ghi nhận lý do hàng lỗi vào note (Ví dụ: "5 hộp móp méo")
            detail.setNote(itemNote);

            // Xử lý tồn kho: Thực tế bên kia xuất bao nhiêu thì trừ bấy nhiêu, còn bên này
            // nhận bao nhiêu thì cộng bấy nhiêu
            if (qtyReal > 0) {
                Long fromBranchId = transfer.getFromBranch().getId();
                Branch toBranch = transfer.getToBranch();

                // 1. Trừ kho xuất (Trừ theo tổng thực tế đi đường)
                deductSourceStock(transfer, fromBranchId, variantId, qtyReal);

                // 2. Cộng kho nhận (Chỉ cộng hàng Đạt vào Available, hàng Lỗi vào Defective)
                addDestinationStock(transfer, toBranch, detail.getProductVariant(), qtyAccepted, qtyRejected);

                // 👉 KÍCH HOẠT XỬ LÝ BACKORDER: Tự động trừ kho và đẩy đơn
                // AWAITING_REPLENISHMENT -> PROCESSING
                // Dùng qtyAccepted (hàng đạt chất lượng) để trả nợ đơn hàng
                backorderService.fulfillBackordersOnStockReceive(toBranch.getId(), variantId, qtyAccepted);
            }
        }

        transfer.setStatus(InventoryTransferStatus.COMPLETED);
        transferRepo.save(transfer);
    }

    private void deductSourceStock(InventoryTransfer transfer, Long fromBranchId, Long variantId, int totalToDeduct) {
        int remaining = totalToDeduct;
        List<Inventory> sourceBatches = inventoryRepo.findForUpdateFIFO(fromBranchId, variantId);

        for (Inventory sBatch : sourceBatches) {
            if (remaining <= 0)
                break;
            int available = Objects.requireNonNullElse(sBatch.getQuantity(), 0);
            if (available <= 0)
                continue;

            int deduct = Math.min(available, remaining);
            sBatch.setQuantity(available - deduct);
            inventoryRepo.save(sBatch);

            transactionRepo.save(InventoryTransaction.builder()
                    .type(TransactionType.TRANSFER_OUT)
                    .quantityChange(-deduct)
                    .newBalance(Objects.requireNonNullElse(sBatch.getQuantity(), 0)
                            + Objects.requireNonNullElse(sBatch.getDefectiveQuantity(), 0))
                    .referenceCode(transfer.getTransferCode())
                    .reason("Xuất điều chuyển (Phiếu: " + transfer.getTransferCode() + ")")
                    .createdAt(LocalDateTime.now())
                    .inventory(sBatch)
                    .build());

            remaining -= deduct;
        }
    }

    private void addDestinationStock(InventoryTransfer transfer, Branch toBranch, ProductVariant variant, int accepted,
            int rejected) {
        // Vì điều chuyển thường đi theo lô gốc từ kho xuất, nhưng ở đây để đơn giản ta
        // gộp vào lô DEFAULT của kho nhận
        // hoặc logic phức tạp hơn là phải mapping từng lô. Ở đây ta giả định nhập vào
        // lô của kho xuất chuyển sang.
        // Tuy nhiên hàm deductSourceStock ở trên chưa trả về info lô.
        // Để đúng yêu cầu QC: Ta sẽ tìm lô phù hợp ở kho nhận để cộng vào.

        if (accepted > 0) {
            updateSingleDestinationBatch(transfer, toBranch, variant, accepted, 0);
        }
        if (rejected > 0) {
            updateSingleDestinationBatch(transfer, toBranch, variant, 0, rejected);
        }
    }

    private void updateSingleDestinationBatch(InventoryTransfer transfer, Branch branch, ProductVariant variant,
            int accepted, int rejected) {
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
                .newBalance(Objects.requireNonNullElse(inv.getQuantity(), 0)
                        + Objects.requireNonNullElse(inv.getDefectiveQuantity(), 0))
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
            try {
                status = InventoryTransferStatus.valueOf(statusStr.toUpperCase());
            } catch (Exception e) {
            }
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
        if (transfer.getStatus() == InventoryTransferStatus.COMPLETED
                || transfer.getStatus() == InventoryTransferStatus.CANCELLED) {
            throw new RuntimeException("Chỉ có thể hủy phiếu đang ở trạng thái Chờ xuất hoặc Đang vận chuyển!");
        }
        transfer.setStatus(InventoryTransferStatus.CANCELLED);
        transferRepo.save(transfer);
    }

    @Transactional
    public void changeDestination(Long id, Long newBranchId) {
        InventoryTransfer transfer = transferRepo.findById(id).orElseThrow();
        if (transfer.getStatus() == InventoryTransferStatus.COMPLETED
                || transfer.getStatus() == InventoryTransferStatus.CANCELLED) {
            throw new RuntimeException("Không thể đổi chi nhánh cho phiếu đã chốt hoặc đã hủy!");
        }
        if (transfer.getFromBranch().getId().equals(newBranchId))
            throw new RuntimeException("Chi nhánh nhận trùng chi nhánh xuất!");
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
                t.getTotalValue(),
                t.getTransferBusinessType(),
                t.getSettlementStatus(),
                t.getTransferAmount());
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
                .transferBusinessType(t.getTransferBusinessType())
                .transferAmount(t.getTransferAmount())
                .settlementStatus(t.getSettlementStatus())
                .sourceReceivableAmount(t.getSourceReceivableAmount())
                .destPayableAmount(t.getDestPayableAmount())
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
                        .unitTransferPrice(d.getUnitTransferPrice())
                        .totalTransferPrice(d.getTotalTransferPrice())
                        .build()).toList())
                .build();
    }

    /**
     * Tính khoảng cách Haversine giữa hai điểm (lat1, lng1) và (lat2, lng2) - đơn
     * vị KM
     */
    private double calculateHaversineDistance(Double lat1, Double lng1, Double lat2, Double lng2) {
        if (lat1 == null || lng1 == null || lat2 == null || lng2 == null) {
            return Double.MAX_VALUE;
        }

        final int EARTH_RADIUS = 6371; // Bán kính Trái Đất tính bằng km

        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLng / 2) * Math.sin(dLng / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIUS * c;
    }
}
