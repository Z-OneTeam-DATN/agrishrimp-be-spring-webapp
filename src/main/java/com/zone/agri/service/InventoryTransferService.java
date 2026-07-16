package com.zone.agri.service;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.zone.agri.dto.request.transfer.TransferItemRequest;
import com.zone.agri.dto.request.transfer.TransferQCRequest;
import com.zone.agri.dto.request.transfer.TransferRequest;
import com.zone.agri.dto.response.transfer.TransferDetailResponse;
import com.zone.agri.dto.response.transfer.TransferResponse;
import com.zone.agri.entity.Branch;
import com.zone.agri.entity.Inventory;
import com.zone.agri.entity.InventoryTransaction;
import com.zone.agri.entity.InventoryTransfer;
import com.zone.agri.entity.InventoryTransferDetail;
import com.zone.agri.entity.ProductVariant;
import com.zone.agri.entity.SubOrder;
import com.zone.agri.entity.SubOrderItem;
import com.zone.agri.entity.enums.BranchStatus;
import com.zone.agri.entity.enums.InventoryTransferStatus;
import com.zone.agri.entity.enums.OrderStatus;
import com.zone.agri.entity.enums.TransactionType;
import com.zone.agri.entity.enums.TransferBusinessType;
import com.zone.agri.entity.enums.TransferSettlementStatus;
import com.zone.agri.repository.BranchRepository;
import com.zone.agri.repository.InventoryRepository;
import com.zone.agri.repository.InventoryTransactionRepository;
import com.zone.agri.repository.InventoryTransferRepository;
import com.zone.agri.repository.ProductVariantRepository;
import com.zone.agri.repository.SubOrderRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class InventoryTransferService {
    private static final String SYSTEM_DEFECT_BRANCH_CODE = "SYSTEM_DEFECT";
    private static final String SYSTEM_DEFECT_BRANCH_PHONE = "SYS-DEFECT-01";
    private static final String AUTO_REPLENISHMENT_TRANSFER_TYPE = "ORDER_REPLENISHMENT";
    private static final boolean ENFORCE_SOURCE_STOCK_CHECK_ON_CREATE = false;

    private final InventoryTransferRepository transferRepo;
    private final BranchRepository branchRepo;
    private final ProductVariantRepository variantRepo;
    private final InventoryRepository inventoryRepo;
    private final InventoryTransactionRepository transactionRepo;
    private final SubOrderRepository subOrderRepo;
    private final BackorderService backorderService;
    private final com.zone.agri.repository.InventoryTransferDetailRepository transferDetailRepo;
    private final com.zone.agri.common.WarehouseContext warehouseContext;
    private final InventoryCheckGuardService inventoryCheckGuardService;
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(InventoryTransferService.class);

    private void assertTransferParticipantAccess(InventoryTransfer transfer) {
        Long allowedBranchId = warehouseContext.resolveWarehouseId();
        if (allowedBranchId == null) {
            return;
        }

        Long fromBranchId = transfer.getFromBranch() != null ? transfer.getFromBranch().getId() : null;
        Long toBranchId = transfer.getToBranch() != null ? transfer.getToBranch().getId() : null;
        if (!Objects.equals(allowedBranchId, fromBranchId) && !Objects.equals(allowedBranchId, toBranchId)) {
            throw new RuntimeException("Khong duoc phep xem phieu dieu chuyen khong lien quan toi chi nhanh cua ban.");
        }
    }

    private boolean isWarehouseBranch(Branch branch) {
        return branch != null
                && ((branch.getBranchType() != null
                        && normalizeText(branch.getBranchType()).contains("warehouse"))
                        || normalizeText(branch.getName()).contains("kho tong"));
    }

    private Branch resolveMainWarehouse() {
        Optional<Branch> warehouseByType = branchRepo.findAll().stream()
                .filter(this::isWarehouseBranch)
                .findFirst();
        if (warehouseByType.isPresent()) {
            return warehouseByType.get();
        }

        Optional<Branch> warehouseByNormalizedName = branchRepo.findAll().stream()
                .filter(branch -> normalizeText(branch.getName()).contains("kho tong"))
                .findFirst();
        if (warehouseByNormalizedName.isPresent()) {
            return warehouseByNormalizedName.get();
        }

        return branchRepo.findAll().stream()
                .filter(branch -> branch.getName() != null
                        && branch.getName().trim().toLowerCase().contains("kho tổng"))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Không tìm thấy kho tổng để điều chuyển bổ sung"));
    }

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
        Long fromBranchId = req.getFromBranchId();
        Long toBranchId = req.getToBranchId();
        inventoryCheckGuardService.assertNoOpenCheckForBranch(fromBranchId, "tạo phiếu điều chuyển");
        inventoryCheckGuardService.assertNoOpenCheckForBranch(toBranchId, "tạo phiếu điều chuyển");
        Branch fromBranch = branchRepo.findById(req.getFromBranchId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy Kho xuất"));
        Branch toBranch = branchRepo.findById(req.getToBranchId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy Kho nhận"));

        // Xác định loại nghiệp vụ (mặc định STOCK_TRANSFER nếu không truyền)
        TransferBusinessType businessType = TransferBusinessType.STOCK_TRANSFER;
        if ("INTERNAL_SALE".equalsIgnoreCase(req.getTransferBusinessType())) {
            businessType = TransferBusinessType.INTERNAL_SALE;
        }

        boolean fromWarehouse = isWarehouseBranch(fromBranch);
        boolean toWarehouse = isWarehouseBranch(toBranch);
        boolean autoReplenishmentTransfer = AUTO_REPLENISHMENT_TRANSFER_TYPE.equalsIgnoreCase(req.getTransferType());

        if (businessType == TransferBusinessType.STOCK_TRANSFER) {
            if (!fromWarehouse && !autoReplenishmentTransfer) {
                throw new RuntimeException("Luồng cấp phát nội bộ chỉ được xuất từ chi nhánh loại kho.");
            }
            if (toWarehouse) {
                throw new RuntimeException(
                        "Luồng cấp phát nội bộ phải chuyển tới chi nhánh nhận, không phải chi nhánh loại kho.");
            }
        } else {
            if (fromWarehouse || toWarehouse) {
                throw new RuntimeException("Luồng thương mại nội bộ chỉ áp dụng giữa các chi nhánh bán hàng với nhau.");
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
        subOrder = subOrderRepo.findByIdWithItems(subOrder.getId())
                .orElseThrow(() -> new RuntimeException("Khong tim thay phan don can dieu chuyen bo sung"));
        boolean hasMissingItems = subOrder.getItems() != null
                && subOrder.getItems().stream()
                        .anyMatch(item -> Objects.requireNonNullElse(item.getMissingQuantity(), 0) > 0);
        if (!hasMissingItems
                || (subOrder.getStatus() != OrderStatus.PENDING
                        && subOrder.getStatus() != OrderStatus.AWAITING_REPLENISHMENT)) {
            throw new RuntimeException("Chỉ có thể tạo điều chuyển bổ sung cho phần đơn đang chờ điều chuyển");
        }

        String referenceCode = subOrder.getOrder().getCode() + "-SUB-" + subOrder.getId();
        List<InventoryTransfer> existingTransfers = transferRepo.findByReferenceCodeAndStatusInOrderByCreatedAtDesc(
                referenceCode,
                List.of(InventoryTransferStatus.PENDING, InventoryTransferStatus.SHIPPING));
        if (!existingTransfers.isEmpty()) {
            return existingTransfers;
        }

        Branch warehouse = resolveMainWarehouse();
        if (shouldUseDistributedReplenishment()) {
            return createPlannedReplenishmentTransfers(subOrder, warehouse, referenceCode);
        }

        Branch toBranch = subOrder.getBranch();

        Map<String, Integer> transferItems = new LinkedHashMap<>();
        List<SubOrderItem> subOrderItems = subOrder.getItems() != null ? subOrder.getItems() : List.of();
        for (SubOrderItem item : subOrderItems) {
            int missingQty = Objects.requireNonNullElse(item.getMissingQuantity(), 0);
            if (missingQty <= 0 || item.getProductVariant() == null) {
                continue;
            }
            // Kiểm tra tồn kho kho tổng
            int warehouseQty = inventoryRepo
                    .findByProductVariantId(item.getProductVariant().getId()).stream()
                    .filter(inv -> inv.getBranch() != null
                            && inv.getBranch().getId().equals(warehouse.getId())
                            && Objects.requireNonNullElse(inv.getQuantity(), 0) > 0)
                    .mapToInt(inv -> Objects.requireNonNullElse(inv.getQuantity(), 0))
                    .sum();
            if (ENFORCE_SOURCE_STOCK_CHECK_ON_CREATE && warehouseQty < missingQty) {
                throw new RuntimeException(
                        "Kho tổng không đủ hàng để điều chuyển bổ sung cho SKU: " + item.getProductVariant().getSku());
            }
            transferItems.merge(item.getProductVariant().getSku(), missingQty, Integer::sum);
        }

        if (transferItems.isEmpty()) {
            throw new RuntimeException("Không có sản phẩm nào cần điều chuyển bổ sung từ kho tổng");
        }

        List<InventoryTransfer> transfers = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        TransferRequest request = new TransferRequest();
        request.setFromBranchId(warehouse.getId());
        request.setToBranchId(toBranch.getId());
        request.setTransferType(AUTO_REPLENISHMENT_TRANSFER_TYPE);
        request.setDescription(buildAutoReplenishmentDescription(subOrder));
        request.setReferenceCode(referenceCode);
        request.setPriority("HIGH");
        request.setTransferDate(now);
        request.setDeadline(now.plusDays(1));

        List<TransferItemRequest> requestItems = transferItems.entrySet().stream()
                .map(itemEntry -> {
                    TransferItemRequest itemRequest = new TransferItemRequest();
                    itemRequest.setSku(itemEntry.getKey());
                    itemRequest.setQuantity(itemEntry.getValue());
                    itemRequest.setItemNote("Tu dong bo sung cho phan don " + referenceCode);
                    return itemRequest;
                })
                .toList();

        request.setItems(requestItems);
        transfers.add(createTransfer(request));
        return transfers;
    }

    private boolean shouldUseDistributedReplenishment() {
        return true;
    }

    private List<InventoryTransfer> createPlannedReplenishmentTransfers(
            SubOrder subOrder,
            Branch warehouse,
            String referenceCode) {
        Branch toBranch = subOrder.getBranch();
        Map<Long, Map<String, Integer>> transferPlanBySourceBranch = buildAutoReplenishmentPlan(subOrder, warehouse);
        if (transferPlanBySourceBranch.isEmpty()) {
            throw new RuntimeException("Khong co san pham nao can dieu chuyen bo sung");
        }

        List<InventoryTransfer> transfers = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (Map.Entry<Long, Map<String, Integer>> sourcePlan : transferPlanBySourceBranch.entrySet()) {
            TransferRequest request = new TransferRequest();
            request.setFromBranchId(sourcePlan.getKey());
            request.setToBranchId(toBranch.getId());
            request.setTransferType(AUTO_REPLENISHMENT_TRANSFER_TYPE);
            request.setDescription(buildAutoReplenishmentDescription(subOrder));
            request.setReferenceCode(referenceCode);
            request.setPriority("HIGH");
            request.setTransferDate(now);
            request.setDeadline(now.plusDays(1));
            request.setItems(sourcePlan.getValue().entrySet().stream()
                    .map(itemEntry -> {
                        TransferItemRequest itemRequest = new TransferItemRequest();
                        itemRequest.setSku(itemEntry.getKey());
                        itemRequest.setQuantity(itemEntry.getValue());
                        itemRequest.setItemNote("Tu dong bo sung cho phan don " + referenceCode);
                        return itemRequest;
                    })
                    .toList());
            transfers.add(createTransfer(request));
        }
        return transfers;
    }

    private Map<Long, Map<String, Integer>> buildAutoReplenishmentPlan(SubOrder subOrder, Branch warehouse) {
        Map<Long, Map<String, Integer>> transferPlanBySourceBranch = new LinkedHashMap<>();
        Branch toBranch = subOrder.getBranch();
        Long toBranchId = toBranch != null ? toBranch.getId() : null;
        Long warehouseId = warehouse != null ? warehouse.getId() : null;

        List<SubOrderItem> subOrderItems = subOrder.getItems() != null ? subOrder.getItems() : List.of();
        for (SubOrderItem item : subOrderItems) {
            int remainingMissing = Objects.requireNonNullElse(item.getMissingQuantity(), 0);
            if (remainingMissing <= 0 || item.getProductVariant() == null || item.getProductVariant().getId() == null) {
                continue;
            }

            String sku = item.getProductVariant().getSku();
            if (sku == null || sku.isBlank()) {
                continue;
            }

            for (SourceBranchCandidate candidate : loadNearestSellableSourceCandidates(
                    toBranch,
                    item.getProductVariant().getId())) {
                if (remainingMissing <= 0) {
                    break;
                }

                int quantityToMove = Math.min(remainingMissing, candidate.availableQuantity());
                if (quantityToMove <= 0) {
                    continue;
                }

                transferPlanBySourceBranch
                        .computeIfAbsent(candidate.branchId(), ignored -> new LinkedHashMap<>())
                        .merge(sku, quantityToMove, Integer::sum);
                remainingMissing -= quantityToMove;
            }

            if (remainingMissing > 0 && warehouseId != null && !Objects.equals(warehouseId, toBranchId)) {
                if (ENFORCE_SOURCE_STOCK_CHECK_ON_CREATE
                        && resolveAvailableQuantityAtBranch(item.getProductVariant().getId(), warehouseId) < remainingMissing) {
                    throw new RuntimeException("Kho tong khong du hang de dieu chuyen bo sung cho SKU: " + sku);
                }
                transferPlanBySourceBranch
                        .computeIfAbsent(warehouseId, ignored -> new LinkedHashMap<>())
                        .merge(sku, remainingMissing, Integer::sum);
            }
        }

        return transferPlanBySourceBranch;
    }

    private List<SourceBranchCandidate> loadNearestSellableSourceCandidates(Branch toBranch, Long variantId) {
        if (toBranch == null || toBranch.getId() == null || variantId == null) {
            return List.of();
        }

        Map<Long, Integer> availableByBranch = new LinkedHashMap<>();
        Map<Long, Branch> branchById = new LinkedHashMap<>();

        for (Inventory inventory : inventoryRepo.findByProductVariantId(variantId)) {
            Branch sourceBranch = inventory.getBranch();
            Long sourceBranchId = sourceBranch != null ? sourceBranch.getId() : null;
            int availableQty = Objects.requireNonNullElse(inventory.getQuantity(), 0);

            if (sourceBranchId == null
                    || Objects.equals(sourceBranchId, toBranch.getId())
                    || isWarehouseBranch(sourceBranch)
                    || availableQty <= 0) {
                continue;
            }

            availableByBranch.merge(sourceBranchId, availableQty, Integer::sum);
            branchById.putIfAbsent(sourceBranchId, sourceBranch);
        }

        return availableByBranch.entrySet().stream()
                .map(entry -> new SourceBranchCandidate(
                        entry.getKey(),
                        entry.getValue(),
                        calculateHaversineDistance(
                                toBranch.getLat(),
                                toBranch.getLng(),
                                branchById.get(entry.getKey()) != null ? branchById.get(entry.getKey()).getLat() : null,
                                branchById.get(entry.getKey()) != null ? branchById.get(entry.getKey()).getLng() : null)))
                .sorted(Comparator
                        .comparingDouble(SourceBranchCandidate::distanceKm)
                        .thenComparing(SourceBranchCandidate::branchId))
                .toList();
    }

    private int resolveAvailableQuantityAtBranch(Long variantId, Long branchId) {
        if (variantId == null || branchId == null) {
            return 0;
        }

        return inventoryRepo.findByProductVariantId(variantId).stream()
                .filter(inv -> inv.getBranch() != null && Objects.equals(inv.getBranch().getId(), branchId))
                .mapToInt(inv -> Objects.requireNonNullElse(inv.getQuantity(), 0))
                .sum();
    }

    private record SourceBranchCandidate(Long branchId, int availableQuantity, double distanceKm) {
    }

    // ==========================================
    // LUỒNG 4 & 5 – BƯỚC 2: DUYỆT (Admin duyệt)
    // - Flow 4 (STOCK_TRANSFER): PENDING → APPROVED + Reserve kho nguồn
    // - Flow 5 (INTERNAL_SALE) : SOURCE_CONFIRMED → APPROVED + Validate giá điều
    // chuyển
    // ==========================================
    @Transactional
    public void approveTransfer(Long transferId) {
        InventoryTransfer transfer = transferRepo.findById(transferId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phiếu điều chuyển"));
        inventoryCheckGuardService.assertNoOpenCheckForBranch(transfer.getFromBranch().getId(), "duyet phieu dieu chuyen");
        inventoryCheckGuardService.assertNoOpenCheckForBranch(transfer.getToBranch().getId(), "duyet phieu dieu chuyen");

        boolean isFlow5 = transfer.getTransferBusinessType() == TransferBusinessType.INTERNAL_SALE;

        if (isFlow5) {
            // Flow 5: phải qua SOURCE_CONFIRMED trước
            if (transfer.getStatus() != InventoryTransferStatus.SOURCE_CONFIRMED) {
                throw new RuntimeException(
                        "Phiếu bán nội bộ phải được chi nhánh nguồn xác nhận trước khi Admin duyệt!");
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
        inventoryCheckGuardService.assertNoOpenCheckForBranch(transfer.getFromBranch().getId(), "xac nhan dieu chuyen");
        inventoryCheckGuardService.assertNoOpenCheckForBranch(transfer.getToBranch().getId(), "xac nhan dieu chuyen");

        if (transfer.getTransferBusinessType() != TransferBusinessType.INTERNAL_SALE) {
            throw new RuntimeException(
                    "Chỉ phiếu bán nội bộ (INTERNAL_SALE) mới cần bước xác nhận của chi nhánh nguồn!");
        }
        if (transfer.getStatus() != InventoryTransferStatus.PENDING) {
            throw new RuntimeException("Chỉ có thể xác nhận phiếu đang ở trạng thái Chờ xác nhận (PENDING)!");
        }

        // Validate giá nội bộ (mỗi dòng phải có unitTransferPrice > 0)
        for (InventoryTransferDetail detail : transfer.getDetails()) {
            if (detail.getUnitTransferPrice() == null
                    || detail.getUnitTransferPrice().compareTo(BigDecimal.ZERO) <= 0) {
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
        inventoryCheckGuardService.assertNoOpenCheckForBranch(transfer.getFromBranch().getId(), "xuat dieu chuyen");
        inventoryCheckGuardService.assertNoOpenCheckForBranch(transfer.getToBranch().getId(), "xuat dieu chuyen");

        if (transfer.getStatus() != InventoryTransferStatus.APPROVED) {
            throw new RuntimeException("Chỉ có thể xuất kho phiếu đã được duyệt (APPROVED)!");
        }

        Long fromBranchId = transfer.getFromBranch().getId();

        for (InventoryTransferDetail detail : transfer.getDetails()) {
            Long variantId = detail.getProductVariant().getId();
            int qtyToShip = Objects.requireNonNullElse(detail.getQuantity(), 0);
            if (qtyToShip <= 0)
                continue;

            // Trừ quantity thực tế theo FIFO + Giải phóng reservedQuantity
            int remaining = qtyToShip;
            List<Inventory> batches = inventoryRepo.findForUpdateFIFO(fromBranchId, variantId);
            for (Inventory batch : batches) {
                if (remaining <= 0)
                    break;
                int available = Objects.requireNonNullElse(batch.getQuantity(), 0);
                if (available <= 0)
                    continue;

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
        inventoryCheckGuardService.assertNoOpenCheckForBranch(transfer.getFromBranch().getId(), "kiem tra dieu chuyen");
        inventoryCheckGuardService.assertNoOpenCheckForBranch(transfer.getToBranch().getId(), "kiem tra dieu chuyen");

        if (transfer.getStatus() != InventoryTransferStatus.SHIPPING) {
            throw new RuntimeException(
                    "Phiếu phải đang ở trạng thái Đang vận chuyển (SHIPPING) mới có thể bắt đầu kiểm hàng!");
        }

        transfer.setStatus(InventoryTransferStatus.INSPECTING);
        transferRepo.save(transfer);
    }

    // ==========================================
    // LUỒNG 4 & 5 – BƯỚC 5: HOÀN THÀNH NHẬN HÀNG (QC + Cập nhật tồn kho đích)
    // INSPECTING → COMPLETED
    // Flow 5 bổ sung: Ghi nhận công nợ nội bộ (B nợ A theo số lượng đã gửi đi ×
    // giá)
    // ==========================================
    @Transactional
    public void receiveTransfer(Long id, List<TransferQCRequest> qcItems) {
        InventoryTransfer transfer = transferRepo.findByIdWithDetails(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phiếu điều chuyển"));
        inventoryCheckGuardService.assertNoOpenCheckForBranch(transfer.getFromBranch().getId(), "nhan dieu chuyen");
        inventoryCheckGuardService.assertNoOpenCheckForBranch(transfer.getToBranch().getId(), "nhan dieu chuyen");

        // Hỗ trợ cả SHIPPING (skip bước INSPECTING) lẫn INSPECTING (đúng flow mới)
        if (transfer.getStatus() != InventoryTransferStatus.INSPECTING
                && transfer.getStatus() != InventoryTransferStatus.SHIPPING) {
            throw new RuntimeException(
                    "Phiếu phải ở trạng thái Đang kiểm hàng (INSPECTING) hoặc Đang vận chuyển (SHIPPING) mới có thể xác nhận nhận hàng!");
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
            int qtyReal = Objects.requireNonNullElse(qcItemReq.getQuantityReal(), 0);
            int qtyAccepted = Objects.requireNonNullElse(qcItemReq.getQuantityAccepted(), 0);
            int qtyRejected = Objects.requireNonNullElse(qcItemReq.getQuantityRejected(), 0);
            String itemNote = qcItemReq.getNote();

            InventoryTransferDetail detail = detailMap.get(variantId);
            if (detail == null) {
                throw new RuntimeException("Sáº£n pháº©m QC khĂ´ng thuá»™c phiáº¿u Ä‘iá»u chuyá»ƒn: " + variantId);
            }

            int requestedQty = Objects.requireNonNullElse(detail.getQuantity(), 0);
            if (qtyReal > requestedQty) {
                throw new RuntimeException(
                        "Sá»‘ lÆ°á»£ng kiá»ƒm hĂ ng khĂ´ng Ä‘Æ°á»£c vÆ°á»£t quĂ¡ sá»‘ lÆ°á»£ng Ä‘iá»u chuyá»ƒn cho SKU: "
                                + detail.getProductVariant().getSku());
            }
            if (qtyAccepted + qtyRejected != qtyReal) {
                throw new RuntimeException(
                        "Sá»‘ lÆ°á»£ng Ä‘áº¡t + lá»—i/thiáº¿u pháº£i Ä‘Ăºng báº±ng tá»•ng thá»±c nháº­n cho SKU: "
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
                addDestinationStock(transfer, detail, toBranch, qtyAccepted, qtyRejected);
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
                        BigDecimal unitPrice = d.getUnitTransferPrice() != null ? d.getUnitTransferPrice()
                                : BigDecimal.ZERO;
                        int qty = Objects.requireNonNullElse(d.getQuantity(), 0); // quantityRequested (số lượng gửi đi)
                        return unitPrice.multiply(BigDecimal.valueOf(qty));
                    })
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            transfer.setTransferAmount(actualDebt);
            transfer.setSourceReceivableAmount(actualDebt); // A phải thu
            transfer.setDestPayableAmount(actualDebt); // B phải trả
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
            int qtyNeeded = Objects.requireNonNullElse(detail.getQuantity(), 0);
            if (qtyNeeded <= 0)
                continue;

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
                if (toReserve <= 0)
                    break;
                int avail = Math.max(0,
                        Objects.requireNonNullElse(batch.getQuantity(), 0)
                                - Objects.requireNonNullElse(batch.getReservedQuantity(), 0));
                if (avail <= 0)
                    continue;

                int take = Math.min(avail, toReserve);
                batch.setReservedQuantity(Objects.requireNonNullElse(batch.getReservedQuantity(), 0) + take);
                inventoryRepo.save(batch);
                toReserve -= take;
            }
        }
    }

    // ==========================================
    // HELPER: GIẢI PHÓNG RESERVE (dùng khi hủy phiếu đã được
    // APPROVED/SOURCE_CONFIRMED)
    // ==========================================
    private void releaseReservedStock(InventoryTransfer transfer) {
        Long fromBranchId = transfer.getFromBranch().getId();
        for (InventoryTransferDetail detail : transfer.getDetails()) {
            Long variantId = detail.getProductVariant().getId();
            int qtyToRelease = Objects.requireNonNullElse(detail.getQuantity(), 0);
            if (qtyToRelease <= 0)
                continue;

            List<Inventory> batches = inventoryRepo.findForUpdateFIFO(fromBranchId, variantId);
            int toRelease = qtyToRelease;
            for (Inventory batch : batches) {
                if (toRelease <= 0)
                    break;
                int reserved = Objects.requireNonNullElse(batch.getReservedQuantity(), 0);
                if (reserved <= 0)
                    continue;
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
        throw new UnsupportedOperationException("Use addDestinationStock with transfer detail to preserve batch cost.");
    }

    private void addDestinationStock(InventoryTransfer transfer, InventoryTransferDetail detail, Branch toBranch,
            int accepted, int rejected) {
        int totalReceived = accepted + rejected;
        if (totalReceived <= 0) {
            return;
        }

        List<TransferInboundAllocation> allocations = resolveInboundAllocations(transfer, detail, totalReceived);
        Branch defectBranch = rejected > 0 ? resolveSystemDefectBranch() : null;
        int remainingAccepted = accepted;
        int remainingRejected = rejected;

        for (TransferInboundAllocation allocation : allocations) {
            int availableInAllocation = allocation.quantity();

            if (remainingAccepted > 0) {
                int acceptedQty = Math.min(remainingAccepted, availableInAllocation);
                if (acceptedQty > 0) {
                    updateSingleDestinationBatch(transfer, toBranch, detail.getProductVariant(), acceptedQty, 0, allocation);
                    remainingAccepted -= acceptedQty;
                    availableInAllocation -= acceptedQty;
                }
            }

            if (remainingRejected > 0 && availableInAllocation > 0 && defectBranch != null) {
                int rejectedQty = Math.min(remainingRejected, availableInAllocation);
                if (rejectedQty > 0) {
                    updateSingleDestinationBatch(transfer, defectBranch, detail.getProductVariant(), 0, rejectedQty, allocation);
                    remainingRejected -= rejectedQty;
                }
            }
        }
    }

    private void updateSingleDestinationBatch(InventoryTransfer transfer, Branch branch, ProductVariant variant,
            int accepted, int rejected, TransferInboundAllocation allocation) {
        String batchNumber = allocation.batchNumber() != null && !allocation.batchNumber().isBlank()
                ? allocation.batchNumber()
                : "TRANSFER-" + transfer.getTransferCode();
        BigDecimal importPrice = allocation.importPrice() != null ? allocation.importPrice() : BigDecimal.ZERO;

        Inventory inv = inventoryRepo.findExactBatchWithLock(branch, variant, batchNumber, importPrice)
                .orElseGet(() -> inventoryRepo.save(Inventory.builder()
                        .branch(branch)
                        .productVariant(variant)
                        .batchNumber(batchNumber)
                        .importPrice(importPrice)
                        .expiryDate(allocation.expiryDate())
                        .lastReceiptDate(LocalDateTime.now())
                        .quantity(0)
                        .defectiveQuantity(0)
                        .build()));

        inv.setQuantity(Objects.requireNonNullElse(inv.getQuantity(), 0) + accepted);
        inv.setDefectiveQuantity(Objects.requireNonNullElse(inv.getDefectiveQuantity(), 0) + rejected);
        if (inv.getExpiryDate() == null && allocation.expiryDate() != null) {
            inv.setExpiryDate(allocation.expiryDate());
        }
        inv.setLastReceiptDate(LocalDateTime.now());
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

    private List<TransferInboundAllocation> resolveInboundAllocations(InventoryTransfer transfer, InventoryTransferDetail detail,
            int totalReceived) {
        List<InventoryTransaction> sourceTransactions = transactionRepo
                .findByReferenceCodeAndType(transfer.getTransferCode(), TransactionType.TRANSFER_OUT)
                .stream()
                .filter(tx -> tx.getInventory() != null
                        && tx.getInventory().getProductVariant() != null
                        && Objects.equals(tx.getInventory().getProductVariant().getId(), detail.getProductVariant().getId()))
                .sorted(Comparator
                        .comparing(InventoryTransaction::getCreatedAt, Comparator.nullsLast(LocalDateTime::compareTo))
                        .thenComparing(InventoryTransaction::getId, Comparator.nullsLast(Long::compareTo)))
                .toList();

        List<TransferInboundAllocation> allocations = new ArrayList<>();
        int remaining = totalReceived;

        for (InventoryTransaction transaction : sourceTransactions) {
            if (remaining <= 0) {
                break;
            }

            int movedQuantity = Math.abs(Objects.requireNonNullElse(transaction.getQuantityChange(), 0));
            if (movedQuantity <= 0 || transaction.getInventory() == null) {
                continue;
            }

            Inventory sourceBatch = transaction.getInventory();
            int allocatedQuantity = Math.min(remaining, movedQuantity);
            BigDecimal inboundImportPrice = resolveInboundImportPrice(transfer, detail, sourceBatch);

            allocations.add(new TransferInboundAllocation(
                    sourceBatch.getBatchNumber(),
                    inboundImportPrice,
                    sourceBatch.getExpiryDate(),
                    allocatedQuantity));
            remaining -= allocatedQuantity;
        }

        if (remaining > 0) {
            allocations.add(new TransferInboundAllocation(
                    "TRANSFER-" + transfer.getTransferCode(),
                    resolveFallbackInboundImportPrice(transfer, detail),
                    null,
                    remaining));
        }

        return allocations;
    }

    private BigDecimal resolveInboundImportPrice(InventoryTransfer transfer, InventoryTransferDetail detail, Inventory sourceBatch) {
        if (transfer.getTransferBusinessType() == TransferBusinessType.INTERNAL_SALE) {
            return detail.getUnitTransferPrice() != null
                    ? detail.getUnitTransferPrice()
                    : Objects.requireNonNullElse(sourceBatch.getImportPrice(), BigDecimal.ZERO);
        }
        return Objects.requireNonNullElse(sourceBatch.getImportPrice(), BigDecimal.ZERO);
    }

    private BigDecimal resolveFallbackInboundImportPrice(InventoryTransfer transfer, InventoryTransferDetail detail) {
        if (transfer.getTransferBusinessType() == TransferBusinessType.INTERNAL_SALE
                && detail.getUnitTransferPrice() != null) {
            return detail.getUnitTransferPrice();
        }
        return BigDecimal.ZERO;
    }

    private record TransferInboundAllocation(String batchNumber, BigDecimal importPrice, LocalDateTime expiryDate,
            int quantity) {
    }

    // ==========================================
    // HÀM LẤY CHI TIẾT
    // ==========================================
    public TransferDetailResponse getById(Long id) {
        InventoryTransfer transfer = transferRepo.findByIdWithDetails(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phiếu điều chuyển ID: " + id));
        assertTransferParticipantAccess(transfer);
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
        Long branchId = warehouseContext.resolveWarehouseId();
        return branchId == null
                ? transferRepo.searchTransfers(keyword, status, pageable)
                : transferRepo.searchTransfersForBranch(keyword, status, branchId, pageable);
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
            throw new RuntimeException(
                    "Không thể hủy phiếu đang vận chuyển hoặc đang kiểm hàng. Vui lòng liên hệ Admin.");
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
                t.getTransferType(),
                t.getStatus(),
                t.getCreatedAt(),
                t.getTransferDate(),
                t.getDeadline(),
                t.getFromBranch() != null ? t.getFromBranch().getName() : "N/A",
                t.getToBranch() != null ? t.getToBranch().getName() : "N/A",
                t.getTransporter(),
                t.getReferenceCode(),
                t.getDescription(),
                t.getPriority(),
                t.getTotalQuantity(),
                t.getDetails() != null ? t.getDetails().size() : 0,
                t.getTotalValue(),
                t.getTransferBusinessType(),
                t.getSettlementStatus(),
                t.getTransferAmount());
    }

    private String buildAutoReplenishmentDescription(SubOrder subOrder) {
        String orderCode = subOrder.getOrder() != null ? subOrder.getOrder().getCode() : "N/A";
        String branchName = subOrder.getBranch() != null ? subOrder.getBranch().getName() : "chi nhanh nhan";
        return "Tu dong tao tu don thieu hang " + orderCode + " cho " + branchName;
    }

    private String normalizeText(String value) {
        if (value == null) {
            return "";
        }

        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replace('đ', 'd')
                .replace('Đ', 'D');
        return normalized.toLowerCase(Locale.ROOT).trim();
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

