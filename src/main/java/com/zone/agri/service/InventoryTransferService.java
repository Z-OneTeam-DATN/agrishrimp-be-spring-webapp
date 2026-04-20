package com.zone.agri.service;

import com.zone.agri.dto.request.transfer.TransferQCRequest;
import com.zone.agri.dto.response.transfer.TransferDetailResponse;
import com.zone.agri.dto.request.transfer.TransferRequest;
import com.zone.agri.dto.request.transfer.TransferItemRequest;
import com.zone.agri.dto.response.transfer.TransferResponse;
import com.zone.agri.entity.*;
import com.zone.agri.entity.enums.BranchStatus;
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
    private static final String GENERAL_WAREHOUSE_CODE = "MAIN_WH";
    private static final String SYSTEM_DEFECT_BRANCH_CODE = "SYSTEM_DEFECT";
    private static final String SYSTEM_DEFECT_BRANCH_PHONE = "SYS-DEFECT-01";

    private final InventoryTransferRepository transferRepo;
    private final BranchRepository branchRepo;
    private final ProductVariantRepository variantRepo;
    private final InventoryRepository inventoryRepo;
    private final InventoryTransactionRepository transactionRepo;
    private final BackorderService backorderService;
    private final com.zone.agri.repository.InventoryTransferDetailRepository transferDetailRepo;
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(InventoryTransferService.class);

    private Branch resolveSystemDefectBranch() {
        return branchRepo.findByBranchCode(SYSTEM_DEFECT_BRANCH_CODE)
                .orElseGet(() -> branchRepo.save(Branch.builder()
                        .branchCode(SYSTEM_DEFECT_BRANCH_CODE)
                        .branchType("WAREHOUSE")
                        .name("Kho lỗi hệ thống")
                        .phone(SYSTEM_DEFECT_BRANCH_PHONE)
                        .email("system-defect@agrishrimp.vn")
                        .addressDetail("Kho ảo dùng để gom hàng lỗi hoặc thiếu phát sinh từ điều chuyển nội bộ")
                        .status(BranchStatus.ACTIVE)
                        .build()));
    }

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

        if (businessType == TransferBusinessType.STOCK_TRANSFER) {
            if (!GENERAL_WAREHOUSE_CODE.equalsIgnoreCase(fromBranch.getBranchCode())) {
                throw new RuntimeException("Luồng cấp phát nội bộ chỉ được xuất từ Kho tổng.");
            }
            if (GENERAL_WAREHOUSE_CODE.equalsIgnoreCase(toBranch.getBranchCode())) {
                throw new RuntimeException("Luồng cấp phát nội bộ phải chuyển tới chi nhánh nhận, không phải Kho tổng.");
            }
        } else {
            if (GENERAL_WAREHOUSE_CODE.equalsIgnoreCase(fromBranch.getBranchCode())
                    || GENERAL_WAREHOUSE_CODE.equalsIgnoreCase(toBranch.getBranchCode())) {
                throw new RuntimeException("Luồng thương mại nội bộ chỉ áp dụng giữa các chi nhánh với nhau.");
            }
        }

        // Validate: INTERNAL_SALE bắt buộc mỗi dòng phải có unitTransferPrice > 0
        if (businessType == TransferBusinessType.INTERNAL_SALE) {
            for (TransferItemRequest itemReq : req.getItems()) {
                if (itemReq.getUnitTransferPrice() == null
                        || itemReq.getUnitTransferPrice().compareTo(BigDecimal.ZERO) <= 0) {
                    throw new RuntimeException(
                            "Phiếu bán nội bộ yêu cầu đơn giá điều chuyển > 0 cho từng mặt hàng (SKU: "
                                    + itemReq.getSku() + ")");
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
                .settlementStatus(
                        businessType == TransferBusinessType.INTERNAL_SALE ? TransferSettlementStatus.UNPAID : null)
                .build();

        List<InventoryTransferDetail> details = new ArrayList<>();
        int totalQty = 0;
        BigDecimal totalValue = BigDecimal.ZERO; // Tổng theo giá vốn FIFO (quản trị kho)
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
                    .totalTransferPrice(
                            businessType == TransferBusinessType.INTERNAL_SALE ? lineTotalTransferPrice : null)
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
            transfer.setDestPayableAmount(transferAmount); // Kho nhận: phải trả nội bộ
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

    // ==========================================
    // LUỒNG 4 & 5 – BƯỚC 2: DUYỆT (Admin duyệt)
    // - Flow 4 (STOCK_TRANSFER): PENDING → APPROVED  + Reserve kho nguồn
    // - Flow 5 (INTERNAL_SALE) : SOURCE_CONFIRMED → APPROVED + Validate giá điều chuyển
    // ==========================================
    @Transactional
    public void approveTransfer(Long transferId) {
        InventoryTransfer transfer = transferRepo.findById(transferId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phiếu điều chuyển"));

        boolean isFlow5 = transfer.getTransferBusinessType() == TransferBusinessType.INTERNAL_SALE;

        if (isFlow5) {
            // Flow 5: phải qua SOURCE_CONFIRMED trước
            if (transfer.getStatus() != InventoryTransferStatus.SOURCE_CONFIRMED) {
                throw new RuntimeException("Phiếu bán nội bộ phải được chi nhánh nguồn xác nhận trước khi Admin duyệt!");
            }
        } else {
            // Flow 4: từ PENDING
            if (transfer.getStatus() != InventoryTransferStatus.PENDING) {
                throw new RuntimeException("Chỉ có thể duyệt phiếu đang ở trạng thái Chờ duyệt!");
            }
            // Reserve kho tổng khi Admin duyệt (Flow 4)
            reserveSourceStock(transfer);
        }

        transfer.setStatus(InventoryTransferStatus.APPROVED);
        transferRepo.save(transfer);
    }

    // ==========================================
    // LUỒNG 5 – BƯỚC 2a: CHI NHÁNH NGUỒN XÁC NHẬN
    // PENDING → SOURCE_CONFIRMED + Reserve kho chi nhánh A
    // ==========================================
    @Transactional
    public void sourceConfirm(Long transferId) {
        InventoryTransfer transfer = transferRepo.findById(transferId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phiếu điều chuyển"));

        if (transfer.getTransferBusinessType() != TransferBusinessType.INTERNAL_SALE) {
            throw new RuntimeException("Chỉ phiếu bán nội bộ (INTERNAL_SALE) mới cần bước xác nhận của chi nhánh nguồn!");
        }
        if (transfer.getStatus() != InventoryTransferStatus.PENDING) {
            throw new RuntimeException("Chỉ có thể xác nhận phiếu đang ở trạng thái Chờ xác nhận (PENDING)!");
        }

        // Validate giá nội bộ (mỗi dòng phải có unitTransferPrice > 0)
        for (InventoryTransferDetail detail : transfer.getDetails()) {
            if (detail.getUnitTransferPrice() == null || detail.getUnitTransferPrice().compareTo(BigDecimal.ZERO) <= 0) {
                throw new RuntimeException("Phiếu bán nội bộ yêu cầu đơn giá điều chuyển > 0 (SKU: "
                        + detail.getProductVariant().getSku() + ")");
            }
        }

        // Kiểm tra và Reserve kho chi nhánh A
        reserveSourceStock(transfer);

        transfer.setStatus(InventoryTransferStatus.SOURCE_CONFIRMED);
        transferRepo.save(transfer);
    }

    // ==========================================
    // LUỒNG 4 & 5 – BƯỚC 3: XUẤT KHO (Đang vận chuyển)
    // APPROVED → SHIPPING: Trừ thực tế khỏi kho nguồn, giải phóng Reserve
    // ==========================================
    @Transactional
    public void approveAndShip(Long transferId) {
        InventoryTransfer transfer = transferRepo.findByIdWithDetails(transferId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phiếu điều chuyển"));

        if (transfer.getStatus() != InventoryTransferStatus.APPROVED) {
            throw new RuntimeException("Chỉ có thể xuất kho phiếu đã được duyệt (APPROVED)!");
        }

        Long fromBranchId = transfer.getFromBranch().getId();

        for (InventoryTransferDetail detail : transfer.getDetails()) {
            Long variantId = detail.getProductVariant().getId();
            int qtyToShip = Objects.requireNonNullElse(detail.getQuantity(), 0);
            if (qtyToShip <= 0) continue;

            // Trừ quantity thực tế theo FIFO + Giải phóng reservedQuantity
            int remaining = qtyToShip;
            List<Inventory> batches = inventoryRepo.findForUpdateFIFO(fromBranchId, variantId);
            for (Inventory batch : batches) {
                if (remaining <= 0) break;
                int available = Objects.requireNonNullElse(batch.getQuantity(), 0);
                if (available <= 0) continue;

                int deduct = Math.min(available, remaining);
                batch.setQuantity(available - deduct);

                // Giải phóng reservation tương ứng
                int reserved = Objects.requireNonNullElse(batch.getReservedQuantity(), 0);
                batch.setReservedQuantity(Math.max(0, reserved - deduct));

                inventoryRepo.save(batch);

                transactionRepo.save(InventoryTransaction.builder()
                        .type(TransactionType.TRANSFER_OUT)
                        .quantityChange(-deduct)
                        .newBalance(Objects.requireNonNullElse(batch.getQuantity(), 0)
                                + Objects.requireNonNullElse(batch.getDefectiveQuantity(), 0))
                        .referenceCode(transfer.getTransferCode())
                        .reason("Xuất điều chuyển (Phiếu: " + transfer.getTransferCode() + ")")
                        .createdAt(LocalDateTime.now())
                        .inventory(batch)
                        .build());

                remaining -= deduct;
            }

            if (remaining > 0) {
                throw new RuntimeException("Kho nguồn không đủ hàng để xuất cho SKU: "
                        + detail.getProductVariant().getSku());
            }
        }

        transfer.setStatus(InventoryTransferStatus.SHIPPING);
        transferRepo.save(transfer);
    }

    // ==========================================
    // LUỒNG 4 & 5 – BƯỚC 4: BẮT ĐẦU KIỂM HÀNG
    // SHIPPING → INSPECTING
    // ==========================================
    @Transactional
    public void startInspection(Long transferId) {
        InventoryTransfer transfer = transferRepo.findById(transferId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phiếu điều chuyển"));

        if (transfer.getStatus() != InventoryTransferStatus.SHIPPING) {
            throw new RuntimeException("Phiếu phải đang ở trạng thái Đang vận chuyển (SHIPPING) mới có thể bắt đầu kiểm hàng!");
        }

        transfer.setStatus(InventoryTransferStatus.INSPECTING);
        transferRepo.save(transfer);
    }

    // ==========================================
    // LUỒNG 4 & 5 – BƯỚC 5: HOÀN THÀNH NHẬN HÀNG (QC + Cập nhật tồn kho đích)
    // INSPECTING → COMPLETED
    // Flow 5 bổ sung: Ghi nhận công nợ nội bộ (B nợ A theo số lượng đã gửi đi × giá)
    // ==========================================
    @Transactional
    public void receiveTransfer(Long id, List<TransferQCRequest> qcItems) {
        InventoryTransfer transfer = transferRepo.findByIdWithDetails(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phiếu điều chuyển"));

        // Hỗ trợ cả SHIPPING (skip bước INSPECTING) lẫn INSPECTING (đúng flow mới)
        if (transfer.getStatus() != InventoryTransferStatus.INSPECTING
                && transfer.getStatus() != InventoryTransferStatus.SHIPPING) {
            throw new RuntimeException("Phiếu phải ở trạng thái Đang kiểm hàng (INSPECTING) hoặc Đang vận chuyển (SHIPPING) mới có thể xác nhận nhận hàng!");
        }

        if (transfer.getStatus() == InventoryTransferStatus.SHIPPING) {
            throw new RuntimeException("Vui lĂ²ng báº¯t Ä‘áº§u kiá»ƒm hĂ ng trÆ°á»›c khi xĂ¡c nháº­n nháº­n hĂ ng.");
        }
        if (qcItems == null || qcItems.isEmpty()) {
            throw new RuntimeException("Vui lĂ²ng nháº­p dá»¯ liá»‡u kiá»ƒm hĂ ng cho tá»«ng sáº£n pháº©m.");
        }

        Map<Long, InventoryTransferDetail> detailMap = transfer.getDetails().stream()
                .collect(Collectors.toMap(
                        d -> d.getProductVariant().getId(),
                        d -> d,
                        (existing, replacement) -> existing));

        Branch toBranch = transfer.getToBranch();

        for (TransferQCRequest qcItemReq : qcItems) {
            Long variantId = qcItemReq.getVariantId();
            int qtyReal     = Objects.requireNonNullElse(qcItemReq.getQuantityReal(), 0);
            int qtyAccepted = Objects.requireNonNullElse(qcItemReq.getQuantityAccepted(), 0);
            int qtyRejected = Objects.requireNonNullElse(qcItemReq.getQuantityRejected(), 0);
            String itemNote = qcItemReq.getNote();

            InventoryTransferDetail detail = detailMap.get(variantId);
            if (detail == null) {
                throw new RuntimeException("Sáº£n pháº©m QC khĂ´ng thuá»™c phiáº¿u Ä‘iá»u chuyá»ƒn: " + variantId);
            }

            int requestedQty = Objects.requireNonNullElse(detail.getQuantity(), 0);
            if (qtyReal > requestedQty) {
                throw new RuntimeException("Sá»‘ lÆ°á»£ng kiá»ƒm hĂ ng khĂ´ng Ä‘Æ°á»£c vÆ°á»£t quĂ¡ sá»‘ lÆ°á»£ng Ä‘iá»u chuyá»ƒn cho SKU: "
                        + detail.getProductVariant().getSku());
            }
            if (qtyAccepted + qtyRejected != qtyReal) {
                throw new RuntimeException("Sá»‘ lÆ°á»£ng Ä‘áº¡t + lá»—i/thiáº¿u pháº£i Ä‘Ăºng báº±ng tá»•ng thá»±c nháº­n cho SKU: "
                        + detail.getProductVariant().getSku());
            }
            if ((qtyRejected > 0 || qtyReal < requestedQty) && (itemNote == null || itemNote.isBlank())) {
                throw new RuntimeException("Vui lĂ²ng ghi chĂº lĂ½ do hĂ ng lá»—i/thiáº¿u cho SKU: "
                        + detail.getProductVariant().getSku());
            }

            detail.setQuantityReal(qtyReal);
            detail.setQuantityAccepted(qtyAccepted);
            detail.setQuantityRejected(qtyRejected);
            detail.setNote(itemNote);

            // Cộng kho nhận: hàng đạt vào quantity, hàng lỗi vào defectiveQuantity
            if (qtyAccepted > 0 || qtyRejected > 0) {
                addDestinationStock(transfer, toBranch, detail.getProductVariant(), qtyAccepted, qtyRejected);
                // Kích hoạt xử lý backorder cho hàng đạt
                if (qtyAccepted > 0) {
                    backorderService.fulfillBackordersOnStockReceive(toBranch.getId(), variantId, qtyAccepted);
                }
            }
        }

        // ── Flow 5 (INTERNAL_SALE): Ghi nhận công nợ nội bộ ─────────────────
        // B nợ A = Σ(quantityRequested × unitTransferPrice) cho mỗi dòng hàng.
        // Lưu ý: tính theo quantityRequested (số lượng A gửi đi), KHÔNG phải
        // quantityAccepted, vì A không chịu trách nhiệm về hàng hỏng trong vận chuyển.
        if (transfer.getTransferBusinessType() == TransferBusinessType.INTERNAL_SALE) {
            BigDecimal actualDebt = transfer.getDetails().stream()
                    .map(d -> {
                        BigDecimal unitPrice = d.getUnitTransferPrice() != null ? d.getUnitTransferPrice() : BigDecimal.ZERO;
                        int qty = Objects.requireNonNullElse(d.getQuantity(), 0); // quantityRequested (số lượng gửi đi)
                        return unitPrice.multiply(BigDecimal.valueOf(qty));
                    })
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            transfer.setTransferAmount(actualDebt);
            transfer.setSourceReceivableAmount(actualDebt); // A phải thu
            transfer.setDestPayableAmount(actualDebt);       // B phải trả
            transfer.setSettlementStatus(TransferSettlementStatus.UNPAID);
        }

        transfer.setStatus(InventoryTransferStatus.COMPLETED);
        transferRepo.save(transfer);
    }

    // ==========================================
    // HELPER: KIỂM TRA & RESERVE KHO NGUỒN
    // Được gọi tại bước APPROVED (Flow 4) hoặc SOURCE_CONFIRMED (Flow 5)
    // ==========================================
    private void reserveSourceStock(InventoryTransfer transfer) {
        Long fromBranchId = transfer.getFromBranch().getId();

        for (InventoryTransferDetail detail : transfer.getDetails()) {
            Long variantId = detail.getProductVariant().getId();
            int qtyNeeded  = Objects.requireNonNullElse(detail.getQuantity(), 0);
            if (qtyNeeded <= 0) continue;

            List<Inventory> batches = inventoryRepo.findForUpdateFIFO(fromBranchId, variantId);

            // Tổng khả dụng = quantity - reservedQuantity
            int totalAvailable = batches.stream()
                    .mapToInt(b -> Math.max(0,
                            Objects.requireNonNullElse(b.getQuantity(), 0)
                            - Objects.requireNonNullElse(b.getReservedQuantity(), 0)))
                    .sum();

            if (totalAvailable < qtyNeeded) {
                throw new RuntimeException(String.format(
                        "Kho nguồn không đủ tồn kho khả dụng để Reserve cho SKU %s. Cần: %d, Khả dụng: %d",
                        detail.getProductVariant().getSku(), qtyNeeded, totalAvailable));
            }

            // Reserve theo thứ tự FIFO
            int toReserve = qtyNeeded;
            for (Inventory batch : batches) {
                if (toReserve <= 0) break;
                int avail = Math.max(0,
                        Objects.requireNonNullElse(batch.getQuantity(), 0)
                        - Objects.requireNonNullElse(batch.getReservedQuantity(), 0));
                if (avail <= 0) continue;

                int take = Math.min(avail, toReserve);
                batch.setReservedQuantity(Objects.requireNonNullElse(batch.getReservedQuantity(), 0) + take);
                inventoryRepo.save(batch);
                toReserve -= take;
            }
        }
    }

    // ==========================================
    // HELPER: GIẢI PHÓNG RESERVE (dùng khi hủy phiếu đã được APPROVED/SOURCE_CONFIRMED)
    // ==========================================
    private void releaseReservedStock(InventoryTransfer transfer) {
        Long fromBranchId = transfer.getFromBranch().getId();
        for (InventoryTransferDetail detail : transfer.getDetails()) {
            Long variantId = detail.getProductVariant().getId();
            int qtyToRelease = Objects.requireNonNullElse(detail.getQuantity(), 0);
            if (qtyToRelease <= 0) continue;

            List<Inventory> batches = inventoryRepo.findForUpdateFIFO(fromBranchId, variantId);
            int toRelease = qtyToRelease;
            for (Inventory batch : batches) {
                if (toRelease <= 0) break;
                int reserved = Objects.requireNonNullElse(batch.getReservedQuantity(), 0);
                if (reserved <= 0) continue;
                int release = Math.min(reserved, toRelease);
                batch.setReservedQuantity(reserved - release);
                inventoryRepo.save(batch);
                toRelease -= release;
            }
        }
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
            updateSingleDestinationBatch(transfer, resolveSystemDefectBranch(), variant, 0, rejected);
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
        InventoryTransfer transfer = transferRepo.findByIdWithDetails(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phiếu điều chuyển ID: " + id));
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

        // Cho phép từ chối ở cả PENDING và SOURCE_CONFIRMED
        if (transfer.getStatus() != InventoryTransferStatus.PENDING
                && transfer.getStatus() != InventoryTransferStatus.SOURCE_CONFIRMED) {
            throw new RuntimeException("Chỉ có thể từ chối phiếu đang ở trạng thái Chờ duyệt hoặc Chờ xác nhận nguồn!");
        }
        // Giải phóng reservation nếu Branch A đã confirm
        if (transfer.getStatus() == InventoryTransferStatus.SOURCE_CONFIRMED) {
            releaseReservedStock(transfer);
        }
        transfer.setStatus(InventoryTransferStatus.REJECTED);
        transferRepo.save(transfer);
    }

    @Transactional
    public void cancelTransfer(Long id) {
        InventoryTransfer transfer = transferRepo.findById(id).orElseThrow();
        if (transfer.getStatus() == InventoryTransferStatus.COMPLETED
                || transfer.getStatus() == InventoryTransferStatus.CANCELLED) {
            throw new RuntimeException("Không thể hủy phiếu đã hoàn thành hoặc đã hủy!");
        }
        if (transfer.getStatus() == InventoryTransferStatus.SHIPPING
                || transfer.getStatus() == InventoryTransferStatus.INSPECTING) {
            throw new RuntimeException("Không thể hủy phiếu đang vận chuyển hoặc đang kiểm hàng. Vui lòng liên hệ Admin.");
        }
        // Nếu đã Reserve kho (APPROVED hoặc SOURCE_CONFIRMED) thì phải giải phóng
        if (transfer.getStatus() == InventoryTransferStatus.APPROVED
                || transfer.getStatus() == InventoryTransferStatus.SOURCE_CONFIRMED) {
            releaseReservedStock(transfer);
        }
        transfer.setStatus(InventoryTransferStatus.CANCELLED);
        transferRepo.save(transfer);
    }

    @Transactional
    public void changeDestination(Long id, Long newBranchId) {
        InventoryTransfer transfer = transferRepo.findByIdWithDetails(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phiếu điều chuyển"));
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
    // MERGE HÀNG VÀO PHIẾU ĐANG PENDING (GOM ĐƠN)
    // ==========================================

    /**
     * Gộp thêm hàng hóa ({sku → qty}) vào phiếu điều chuyển đang PENDING.
     * <p>
     * Quy tắc:
     * - Nếu SKU đã có trong phiếu → cộng thêm quantity
     * - Nếu SKU chưa có → thêm dòng detail mới
     * - Cập nhật lại totalQuantity và totalValue (tính theo giá vốn FIFO kho xuất)
     */
    @Transactional
    public void mergeItemsIntoTransfer(InventoryTransfer transfer,
            Map<String, Integer> skuQuantities,
            String itemNote) {
        if (skuQuantities == null || skuQuantities.isEmpty())
            return;

        Long fromBranchId = transfer.getFromBranch().getId();
        BigDecimal addedValue = BigDecimal.ZERO;

        for (Map.Entry<String, Integer> entry : skuQuantities.entrySet()) {
            String sku = entry.getKey();
            int addQty = entry.getValue();
            if (addQty <= 0)
                continue;

            ProductVariant variant = variantRepo.findBySku(sku).orElse(null);
            if (variant == null)
                continue;

            // Tìm dòng chi tiết hiện tại trong phiếu
            com.zone.agri.entity.InventoryTransferDetail existing = transferDetailRepo
                    .findByInventoryTransferIdAndProductVariantId(
                            transfer.getId(), variant.getId())
                    .orElse(null);

            if (existing != null) {
                existing.setQuantity(existing.getQuantity() + addQty);
                existing.setQuantityRequested(
                        Objects.requireNonNullElse(existing.getQuantityRequested(), existing.getQuantity()) + addQty);
                transferDetailRepo.save(existing);
            } else {
                com.zone.agri.entity.InventoryTransferDetail newDetail = com.zone.agri.entity.InventoryTransferDetail
                        .builder()
                        .inventoryTransfer(transfer)
                        .productVariant(variant)
                        .quantity(addQty)
                        .quantityRequested(addQty)
                        .quantityReal(0)
                        .note(itemNote)
                        .build();
                transferDetailRepo.save(newDetail);
            }

            // Cộng thêm giá trị FIFO của lô hàng mới vào totalValue
            addedValue = addedValue.add(estimateFifoValue(fromBranchId, variant.getId(), addQty));
        }

        // Cập nhật totals trực tiếp trên transfer
        int newTotalQty = transferDetailRepo.findByInventoryTransferId(transfer.getId())
                .stream().mapToInt(d -> Objects.requireNonNullElse(d.getQuantity(), 0)).sum();
        transfer.setTotalQuantity(newTotalQty);
        transfer.setTotalValue(Objects.requireNonNullElse(transfer.getTotalValue(), BigDecimal.ZERO).add(addedValue));
        transferRepo.save(transfer);

        log.info("Merged {} SKU(s) into transfer {} (total qty now: {})",
                skuQuantities.size(), transfer.getTransferCode(), newTotalQty);
    }

    /** Ước tính giá trị FIFO cho qty đơn vị của một variant tại kho xuất. */
    private BigDecimal estimateFifoValue(Long fromBranchId, Long variantId, int qty) {
        List<Inventory> batches = inventoryRepo.findByProductVariantId(variantId).stream()
                .filter(inv -> inv.getBranch().getId().equals(fromBranchId)
                        && Objects.requireNonNullElse(inv.getQuantity(), 0) > 0)
                .sorted(Comparator.comparing(Inventory::getId))
                .collect(Collectors.toList());

        int remaining = qty;
        BigDecimal value = BigDecimal.ZERO;
        for (Inventory batch : batches) {
            if (remaining <= 0)
                break;
            int take = Math.min(remaining, batch.getQuantity());
            BigDecimal price = batch.getImportPrice() != null ? batch.getImportPrice() : BigDecimal.ZERO;
            value = value.add(price.multiply(BigDecimal.valueOf(take)));
            remaining -= take;
        }
        return value;
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
