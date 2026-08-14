package com.zone.agri.service;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.zone.agri.common.AuthUtils;
import com.zone.agri.common.RoleUtils;
import com.zone.agri.dto.request.transfer.TransferItemRequest;
import com.zone.agri.dto.request.transfer.TransferQCRequest;
import com.zone.agri.dto.request.transfer.TransferRequest;
import com.zone.agri.dto.request.transfer.TransferSettlementRequest;
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
import com.zone.agri.entity.User;
import com.zone.agri.entity.enums.BranchStatus;
import com.zone.agri.entity.enums.InventoryTransferStatus;
import com.zone.agri.entity.enums.OrderStatus;
import com.zone.agri.entity.enums.TransactionType;
import com.zone.agri.entity.enums.TransferBusinessType;
import com.zone.agri.entity.enums.TransferSettlementStatus;
import com.zone.agri.exception.BadRequestException;
import com.zone.agri.exception.Forbidden;
import com.zone.agri.exception.NotFoundException;
import com.zone.agri.repository.BranchRepository;
import com.zone.agri.repository.InventoryRepository;
import com.zone.agri.repository.InventoryTransactionRepository;
import com.zone.agri.repository.InventoryTransferRepository;
import com.zone.agri.repository.ProductVariantRepository;
import com.zone.agri.repository.SubOrderRepository;
import com.zone.agri.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class InventoryTransferService {
    private static final String SYSTEM_DEFECT_BRANCH_CODE = "SYSTEM_DEFECT";
    private static final String SYSTEM_DEFECT_BRANCH_PHONE = "SYS-DEFECT-01";
    private static final String AUTO_REPLENISHMENT_TRANSFER_TYPE = "ORDER_REPLENISHMENT";
    private static final boolean ENFORCE_SOURCE_STOCK_CHECK_ON_CREATE = false;
    private static final List<InventoryTransferStatus> ACTIVE_REPLENISHMENT_TRANSFER_STATUSES = List.of(
            InventoryTransferStatus.PENDING,
            InventoryTransferStatus.SOURCE_CONFIRMED,
            InventoryTransferStatus.APPROVED,
            InventoryTransferStatus.SHIPPING,
            InventoryTransferStatus.INSPECTING);
    private static final List<InventoryTransferStatus> RESERVATION_HOLDER_STATUSES = List.of(
            InventoryTransferStatus.SOURCE_CONFIRMED,
            InventoryTransferStatus.APPROVED);

    public enum ReplenishmentDecisionType {
        REGULAR_BRANCH_TRANSFER,
        MAIN_WAREHOUSE_TRANSFER,
        PURCHASE_REQUEST
    }

    public record ReplenishmentDecision(
            ReplenishmentDecisionType type,
            Branch sourceBranch,
            Branch destinationBranch,
            String referenceCode,
            Map<String, Integer> itemQuantities) {
    }

    public record ReplenishmentCreationResult(
            List<InventoryTransfer> transfers,
            Map<Long, Integer> uncoveredQuantitiesByVariantId) {
    }

    private record MissingItemRequirement(
            Long variantId,
            String sku,
            int quantity) {
    }

    private final InventoryTransferRepository transferRepo;
    private final BranchRepository branchRepo;
    private final ProductVariantRepository variantRepo;
    private final InventoryRepository inventoryRepo;
    private final InventoryTransactionRepository transactionRepo;
    private final SubOrderRepository subOrderRepo;
    private final UserRepository userRepo;
    private final BackorderService backorderService;
    private final com.zone.agri.repository.InventoryTransferDetailRepository transferDetailRepo;
    private final com.zone.agri.common.WarehouseContext warehouseContext;
    private final InventoryCheckGuardService inventoryCheckGuardService;
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(InventoryTransferService.class);

    private boolean hasAuthority(String authority) {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> authority.equals(a.getAuthority()));
    }

    private boolean canApproveTransfersAcrossBranches() {
        return hasAuthority("TRANSFER_APPROVE")
                && RoleUtils.hasAdminLikeAuthority(AuthUtils.getAuthorities());
    }

    private void assertTransferParticipantAccess(InventoryTransfer transfer) {
        if (canApproveTransfersAcrossBranches()) {
            return;
        }

        Long allowedBranchId = warehouseContext.resolveWarehouseId();
        if (allowedBranchId == null) {
            return;
        }

        Long fromBranchId = transfer.getFromBranch() != null ? transfer.getFromBranch().getId() : null;
        Long toBranchId = transfer.getToBranch() != null ? transfer.getToBranch().getId() : null;
        if (!Objects.equals(allowedBranchId, fromBranchId) && !Objects.equals(allowedBranchId, toBranchId)) {
            throw new Forbidden("Không được phép xem phiếu điều chuyển không liên quan tới chi nhánh của bạn.");
        }
    }

    private boolean isWarehouseBranch(Branch branch) {
        return branch != null
                && ((branch.getBranchType() != null
                        && normalizeText(branch.getBranchType()).contains("warehouse"))
                        || normalizeText(branch.getName()).contains("kho tong"));
    }

    private boolean isAutoReplenishmentTransfer(InventoryTransfer transfer) {
        return transfer != null
                && AUTO_REPLENISHMENT_TRANSFER_TYPE.equalsIgnoreCase(transfer.getTransferType());
    }

    private boolean requiresSourceConfirmation(InventoryTransfer transfer) {
        return transfer != null
                && (transfer.getTransferBusinessType() == TransferBusinessType.INTERNAL_SALE
                        || (isAutoReplenishmentTransfer(transfer)
                                && !isWarehouseBranch(transfer.getFromBranch())));
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

    private User getCurrentUser() {
        com.zone.agri.dto.response.user.UserDetail userDetail = AuthUtils.getUserDetail();
        if (userDetail == null || userDetail.getId() == null) {
            throw new BadRequestException("Không xác định được người đang thao tác phiếu điều chuyển.");
        }
        return userRepo.findById(userDetail.getId())
                .orElseThrow(() -> new BadRequestException("Không tìm thấy thông tin người dùng hiện tại."));
    }

    private User findCurrentUserOrNull() {
        com.zone.agri.dto.response.user.UserDetail userDetail = AuthUtils.getUserDetail();
        if (userDetail == null || userDetail.getId() == null) {
            return null;
        }
        return userRepo.findById(userDetail.getId()).orElse(null);
    }

    private void assertBranchActor(User user, Branch branch, String action) {
        if (user == null || user.getBranch() == null || branch == null) {
            return;
        }
        if (!Objects.equals(user.getBranch().getId(), branch.getId())) {
            throw new Forbidden("Bạn không thuộc chi nhánh được phép " + action + " cho phiếu này.");
        }
    }

    private TransferBusinessType resolveBusinessType(String rawBusinessType) {
        return "INTERNAL_SALE".equalsIgnoreCase(rawBusinessType)
                ? TransferBusinessType.INTERNAL_SALE
                : TransferBusinessType.STOCK_TRANSFER;
    }

    private void validateTransferRequestBasics(
            TransferRequest req,
            Branch fromBranch,
            Branch toBranch,
            TransferBusinessType businessType) {
        if (req.getItems() == null || req.getItems().isEmpty()) {
            throw new BadRequestException("Phiếu điều chuyển phải có ít nhất một mặt hàng.");
        }
        if (Objects.equals(fromBranch.getId(), toBranch.getId())) {
            throw new BadRequestException("Chi nhánh nhận phải khác chi nhánh xuất.");
        }

        boolean fromWarehouse = isWarehouseBranch(fromBranch);
        boolean toWarehouse = isWarehouseBranch(toBranch);
        boolean autoReplenishmentTransfer = AUTO_REPLENISHMENT_TRANSFER_TYPE.equalsIgnoreCase(req.getTransferType());

        if (businessType == TransferBusinessType.STOCK_TRANSFER) {
            if (!fromWarehouse && !autoReplenishmentTransfer) {
                throw new BadRequestException("Luồng cấp phát nội bộ chỉ được xuất từ chi nhánh loại kho.");
            }
            if (toWarehouse && !autoReplenishmentTransfer) {
                throw new BadRequestException("Luồng cấp phát nội bộ phải chuyển tới chi nhánh nhận, không phải chi nhánh loại kho.");
            }
        } else if (fromWarehouse || toWarehouse) {
            throw new BadRequestException("Luồng thương mại nội bộ chỉ áp dụng giữa các chi nhánh bán hàng với nhau.");
        }

        Set<String> normalizedSkus = new HashSet<>();
        for (TransferItemRequest itemReq : req.getItems()) {
            String normalizedSku = itemReq.getSku() == null ? "" : itemReq.getSku().trim().toUpperCase(Locale.ROOT);
            if (normalizedSku.isBlank()) {
                throw new BadRequestException("SKU không được để trống.");
            }
            if (!normalizedSkus.add(normalizedSku)) {
                throw new BadRequestException("SKU " + itemReq.getSku() + " đang bị trùng trong phiếu điều chuyển.");
            }
            if (itemReq.getQuantity() == null || itemReq.getQuantity() <= 0) {
                throw new BadRequestException("Số lượng điều chuyển phải lớn hơn 0 cho SKU: " + itemReq.getSku());
            }
        }
    }

    private void validateInternalSalePricesForRequest(List<TransferItemRequest> items) {
        for (TransferItemRequest itemReq : items) {
            if (itemReq.getUnitTransferPrice() == null
                    || itemReq.getUnitTransferPrice().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BadRequestException(
                        "Phiếu bán nội bộ yêu cầu đơn giá điều chuyển > 0 cho từng mặt hàng (SKU: "
                                + itemReq.getSku() + ")");
            }
        }
    }

    private void validateInternalSalePricesForTransfer(InventoryTransfer transfer) {
        for (InventoryTransferDetail detail : transfer.getDetails()) {
            if (detail.getUnitTransferPrice() == null
                    || detail.getUnitTransferPrice().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BadRequestException("Phiếu bán nội bộ yêu cầu đơn giá điều chuyển > 0 (SKU: "
                        + detail.getProductVariant().getSku() + ")");
            }
        }
    }

    private TransferComputation buildTransferComputation(
            InventoryTransfer transfer,
            Branch fromBranch,
            TransferBusinessType businessType,
            List<TransferItemRequest> itemRequests) {
        List<InventoryTransferDetail> details = new ArrayList<>();
        int totalQty = 0;
        BigDecimal totalValue = BigDecimal.ZERO;
        BigDecimal transferAmount = BigDecimal.ZERO;

        for (TransferItemRequest itemReq : itemRequests) {
            ProductVariant variant = variantRepo.findBySku(itemReq.getSku())
                    .orElseThrow(() -> new BadRequestException("Sản phẩm với SKU " + itemReq.getSku() + " không tồn tại"));

            BigDecimal unitPrice = businessType == TransferBusinessType.INTERNAL_SALE
                    ? Objects.requireNonNullElse(itemReq.getUnitTransferPrice(), BigDecimal.ZERO)
                    : null;
            BigDecimal lineTotalTransferPrice = businessType == TransferBusinessType.INTERNAL_SALE
                    ? unitPrice.multiply(BigDecimal.valueOf(itemReq.getQuantity()))
                    : null;

            InventoryTransferDetail detail = InventoryTransferDetail.builder()
                    .inventoryTransfer(transfer)
                    .productVariant(variant)
                    .quantity(itemReq.getQuantity())
                    .quantityRequested(itemReq.getQuantity())
                    .quantityReal(0)
                    .quantityAccepted(0)
                    .quantityRejected(0)
                    .note(itemReq.getItemNote())
                    .unitTransferPrice(unitPrice)
                    .totalTransferPrice(lineTotalTransferPrice)
                    .build();

            details.add(detail);
            totalQty += itemReq.getQuantity();
            totalValue = totalValue.add(estimateFifoValue(fromBranch.getId(), variant.getId(), itemReq.getQuantity()));
            if (lineTotalTransferPrice != null) {
                transferAmount = transferAmount.add(lineTotalTransferPrice);
            }
        }

        return new TransferComputation(details, totalQty, totalValue, transferAmount);
    }

    private void applyTransferRequest(
            InventoryTransfer transfer,
            TransferRequest req,
            Branch fromBranch,
            Branch toBranch,
            TransferBusinessType businessType) {
        TransferComputation computation = buildTransferComputation(transfer, fromBranch, businessType, req.getItems());

        transfer.setFromBranch(fromBranch);
        transfer.setToBranch(toBranch);
        transfer.setTransferType(req.getTransferType());
        transfer.setDescription(req.getDescription());
        transfer.setTransporter(req.getTransporter());
        transfer.setVehicle(req.getVehicle());
        transfer.setDispatchOrder(req.getDispatchOrder());
        transfer.setReferenceCode(req.getReferenceCode());
        transfer.setPriority(req.getPriority());
        transfer.setTransferDate(req.getTransferDate());
        transfer.setDeadline(req.getDeadline());
        transfer.setTransferBusinessType(businessType);
        transfer.setTotalQuantity(computation.totalQuantity());
        transfer.setTotalValue(computation.totalValue());
        transfer.getDetails().clear();
        transfer.getDetails().addAll(computation.details());

        if (businessType == TransferBusinessType.INTERNAL_SALE) {
            transfer.setTransferAmount(computation.transferAmount());
            transfer.setSourceReceivableAmount(computation.transferAmount());
            transfer.setDestPayableAmount(computation.transferAmount());
            transfer.setPaidAmount(BigDecimal.ZERO);
            transfer.setSettlementStatus(TransferSettlementStatus.UNPAID);
        } else {
            transfer.setTransferAmount(null);
            transfer.setSourceReceivableAmount(null);
            transfer.setDestPayableAmount(null);
            transfer.setPaidAmount(null);
            transfer.setSettlementStatus(null);
        }
    }

    private void resetSourceConfirmation(InventoryTransfer transfer) {
        transfer.setSourceConfirmedBy(null);
        transfer.setSourceConfirmedAt(null);
    }

    private void resetApprovalFlowAudit(InventoryTransfer transfer) {
        transfer.setApprovedBy(null);
        transfer.setApprovedAt(null);
        transfer.setShippedBy(null);
        transfer.setShippedAt(null);
        transfer.setInspectionStartedBy(null);
        transfer.setInspectionStartedAt(null);
        transfer.setReceivedBy(null);
        transfer.setReceivedAt(null);
        transfer.setSettledBy(null);
        transfer.setSettledAt(null);
    }

    private InventoryTransfer createTransferInternal(
            TransferRequest req,
            User createdByUser,
            Branch createdByBranchOverride) {
        Long fromBranchId = req.getFromBranchId();
        Long toBranchId = req.getToBranchId();
        inventoryCheckGuardService.assertNoOpenCheckForBranch(fromBranchId, "tao phieu dieu chuyen");
        inventoryCheckGuardService.assertNoOpenCheckForBranch(toBranchId, "tao phieu dieu chuyen");
        Branch fromBranch = branchRepo.findById(fromBranchId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy kho xuất"));
        Branch toBranch = branchRepo.findById(toBranchId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy kho nhận"));
        TransferBusinessType businessType = resolveBusinessType(req.getTransferBusinessType());

        validateTransferRequestBasics(req, fromBranch, toBranch, businessType);
        if (businessType == TransferBusinessType.INTERNAL_SALE) {
            validateInternalSalePricesForRequest(req.getItems());
        }

        Branch createdByBranch = createdByBranchOverride != null
                ? createdByBranchOverride
                : createdByUser != null && createdByUser.getBranch() != null
                        ? createdByUser.getBranch()
                        : toBranch;

        String newCode = String.format("PDC-%06d", transferRepo.countTotalTransfers() + 1);
        InventoryTransfer transfer = InventoryTransfer.builder()
                .transferCode(newCode)
                .status(InventoryTransferStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .createdBy(createdByUser)
                .createdByBranch(createdByBranch)
                .build();

        applyTransferRequest(transfer, req, fromBranch, toBranch, businessType);
        return transferRepo.save(transfer);
    }

    @Transactional
    public InventoryTransfer createTransfer(TransferRequest req) {
        User currentUser = getCurrentUser();
        return createTransferInternal(req, currentUser, currentUser.getBranch());
    }

    @Transactional(readOnly = true)
    public TransferDetailResponse getTransferDetail(Long id) {
        InventoryTransfer transfer = transferRepo.findByIdWithDetails(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy phiếu điều chuyển ID: " + id));
        assertTransferParticipantAccess(transfer);
        return convertToDetailResponse(transfer);
    }

    @Transactional
    public TransferDetailResponse updateTransfer(Long transferId, TransferRequest req) {
        InventoryTransfer transfer = transferRepo.findByIdWithDetails(transferId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy phiếu điều chuyển"));

        if (transfer.getStatus() != InventoryTransferStatus.PENDING
                && transfer.getStatus() != InventoryTransferStatus.SOURCE_CONFIRMED) {
            throw new BadRequestException("Chỉ có thể sửa phiếu trước khi được người có quyền duyệt phê duyệt.");
        }

        inventoryCheckGuardService.assertNoOpenCheckForBranch(transfer.getFromBranch().getId(), "sua phieu dieu chuyen");
        inventoryCheckGuardService.assertNoOpenCheckForBranch(transfer.getToBranch().getId(), "sua phieu dieu chuyen");
        inventoryCheckGuardService.assertNoOpenCheckForBranch(req.getFromBranchId(), "sua phieu dieu chuyen");
        inventoryCheckGuardService.assertNoOpenCheckForBranch(req.getToBranchId(), "sua phieu dieu chuyen");

        if (transfer.getStatus() == InventoryTransferStatus.SOURCE_CONFIRMED) {
            releaseReservedStock(transfer);
            resetSourceConfirmation(transfer);
        }

        Branch fromBranch = branchRepo.findById(req.getFromBranchId())
                .orElseThrow(() -> new NotFoundException("Không tìm thấy kho xuất"));
        Branch toBranch = branchRepo.findById(req.getToBranchId())
                .orElseThrow(() -> new NotFoundException("Không tìm thấy kho nhận"));
        TransferBusinessType businessType = resolveBusinessType(req.getTransferBusinessType());

        validateTransferRequestBasics(req, fromBranch, toBranch, businessType);
        if (businessType == TransferBusinessType.INTERNAL_SALE) {
            validateInternalSalePricesForRequest(req.getItems());
        }

        transfer.setStatus(InventoryTransferStatus.PENDING);
        resetApprovalFlowAudit(transfer);
        applyTransferRequest(transfer, req, fromBranch, toBranch, businessType);

        InventoryTransfer savedTransfer = transferRepo.save(transfer);
        return convertToDetailResponse(savedTransfer);
    }
    @Transactional
    public List<InventoryTransfer> createReplenishmentTransfersForSubOrder(SubOrder subOrder) {
        if (useGreedyReplenishmentCreation()) {
            return createGreedyReplenishmentForSubOrder(subOrder).transfers();
        }

        if (canUseSingleSourceReplenishmentRule()) {
            return createReplenishmentTransfersWithSingleSourceRule(subOrder);
        }

        subOrder = subOrderRepo.findByIdWithItems(subOrder.getId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phân đơn cần điều chuyển bổ sung"));
        boolean hasMissingItems = subOrder.getItems() != null
                && subOrder.getItems().stream()
                        .anyMatch(item -> Objects.requireNonNullElse(item.getMissingQuantity(), 0) > 0);
        if (!hasMissingItems
                || (subOrder.getStatus() != OrderStatus.PENDING
                        && subOrder.getStatus() != OrderStatus.AWAITING_REPLENISHMENT)) {
            throw new BadRequestException("Chỉ có thể tạo điều chuyển bổ sung cho phân đơn đang chờ điều chuyển");
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
                    itemRequest.setItemNote("Tự động bổ sung cho phân đơn " + referenceCode);
                    return itemRequest;
                })
                .toList();

        request.setItems(requestItems);
        transfers.add(createTransferInternal(request, findCurrentUserOrNull(), toBranch));
        return transfers;
    }

    private boolean useGreedyReplenishmentCreation() {
        return true;
    }

    @Transactional
    public ReplenishmentCreationResult createGreedyReplenishmentForSubOrder(SubOrder subOrder) {
        SubOrder replenishmentSubOrder = loadReplenishmentSubOrder(subOrder.getId());
        validateReplenishmentSubOrder(replenishmentSubOrder);

        List<InventoryTransfer> transfers = new ArrayList<>(findActiveReplenishmentTransfersForSubOrder(
                replenishmentSubOrder.getId()));
        List<MissingItemRequirement> requirements = reduceRequirementsByCoveredQuantities(
                buildMissingItemRequirements(replenishmentSubOrder),
                buildCoveredSkuQuantities(transfers));
        if (requirements.isEmpty()) {
            return new ReplenishmentCreationResult(transfers, Map.of());
        }

        ReplenishmentDecision decision = planReplenishmentForRequirements(replenishmentSubOrder, requirements);
        if (decision.type() != ReplenishmentDecisionType.PURCHASE_REQUEST) {
            transfers.addAll(createTransfersForDecision(decision));
            return new ReplenishmentCreationResult(transfers, Map.of());
        }

        Branch warehouse = decision.sourceBranch() != null ? decision.sourceBranch() : resolveMainWarehouse();
        Map<Long, Map<String, Integer>> transferPlanBySourceBranch = shouldUseDistributedReplenishment()
                ? buildAutoReplenishmentPlan(requirements, replenishmentSubOrder.getBranch(), warehouse)
                : buildWarehouseOnlyReplenishmentPlan(requirements, replenishmentSubOrder.getBranch(), warehouse);
        transfers.addAll(createTransfersForPlan(
                replenishmentSubOrder,
                decision.referenceCode(),
                transferPlanBySourceBranch));

        return new ReplenishmentCreationResult(
                transfers,
                calculateUncoveredRequirements(
                        requirements,
                        buildCoveredSkuQuantities(transferPlanBySourceBranch)));
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
            throw new RuntimeException("Không có sản phẩm nào cần điều chuyển bổ sung");
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
                        itemRequest.setItemNote("Tự động bổ sung cho phân đơn " + referenceCode);
                        return itemRequest;
                    })
                    .toList());
            transfers.add(createTransferInternal(request, findCurrentUserOrNull(), toBranch));
        }
        return transfers;
    }

    private Map<Long, Map<String, Integer>> buildAutoReplenishmentPlan(SubOrder subOrder, Branch warehouse) {
        if (useRequirementBasedGreedyPlan()) {
            return buildAutoReplenishmentPlan(
                    buildMissingItemRequirements(subOrder),
                    subOrder.getBranch(),
                    warehouse);
        }

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
                    throw new RuntimeException("Kho tổng không đủ hàng để điều chuyển bổ sung cho SKU: " + sku);
                }
                transferPlanBySourceBranch
                        .computeIfAbsent(warehouseId, ignored -> new LinkedHashMap<>())
                        .merge(sku, remainingMissing, Integer::sum);
            }
        }

        return transferPlanBySourceBranch;
    }

    private boolean useRequirementBasedGreedyPlan() {
        return true;
    }

    private Map<Long, Map<String, Integer>> buildAutoReplenishmentPlan(
            List<MissingItemRequirement> requirements,
            Branch toBranch,
            Branch warehouse) {
        Map<Long, Map<String, Integer>> transferPlanBySourceBranch = new LinkedHashMap<>();
        Long toBranchId = toBranch != null ? toBranch.getId() : null;
        Long warehouseId = warehouse != null ? warehouse.getId() : null;
        boolean includeWarehouseSources = shouldIncludeWarehouseSources(toBranch);

        for (MissingItemRequirement requirement : requirements != null ? requirements : List.<MissingItemRequirement>of()) {
            int remainingMissing = requirement.quantity();
            if (remainingMissing <= 0 || requirement.variantId() == null || requirement.sku() == null
                    || requirement.sku().isBlank()) {
                continue;
            }

            for (SourceBranchCandidate candidate : loadNearestSellableSourceCandidates(toBranch, requirement.variantId())) {
                if (remainingMissing <= 0) {
                    break;
                }

                int quantityToMove = Math.min(remainingMissing, candidate.availableQuantity());
                if (quantityToMove <= 0) {
                    continue;
                }

                addTransferPlanQuantity(
                        transferPlanBySourceBranch,
                        candidate.branchId(),
                        requirement.sku(),
                        quantityToMove);
                remainingMissing -= quantityToMove;
            }

            if (!includeWarehouseSources
                    && remainingMissing > 0
                    && warehouseId != null
                    && !Objects.equals(warehouseId, toBranchId)) {
                int warehouseAvailable = resolveTransferableQuantityAtBranch(requirement.variantId(), warehouseId);
                if (ENFORCE_SOURCE_STOCK_CHECK_ON_CREATE && warehouseAvailable < remainingMissing) {
                    throw new RuntimeException("Kho tong khong du hang de dieu chuyen bo sung cho SKU: "
                            + requirement.sku());
                }

                int quantityToMove = Math.min(remainingMissing, warehouseAvailable);
                if (quantityToMove > 0) {
                    addTransferPlanQuantity(
                            transferPlanBySourceBranch,
                            warehouseId,
                            requirement.sku(),
                            quantityToMove);
                }
            }
        }

        return transferPlanBySourceBranch;
    }

    private Map<Long, Map<String, Integer>> buildWarehouseOnlyReplenishmentPlan(
            List<MissingItemRequirement> requirements,
            Branch toBranch,
            Branch warehouse) {
        Map<Long, Map<String, Integer>> transferPlanBySourceBranch = new LinkedHashMap<>();
        Long toBranchId = toBranch != null ? toBranch.getId() : null;
        Long warehouseId = warehouse != null ? warehouse.getId() : null;
        if (warehouseId == null || Objects.equals(warehouseId, toBranchId)) {
            return transferPlanBySourceBranch;
        }

        for (MissingItemRequirement requirement : requirements != null ? requirements : List.<MissingItemRequirement>of()) {
            int warehouseAvailable = resolveTransferableQuantityAtBranch(requirement.variantId(), warehouseId);
            int quantityToMove = Math.min(requirement.quantity(), warehouseAvailable);
            if (quantityToMove > 0) {
                addTransferPlanQuantity(
                        transferPlanBySourceBranch,
                        warehouseId,
                        requirement.sku(),
                        quantityToMove);
            }
        }

        return transferPlanBySourceBranch;
    }

    private void addTransferPlanQuantity(
            Map<Long, Map<String, Integer>> transferPlanBySourceBranch,
            Long sourceBranchId,
            String sku,
            int quantity) {
        if (sourceBranchId == null || sku == null || sku.isBlank() || quantity <= 0) {
            return;
        }

        transferPlanBySourceBranch
                .computeIfAbsent(sourceBranchId, ignored -> new LinkedHashMap<>())
                .merge(sku, quantity, Integer::sum);
    }

    private Map<String, Integer> buildCoveredSkuQuantities(List<InventoryTransfer> transfers) {
        Map<String, Integer> coveredBySku = new LinkedHashMap<>();
        for (InventoryTransfer transfer : transfers != null ? transfers : List.<InventoryTransfer>of()) {
            List<InventoryTransferDetail> details = transfer.getDetails() != null
                    ? transfer.getDetails()
                    : List.of();
            for (InventoryTransferDetail detail : details) {
                String sku = detail.getProductVariant() != null ? detail.getProductVariant().getSku() : null;
                int quantity = Objects.requireNonNullElse(
                        detail.getQuantityRequested(),
                        Objects.requireNonNullElse(detail.getQuantity(), 0));
                if (sku != null && !sku.isBlank() && quantity > 0) {
                    coveredBySku.merge(sku, quantity, Integer::sum);
                }
            }
        }
        return coveredBySku;
    }

    private Map<String, Integer> buildCoveredSkuQuantities(Map<Long, Map<String, Integer>> transferPlanBySourceBranch) {
        Map<String, Integer> coveredBySku = new LinkedHashMap<>();
        for (Map<String, Integer> sourcePlan : transferPlanBySourceBranch != null
                ? transferPlanBySourceBranch.values()
                : List.<Map<String, Integer>>of()) {
            for (Map.Entry<String, Integer> itemEntry : sourcePlan.entrySet()) {
                if (itemEntry.getKey() != null && !itemEntry.getKey().isBlank()
                        && Objects.requireNonNullElse(itemEntry.getValue(), 0) > 0) {
                    coveredBySku.merge(itemEntry.getKey(), itemEntry.getValue(), Integer::sum);
                }
            }
        }
        return coveredBySku;
    }

    private List<MissingItemRequirement> reduceRequirementsByCoveredQuantities(
            List<MissingItemRequirement> requirements,
            Map<String, Integer> coveredBySku) {
        List<MissingItemRequirement> remainingRequirements = new ArrayList<>();
        Map<String, Integer> mutableCoveredBySku = new LinkedHashMap<>(
                coveredBySku != null ? coveredBySku : Map.of());

        for (MissingItemRequirement requirement : requirements != null ? requirements : List.<MissingItemRequirement>of()) {
            int coveredQuantity = Math.max(0, mutableCoveredBySku.getOrDefault(requirement.sku(), 0));
            int remainingQuantity = requirement.quantity() - Math.min(requirement.quantity(), coveredQuantity);
            mutableCoveredBySku.put(requirement.sku(), Math.max(0, coveredQuantity - requirement.quantity()));
            if (remainingQuantity > 0) {
                remainingRequirements.add(new MissingItemRequirement(
                        requirement.variantId(),
                        requirement.sku(),
                        remainingQuantity));
            }
        }

        return remainingRequirements;
    }

    private Map<Long, Integer> calculateUncoveredRequirements(
            List<MissingItemRequirement> requirements,
            Map<String, Integer> coveredBySku) {
        Map<Long, Integer> uncoveredByVariantId = new LinkedHashMap<>();
        for (MissingItemRequirement requirement : reduceRequirementsByCoveredQuantities(requirements, coveredBySku)) {
            uncoveredByVariantId.merge(requirement.variantId(), requirement.quantity(), Integer::sum);
        }
        return uncoveredByVariantId;
    }

    private List<SourceBranchCandidate> loadNearestSellableSourceCandidates(Branch toBranch, Long variantId) {
        if (toBranch == null || toBranch.getId() == null || variantId == null) {
            return List.of();
        }

        Map<Long, Integer> availableByBranch = new LinkedHashMap<>();
        Map<Long, Integer> safetyThresholdByBranch = new LinkedHashMap<>();
        Map<Long, Branch> branchById = new LinkedHashMap<>();
        boolean includeWarehouseSources = shouldIncludeWarehouseSources(toBranch);

        for (Inventory inventory : inventoryRepo.findByProductVariantId(variantId)) {
            Branch sourceBranch = inventory.getBranch();
            Long sourceBranchId = sourceBranch != null ? sourceBranch.getId() : null;
            int availableQty = Math.max(
                    0,
                    Objects.requireNonNullElse(inventory.getQuantity(), 0)
                            - Objects.requireNonNullElse(inventory.getReservedQuantity(), 0));

            if (sourceBranchId == null
                    || sourceBranch == null
                    || Objects.equals(sourceBranchId, toBranch.getId())
                    || sourceBranch.getStatus() != BranchStatus.ACTIVE
                    || (!includeWarehouseSources && isWarehouseBranch(sourceBranch))
                    || availableQty <= 0) {
                continue;
            }

            availableByBranch.merge(sourceBranchId, availableQty, Integer::sum);
            safetyThresholdByBranch.merge(
                    sourceBranchId,
                    Math.max(0, Objects.requireNonNullElse(inventory.getMinStock(), 0)),
                    Integer::max);
            branchById.putIfAbsent(sourceBranchId, sourceBranch);
        }

        return availableByBranch.entrySet().stream()
                .map(entry -> {
                    Branch sourceBranch = branchById.get(entry.getKey());
                    int transferableQuantity = Math.max(
                            0,
                            entry.getValue() - safetyThresholdByBranch.getOrDefault(entry.getKey(), 0));
                    return new SourceBranchCandidate(
                            entry.getKey(),
                            transferableQuantity,
                            calculateHaversineDistance(
                                    toBranch.getLat(),
                                    toBranch.getLng(),
                                    sourceBranch != null ? sourceBranch.getLat() : null,
                                    sourceBranch != null ? sourceBranch.getLng() : null));
                })
                .filter(candidate -> candidate.availableQuantity() > 0)
                .sorted(Comparator
                        .comparingDouble(SourceBranchCandidate::distanceKm)
                        .thenComparing(candidate -> {
                            Branch sourceBranch = branchById.get(candidate.branchId());
                            return isWarehouseBranch(sourceBranch) ? 0 : 1;
                        })
                        .thenComparing(SourceBranchCandidate::branchId))
                .toList();
    }

    private int resolveAvailableQuantityAtBranch(Long variantId, Long branchId) {
        if (variantId == null || branchId == null) {
            return 0;
        }

        return inventoryRepo.findByProductVariantId(variantId).stream()
                .filter(inv -> inv.getBranch() != null && Objects.equals(inv.getBranch().getId(), branchId))
                .mapToInt(inv -> Math.max(
                        0,
                        Objects.requireNonNullElse(inv.getQuantity(), 0)
                                - Objects.requireNonNullElse(inv.getReservedQuantity(), 0)))
                .sum();
    }

    private int resolveSafetyThresholdAtBranch(Long variantId, Long branchId) {
        if (variantId == null || branchId == null) {
            return 0;
        }

        return inventoryRepo.findByProductVariantId(variantId).stream()
                .filter(inv -> inv.getBranch() != null && Objects.equals(inv.getBranch().getId(), branchId))
                .map(Inventory::getMinStock)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .max()
                .orElse(0);
    }

    private int resolveTransferableQuantityAtBranch(Long variantId, Long branchId) {
        return Math.max(
                0,
                resolveAvailableQuantityAtBranch(variantId, branchId)
                        - resolveSafetyThresholdAtBranch(variantId, branchId));
    }

    private record SourceBranchCandidate(Long branchId, int availableQuantity, double distanceKm) {
    }

    private boolean shouldIncludeWarehouseSources(Branch destinationBranch) {
        return isWarehouseBranch(destinationBranch);
    }

    private boolean canUseSingleSourceReplenishmentRule() {
        return true;
    }

    @Transactional(readOnly = true)
    public List<InventoryTransfer> findActiveReplenishmentTransfersForSubOrder(Long subOrderId) {
        SubOrder subOrder = loadReplenishmentSubOrder(subOrderId);
        return transferRepo.findByReferenceCodeAndStatusInOrderByCreatedAtDesc(
                buildReplenishmentReferenceCode(subOrder),
                ACTIVE_REPLENISHMENT_TRANSFER_STATUSES);
    }

    @Transactional(readOnly = true)
    public ReplenishmentDecision planReplenishmentForSubOrder(SubOrder subOrder) {
        SubOrder replenishmentSubOrder = loadReplenishmentSubOrder(subOrder.getId());
        validateReplenishmentSubOrder(replenishmentSubOrder);

        return planReplenishmentForRequirements(
                replenishmentSubOrder,
                buildMissingItemRequirements(replenishmentSubOrder));
    }

    private ReplenishmentDecision planReplenishmentForRequirements(
            SubOrder replenishmentSubOrder,
            List<MissingItemRequirement> requirements) {
        Branch destinationBranch = replenishmentSubOrder.getBranch();
        Branch mainWarehouse = resolveMainWarehouse();
        Map<String, Integer> itemQuantities = buildItemQuantities(requirements);
        String referenceCode = buildReplenishmentReferenceCode(replenishmentSubOrder);

        Branch regularSourceBranch = findNearestRegularSourceBranch(destinationBranch, requirements);
        if (regularSourceBranch != null) {
            return new ReplenishmentDecision(
                    ReplenishmentDecisionType.REGULAR_BRANCH_TRANSFER,
                    regularSourceBranch,
                    destinationBranch,
                    referenceCode,
                    itemQuantities);
        }

        if (!Objects.equals(mainWarehouse.getId(), destinationBranch.getId())
                && canBranchFullySupplyRequirements(mainWarehouse.getId(), requirements)) {
            return new ReplenishmentDecision(
                    ReplenishmentDecisionType.MAIN_WAREHOUSE_TRANSFER,
                    mainWarehouse,
                    destinationBranch,
                    referenceCode,
                    itemQuantities);
        }

        return new ReplenishmentDecision(
                ReplenishmentDecisionType.PURCHASE_REQUEST,
                mainWarehouse,
                destinationBranch,
                referenceCode,
                itemQuantities);
    }

    @Transactional
    public List<InventoryTransfer> createMainWarehouseReplenishmentTransferIfPossible(Long subOrderId) {
        SubOrder subOrder = loadReplenishmentSubOrder(subOrderId);
        if (!canReplenishSubOrder(subOrder)) {
            return List.of();
        }

        List<InventoryTransfer> transfers = new ArrayList<>(findActiveReplenishmentTransfersForSubOrder(subOrderId));
        List<MissingItemRequirement> requirements = reduceRequirementsByCoveredQuantities(
                buildMissingItemRequirements(subOrder),
                buildCoveredSkuQuantities(transfers));
        if (requirements.isEmpty()) {
            return transfers;
        }

        Branch mainWarehouse = resolveMainWarehouse();
        if (Objects.equals(mainWarehouse.getId(), subOrder.getBranch().getId())) {
            return transfers;
        }

        Map<Long, Map<String, Integer>> transferPlanBySourceBranch = buildWarehouseOnlyReplenishmentPlan(
                requirements,
                subOrder.getBranch(),
                mainWarehouse);
        transfers.addAll(createTransfersForPlan(
                subOrder,
                buildReplenishmentReferenceCode(subOrder),
                transferPlanBySourceBranch));
        return transfers;
    }

    private List<InventoryTransfer> createReplenishmentTransfersWithSingleSourceRule(SubOrder subOrder) {
        SubOrder replenishmentSubOrder = loadReplenishmentSubOrder(subOrder.getId());
        validateReplenishmentSubOrder(replenishmentSubOrder);

        List<InventoryTransfer> existingTransfers = findActiveReplenishmentTransfersForSubOrder(replenishmentSubOrder.getId());
        if (!existingTransfers.isEmpty()) {
            return existingTransfers;
        }

        ReplenishmentDecision decision = planReplenishmentForSubOrder(replenishmentSubOrder);
        if (decision.type() == ReplenishmentDecisionType.PURCHASE_REQUEST) {
            return List.of();
        }

        return createTransfersForDecision(decision);
    }

    private SubOrder loadReplenishmentSubOrder(Long subOrderId) {
        return subOrderRepo.findByIdWithItems(subOrderId)
                .orElseThrow(() -> new BadRequestException("Không tìm thấy phân đơn cần điều chuyển bổ sung."));
    }

    private boolean canReplenishSubOrder(SubOrder subOrder) {
        return subOrder != null
                && subOrder.getOrder() != null
                && subOrder.getBranch() != null
                && (subOrder.getStatus() == OrderStatus.PENDING
                        || subOrder.getStatus() == OrderStatus.AWAITING_REPLENISHMENT)
                && subOrder.getItems() != null
                && subOrder.getItems().stream()
                        .anyMatch(item -> Objects.requireNonNullElse(item.getMissingQuantity(), 0) > 0
                                && item.getProductVariant() != null
                                && item.getProductVariant().getId() != null
                                && item.getProductVariant().getSku() != null
                                && !item.getProductVariant().getSku().isBlank());
    }

    private void validateReplenishmentSubOrder(SubOrder subOrder) {
        if (!canReplenishSubOrder(subOrder)) {
            throw new BadRequestException("Chỉ có thể tạo điều chuyển bổ sung cho phân đơn đang chờ điều chuyển.");
        }
    }

    private String buildReplenishmentReferenceCode(SubOrder subOrder) {
        return subOrder.getOrder().getCode() + "-SUB-" + subOrder.getId();
    }

    private List<MissingItemRequirement> buildMissingItemRequirements(SubOrder subOrder) {
        Map<String, MissingItemRequirement> requirementsBySku = new LinkedHashMap<>();
        List<SubOrderItem> subOrderItems = subOrder.getItems() != null ? subOrder.getItems() : List.of();

        for (SubOrderItem item : subOrderItems) {
            int missingQty = Objects.requireNonNullElse(item.getMissingQuantity(), 0);
            if (missingQty <= 0 || item.getProductVariant() == null || item.getProductVariant().getId() == null) {
                continue;
            }

            String sku = item.getProductVariant().getSku();
            if (sku == null || sku.isBlank()) {
                continue;
            }

            requirementsBySku.compute(
                    sku,
                    (ignored, existing) -> existing == null
                            ? new MissingItemRequirement(item.getProductVariant().getId(), sku, missingQty)
                            : new MissingItemRequirement(existing.variantId(), existing.sku(), existing.quantity() + missingQty));
        }

        return new ArrayList<>(requirementsBySku.values());
    }

    private Map<String, Integer> buildItemQuantities(List<MissingItemRequirement> requirements) {
        Map<String, Integer> itemQuantities = new LinkedHashMap<>();
        for (MissingItemRequirement requirement : requirements) {
            if (requirement.quantity() <= 0 || requirement.sku() == null || requirement.sku().isBlank()) {
                continue;
            }
            itemQuantities.merge(requirement.sku(), requirement.quantity(), Integer::sum);
        }
        return itemQuantities;
    }

    private Branch findNearestRegularSourceBranch(
            Branch destinationBranch,
            List<MissingItemRequirement> requirements) {
        if (destinationBranch == null || destinationBranch.getId() == null || requirements.isEmpty()) {
            return null;
        }

        boolean includeWarehouseSources = shouldIncludeWarehouseSources(destinationBranch);
        return branchRepo.findAll().stream()
                .filter(branch -> branch != null
                        && branch.getId() != null
                        && !Objects.equals(branch.getId(), destinationBranch.getId())
                        && branch.getStatus() == BranchStatus.ACTIVE
                        && (includeWarehouseSources || !isWarehouseBranch(branch))
                        && canBranchFullySupplyRequirements(branch.getId(), requirements))
                .min(Comparator
                        .<Branch>comparingDouble(branch -> calculateHaversineDistance(
                                destinationBranch.getLat(),
                                destinationBranch.getLng(),
                                branch.getLat(),
                                branch.getLng()))
                        .thenComparing(branch -> isWarehouseBranch(branch) ? 0 : 1)
                        .thenComparing(Branch::getId))
                .orElse(null);
    }

    private boolean canBranchFullySupplyRequirements(
            Long branchId,
            List<MissingItemRequirement> requirements) {
        if (branchId == null || requirements == null || requirements.isEmpty()) {
            return false;
        }

        for (MissingItemRequirement requirement : requirements) {
            if (resolveTransferableQuantityAtBranch(requirement.variantId(), branchId) < requirement.quantity()) {
                return false;
            }
        }
        return true;
    }

    private TransferRequest buildAutoReplenishmentTransferRequest(
            Branch sourceBranch,
            Branch destinationBranch,
            String referenceCode,
            Map<String, Integer> itemQuantities) {
        LocalDateTime now = LocalDateTime.now();
        TransferRequest request = new TransferRequest();
        request.setFromBranchId(sourceBranch.getId());
        request.setToBranchId(destinationBranch.getId());
        request.setTransferType(AUTO_REPLENISHMENT_TRANSFER_TYPE);
        request.setDescription(buildAutoReplenishmentDescription(referenceCode, destinationBranch));
        request.setReferenceCode(referenceCode);
        request.setPriority("HIGH");
        request.setTransferDate(now);
        request.setDeadline(now.plusDays(1));
        request.setItems(itemQuantities.entrySet().stream()
                .map(itemEntry -> {
                    TransferItemRequest itemRequest = new TransferItemRequest();
                    itemRequest.setSku(itemEntry.getKey());
                    itemRequest.setQuantity(itemEntry.getValue());
                    itemRequest.setItemNote("Tự động bổ sung cho phân đơn " + referenceCode);
                    return itemRequest;
                })
                .toList());
        return request;
    }

    private List<InventoryTransfer> createTransfersForPlan(
            SubOrder subOrder,
            String referenceCode,
            Map<Long, Map<String, Integer>> transferPlanBySourceBranch) {
        if (subOrder == null
                || subOrder.getBranch() == null
                || transferPlanBySourceBranch == null
                || transferPlanBySourceBranch.isEmpty()) {
            return List.of();
        }

        List<InventoryTransfer> transfers = new ArrayList<>();
        Branch destinationBranch = subOrder.getBranch();
        for (Map.Entry<Long, Map<String, Integer>> sourcePlan : transferPlanBySourceBranch.entrySet()) {
            if (sourcePlan.getValue() == null || sourcePlan.getValue().isEmpty()) {
                continue;
            }

            Branch sourceBranch = branchRepo.findById(sourcePlan.getKey())
                    .orElseThrow(() -> new NotFoundException("Khong tim thay chi nhanh nguon bo sung"));
            transfers.add(createTransferInternal(
                    buildAutoReplenishmentTransferRequest(
                            sourceBranch,
                            destinationBranch,
                            referenceCode,
                            sourcePlan.getValue()),
                    findCurrentUserOrNull(),
                    destinationBranch));
        }

        return transfers;
    }

    private List<InventoryTransfer> createTransfersForDecision(ReplenishmentDecision decision) {
        if (decision == null
                || decision.type() == ReplenishmentDecisionType.PURCHASE_REQUEST
                || decision.sourceBranch() == null
                || decision.destinationBranch() == null
                || decision.itemQuantities() == null
                || decision.itemQuantities().isEmpty()) {
            return List.of();
        }

        return List.of(createTransferInternal(
                buildAutoReplenishmentTransferRequest(
                        decision.sourceBranch(),
                        decision.destinationBranch(),
                        decision.referenceCode(),
                        decision.itemQuantities()),
                findCurrentUserOrNull(),
                decision.destinationBranch()));
    }

    // ==========================================
    // LUỒNG 4 & 5 – BƯỚC 2: DUYỆT (Admin duyệt)
    // - Flow 4 (STOCK_TRANSFER): PENDING → APPROVED + Reserve kho nguồn
    // - Flow 5 (INTERNAL_SALE) : SOURCE_CONFIRMED → APPROVED + Validate giá điều
    // chuyển
    // ==========================================
    @Transactional
    public void approveTransfer(Long transferId) {
        InventoryTransfer transfer = transferRepo.findByIdWithDetails(transferId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy phiếu điều chuyển"));
        inventoryCheckGuardService.assertNoOpenCheckForBranch(transfer.getFromBranch().getId(), "duyet phieu dieu chuyen");
        inventoryCheckGuardService.assertNoOpenCheckForBranch(transfer.getToBranch().getId(), "duyet phieu dieu chuyen");

        boolean sourceConfirmationRequired = requiresSourceConfirmation(transfer);
        if (sourceConfirmationRequired) {
            if (transfer.getStatus() != InventoryTransferStatus.SOURCE_CONFIRMED) {
                throw new BadRequestException("Phiếu bán nội bộ phải được chi nhánh nguồn xác nhận trước khi người có quyền duyệt phê duyệt.");
            }
            if (transfer.getTransferBusinessType() == TransferBusinessType.INTERNAL_SALE) {
                validateInternalSalePricesForTransfer(transfer);
            }
        } else {
            if (transfer.getStatus() != InventoryTransferStatus.PENDING) {
                throw new BadRequestException("Chỉ có thể duyệt phiếu đang ở trạng thái chờ duyệt.");
            }
            reserveSourceStock(transfer);
        }

        transfer.setApprovedBy(getCurrentUser());
        transfer.setApprovedAt(LocalDateTime.now());
        transfer.setStatus(InventoryTransferStatus.APPROVED);
        transferRepo.save(transfer);
    }

    @Transactional
    public void sourceConfirm(Long transferId) {
        InventoryTransfer transfer = transferRepo.findByIdWithDetails(transferId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy phiếu điều chuyển"));
        inventoryCheckGuardService.assertNoOpenCheckForBranch(transfer.getFromBranch().getId(), "xac nhan dieu chuyen");
        inventoryCheckGuardService.assertNoOpenCheckForBranch(transfer.getToBranch().getId(), "xac nhan dieu chuyen");

        if (!requiresSourceConfirmation(transfer)) {
            throw new BadRequestException("Chỉ phiếu bán nội bộ mới cần bước xác nhận của chi nhánh nguồn.");
        }
        if (transfer.getStatus() != InventoryTransferStatus.PENDING) {
            throw new BadRequestException("Chỉ có thể xác nhận phiếu đang ở trạng thái PENDING.");
        }

        User currentUser = getCurrentUser();
        assertBranchActor(currentUser, transfer.getFromBranch(), "xác nhận nguồn");
        if (transfer.getTransferBusinessType() == TransferBusinessType.INTERNAL_SALE) {
            validateInternalSalePricesForTransfer(transfer);
        }
        reserveSourceStock(transfer);

        transfer.setSourceConfirmedBy(currentUser);
        transfer.setSourceConfirmedAt(LocalDateTime.now());
        transfer.setStatus(InventoryTransferStatus.SOURCE_CONFIRMED);
        transferRepo.save(transfer);
    }
    @Transactional
    public void approveAndShip(Long transferId) {
        InventoryTransfer transfer = transferRepo.findByIdWithDetails(transferId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy phiếu điều chuyển"));
        inventoryCheckGuardService.assertStockMutationAllowed(
                transfer.getFromBranch().getId(),
                transfer.getDetails().stream().map(detail -> detail.getProductVariant().getId()).toList(),
                "xuất hàng điều chuyển"
        );

        if (transfer.getStatus() != InventoryTransferStatus.APPROVED) {
            throw new BadRequestException("Chỉ có thể xuất kho phiếu đã được duyệt.");
        }

        Long fromBranchId = transfer.getFromBranch().getId();

        for (InventoryTransferDetail detail : transfer.getDetails()) {
            Long variantId = detail.getProductVariant().getId();
            int qtyToShip = Objects.requireNonNullElse(detail.getQuantity(), 0);
            if (qtyToShip <= 0) {
                continue;
            }

            int remaining = qtyToShip;
            List<Inventory> batches = inventoryRepo.findForUpdateFIFO(fromBranchId, variantId);
            for (Inventory batch : batches) {
                if (remaining <= 0) {
                    break;
                }
                int available = Objects.requireNonNullElse(batch.getQuantity(), 0);
                if (available <= 0) {
                    continue;
                }

                int deduct = Math.min(available, remaining);
                batch.setQuantity(available - deduct);
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
                throw new BadRequestException("Kho nguồn không đủ hàng để xuất cho SKU: "
                        + detail.getProductVariant().getSku());
            }
        }

        transfer.setShippedBy(getCurrentUser());
        transfer.setShippedAt(LocalDateTime.now());
        transfer.setStatus(InventoryTransferStatus.SHIPPING);
        transferRepo.save(transfer);
    }
    @Transactional
    public void startInspection(Long transferId) {
        InventoryTransfer transfer = transferRepo.findByIdWithDetails(transferId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy phiếu điều chuyển"));

        if (transfer.getStatus() != InventoryTransferStatus.SHIPPING) {
            throw new BadRequestException("Phiếu phải đang ở trạng thái SHIPPING mới có thể bắt đầu kiểm hàng.");
        }

        User currentUser = getCurrentUser();
        assertBranchActor(currentUser, transfer.getToBranch(), "bắt đầu kiểm hàng");
        transfer.setInspectionStartedBy(currentUser);
        transfer.setInspectionStartedAt(LocalDateTime.now());
        transfer.setStatus(InventoryTransferStatus.INSPECTING);
        transferRepo.save(transfer);
    }
    @Transactional
    public void receiveTransfer(Long id, List<TransferQCRequest> qcItems) {
        InventoryTransfer transfer = transferRepo.findByIdWithDetails(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy phiếu điều chuyển"));
        inventoryCheckGuardService.assertStockMutationAllowed(
                transfer.getToBranch().getId(),
                transfer.getDetails().stream().map(detail -> detail.getProductVariant().getId()).toList(),
                "nhận hàng điều chuyển"
        );

        if (transfer.getStatus() != InventoryTransferStatus.INSPECTING) {
            throw new BadRequestException("Phiếu phải ở trạng thái INSPECTING mới có thể xác nhận nhận hàng.");
        }
        if (qcItems == null || qcItems.isEmpty()) {
            throw new BadRequestException("Vui lòng nhập dữ liệu kiểm hàng cho từng sản phẩm.");
        }

        User currentUser = getCurrentUser();
        assertBranchActor(currentUser, transfer.getToBranch(), "nhận hàng");

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
                throw new BadRequestException("Sản phẩm QC không thuộc phiếu điều chuyển: " + variantId);
            }

            int requestedQty = Objects.requireNonNullElse(detail.getQuantity(), 0);
            if (qtyReal > requestedQty) {
                throw new BadRequestException("Số lượng kiểm hàng không được vượt quá số lượng điều chuyển cho SKU: "
                        + detail.getProductVariant().getSku());
            }
            if (qtyAccepted + qtyRejected != qtyReal) {
                throw new BadRequestException("Số lượng đạt + lỗi/thiếu phải bằng tổng thực nhận cho SKU: "
                        + detail.getProductVariant().getSku());
            }
            if ((qtyRejected > 0 || qtyReal < requestedQty) && (itemNote == null || itemNote.isBlank())) {
                throw new BadRequestException("Vui lòng ghi chú lý do hàng lỗi/thiếu cho SKU: "
                        + detail.getProductVariant().getSku());
            }

            detail.setQuantityReal(qtyReal);
            detail.setQuantityAccepted(qtyAccepted);
            detail.setQuantityRejected(qtyRejected);
            detail.setNote(itemNote);

            if (qtyAccepted > 0 || qtyRejected > 0) {
                addDestinationStock(transfer, detail, toBranch, qtyAccepted, qtyRejected);
                if (qtyAccepted > 0) {
                    backorderService.fulfillBackordersOnStockReceive(toBranch.getId(), variantId, qtyAccepted);
                }
            }
        }

        if (transfer.getTransferBusinessType() == TransferBusinessType.INTERNAL_SALE) {
            BigDecimal actualDebt = transfer.getDetails().stream()
                    .map(d -> {
                        BigDecimal unitPrice = d.getUnitTransferPrice() != null ? d.getUnitTransferPrice() : BigDecimal.ZERO;
                        int acceptedQty = Objects.requireNonNullElse(d.getQuantityAccepted(), 0);
                        return unitPrice.multiply(BigDecimal.valueOf(acceptedQty));
                    })
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            transfer.setTransferAmount(actualDebt);
            transfer.setPaidAmount(BigDecimal.ZERO);
            transfer.setSourceReceivableAmount(actualDebt);
            transfer.setDestPayableAmount(actualDebt);
            transfer.setSettlementStatus(actualDebt.compareTo(BigDecimal.ZERO) > 0
                    ? TransferSettlementStatus.UNPAID
                    : TransferSettlementStatus.PAID);
        }

        transfer.setReceivedBy(currentUser);
        transfer.setReceivedAt(LocalDateTime.now());
        transfer.setStatus(InventoryTransferStatus.COMPLETED);
        transferRepo.save(transfer);
    }

    @Transactional
    public TransferDetailResponse settleInternalPayment(Long transferId, TransferSettlementRequest request) {
        InventoryTransfer transfer = transferRepo.findByIdWithDetails(transferId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy phiếu điều chuyển"));

        if (transfer.getTransferBusinessType() != TransferBusinessType.INTERNAL_SALE) {
            throw new BadRequestException("Chỉ phiếu bán nội bộ mới có công nợ thanh toán.");
        }
        if (transfer.getStatus() != InventoryTransferStatus.COMPLETED) {
            throw new BadRequestException("Chỉ có thể thanh toán khi phiếu đã hoàn tất kiểm hàng.");
        }

        BigDecimal paidAmount = Objects.requireNonNullElse(transfer.getPaidAmount(), BigDecimal.ZERO);
        BigDecimal transferAmount = Objects.requireNonNullElse(transfer.getTransferAmount(), BigDecimal.ZERO);
        BigDecimal outstanding = transferAmount.subtract(paidAmount).max(BigDecimal.ZERO);
        if (request.getAmount().compareTo(outstanding) > 0) {
            throw new BadRequestException("Số tiền thanh toán vượt quá công nợ còn lại của phiếu điều chuyển.");
        }

        BigDecimal newPaidAmount = paidAmount.add(request.getAmount());
        BigDecimal newOutstanding = transferAmount.subtract(newPaidAmount).max(BigDecimal.ZERO);

        transfer.setPaidAmount(newPaidAmount);
        transfer.setSourceReceivableAmount(newOutstanding);
        transfer.setDestPayableAmount(newOutstanding);
        transfer.setSettlementStatus(newOutstanding.compareTo(BigDecimal.ZERO) == 0
                ? TransferSettlementStatus.PAID
                : TransferSettlementStatus.PARTIAL);
        transfer.setSettledBy(getCurrentUser());
        transfer.setSettledAt(LocalDateTime.now());
        transferRepo.save(transfer);
        return convertToDetailResponse(transfer);
    }
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
                String failureMessage = buildReserveFailureMessage(
                        transfer,
                        detail,
                        batches,
                        qtyNeeded,
                        totalAvailable);
                log.warn(
                        "Không đủ tồn kho khả dụng để reserve phiếu {} tại kho nguồn {} cho SKU {}. {}",
                        safeTransferCode(transfer),
                        safeBranchName(transfer.getFromBranch()),
                        detail.getProductVariant().getSku(),
                        failureMessage);
                throw new BadRequestException(failureMessage);
            }

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
        Long branchId = canApproveTransfersAcrossBranches() ? null : warehouseContext.resolveWarehouseId();
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
                    "Không thể hủy phiếu đang vận chuyển hoặc đang kiểm hàng. Vui lòng liên hệ người có quyền duyệt.");
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
                .orElseThrow(() -> new NotFoundException("Không tìm thấy phiếu điều chuyển"));
        if (transfer.getStatus() != InventoryTransferStatus.PENDING
                && transfer.getStatus() != InventoryTransferStatus.SOURCE_CONFIRMED) {
            throw new BadRequestException("Chỉ có thể đổi chi nhánh nhận trước khi phiếu được người có quyền duyệt phê duyệt.");
        }
        if (transfer.getFromBranch().getId().equals(newBranchId)) {
            throw new BadRequestException("Chi nhánh nhận trùng chi nhánh xuất.");
        }
        if (transfer.getStatus() == InventoryTransferStatus.SOURCE_CONFIRMED) {
            releaseReservedStock(transfer);
            resetSourceConfirmation(transfer);
            transfer.setStatus(InventoryTransferStatus.PENDING);
        }
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
    private String safeUserName(User user) {
        return user != null && user.getFullName() != null && !user.getFullName().isBlank()
                ? user.getFullName()
                : null;
    }

    private BigDecimal resolveOutstandingAmount(InventoryTransfer transfer) {
        BigDecimal transferAmount = Objects.requireNonNullElse(transfer.getTransferAmount(), BigDecimal.ZERO);
        BigDecimal paidAmount = Objects.requireNonNullElse(transfer.getPaidAmount(), BigDecimal.ZERO);
        return transferAmount.subtract(paidAmount).max(BigDecimal.ZERO);
    }

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
        String branchName = subOrder.getBranch() != null ? subOrder.getBranch().getName() : "chi nhánh nhận";
        return "Tự động tạo từ đơn thiếu hàng " + orderCode + " cho " + branchName;
    }

    private String buildAutoReplenishmentDescription(String referenceCode, Branch destinationBranch) {
        return "Tự động tạo từ đơn thiếu hàng "
                + referenceCode
                + " cho "
                + safeBranchName(destinationBranch);
    }

    private String normalizeText(String value) {
        if (value == null) {
            return "";
        }

        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replace('\u0111', 'd')
                .replace('\u0110', 'D');
        return normalized.toLowerCase(Locale.ROOT).trim();
    }

    private String safeBranchName(Branch branch) {
        return branch != null && branch.getName() != null && !branch.getName().isBlank()
                ? branch.getName()
                : "chi nhánh không xác định";
    }

    private String safeTransferCode(InventoryTransfer transfer) {
        return transfer != null && transfer.getTransferCode() != null && !transfer.getTransferCode().isBlank()
                ? transfer.getTransferCode()
                : "chưa có mã phiếu";
    }

    private String buildBatchSnapshot(List<Inventory> batches) {
        if (batches == null || batches.isEmpty()) {
            return "Không có lô tồn kho nào cho SKU này tại kho nguồn.";
        }

        return "Chi tiết lô: " + batches.stream()
                .map(batch -> {
                    String batchLabel = batch.getBatchNumber() != null && !batch.getBatchNumber().isBlank()
                            ? batch.getBatchNumber()
                            : "không mã lô";
                    int quantity = Objects.requireNonNullElse(batch.getQuantity(), 0);
                    int reservedQuantity = Objects.requireNonNullElse(batch.getReservedQuantity(), 0);
                    int availableQuantity = Math.max(0, quantity - reservedQuantity);
                    return String.format("%s [quantity=%d, reservedQuantity=%d, available=%d]",
                            batchLabel, quantity, reservedQuantity, availableQuantity);
                })
                .collect(Collectors.joining("; "));
    }

    private String buildReservationHolderSummary(
            Long fromBranchId,
            Long variantId,
            Long currentTransferId,
            int totalReservedQuantity) {
        List<String> holderLines = transferRepo.findReservationHolderSummaries(
                        fromBranchId,
                        variantId,
                        RESERVATION_HOLDER_STATUSES)
                .stream()
                .filter(row -> currentTransferId == null || !Objects.equals(currentTransferId, row[0]))
                .map(row -> String.format(
                        "%s [%s] -> %s (giữ %s)",
                        Objects.toString(row[1], "chưa có mã phiếu"),
                        Objects.toString(row[2], "UNKNOWN"),
                        Objects.toString(row[3], "chi nhánh không xác định"),
                        Objects.toString(row[4], "0")))
                .toList();

        if (!holderLines.isEmpty()) {
            return "Phiếu đang giữ hàng: " + String.join("; ", holderLines) + ".";
        }

        if (totalReservedQuantity > 0) {
            return "Hiện không truy vết được phiếu đang giữ hàng dù reservedQuantity > 0. Vui lòng kiểm tra dữ liệu reserve.";
        }

        return "Hiện chưa có phiếu điều chuyển nào khác đang giữ hàng.";
    }

    private String buildReserveFailureMessage(
            InventoryTransfer transfer,
            InventoryTransferDetail detail,
            List<Inventory> batches,
            int qtyNeeded,
            int totalAvailable) {
        int totalQuantity = batches.stream()
                .mapToInt(batch -> Objects.requireNonNullElse(batch.getQuantity(), 0))
                .sum();
        int totalReservedQuantity = batches.stream()
                .mapToInt(batch -> Objects.requireNonNullElse(batch.getReservedQuantity(), 0))
                .sum();

        String batchSnapshot = buildBatchSnapshot(batches);
        String reservationHolders = buildReservationHolderSummary(
                transfer.getFromBranch().getId(),
                detail.getProductVariant().getId(),
                transfer.getId(),
                totalReservedQuantity);

        return String.format(
                "Kho nguồn không đủ tồn kho khả dụng để giữ chỗ cho SKU %s. Cần: %d, khả dụng: %d, tồn thực tế: %d, đang giữ: %d. %s %s",
                detail.getProductVariant().getSku(),
                qtyNeeded,
                totalAvailable,
                totalQuantity,
                totalReservedQuantity,
                batchSnapshot,
                reservationHolders);
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
                .sourceBranchId(t.getFromBranch() != null ? t.getFromBranch().getId() : null)
                .destinationBranchId(t.getToBranch() != null ? t.getToBranch().getId() : null)
                .fromBranchName(t.getFromBranch() != null ? t.getFromBranch().getName() : "N/A")
                .toBranchName(t.getToBranch() != null ? t.getToBranch().getName() : "N/A")
                .createdByBranchId(t.getCreatedByBranch() != null ? t.getCreatedByBranch().getId() : null)
                .createdByBranchName(t.getCreatedByBranch() != null ? t.getCreatedByBranch().getName() : null)
                .sourceConfirmationRequired(requiresSourceConfirmation(t))
                .createdByName(safeUserName(t.getCreatedBy()))
                .sourceConfirmedByName(safeUserName(t.getSourceConfirmedBy()))
                .sourceConfirmedAt(t.getSourceConfirmedAt())
                .approvedByName(safeUserName(t.getApprovedBy()))
                .approvedAt(t.getApprovedAt())
                .shippedByName(safeUserName(t.getShippedBy()))
                .shippedAt(t.getShippedAt())
                .inspectionStartedByName(safeUserName(t.getInspectionStartedBy()))
                .inspectionStartedAt(t.getInspectionStartedAt())
                .receivedByName(safeUserName(t.getReceivedBy()))
                .receivedAt(t.getReceivedAt())
                .settledByName(safeUserName(t.getSettledBy()))
                .settledAt(t.getSettledAt())
                .totalQuantity(t.getTotalQuantity())
                .totalValue(t.getTotalValue())
                .transferBusinessType(t.getTransferBusinessType())
                .transferAmount(t.getTransferAmount())
                .settlementStatus(t.getSettlementStatus())
                .sourceReceivableAmount(t.getSourceReceivableAmount())
                .destPayableAmount(t.getDestPayableAmount())
                .paidAmount(Objects.requireNonNullElse(t.getPaidAmount(), BigDecimal.ZERO))
                .outstandingAmount(resolveOutstandingAmount(t))
                .requiredMarginPercent(null)
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

    private record TransferComputation(
            List<InventoryTransferDetail> details,
            int totalQuantity,
            BigDecimal totalValue,
            BigDecimal transferAmount) {
    }

    /**
     * Tinh khoang cach Haversine giua hai diem (lat1, lng1) va (lat2, lng2) - don vi KM
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


