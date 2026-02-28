package com.zone.agri.service;

import com.zone.agri.common.AuthUtils;
import com.zone.agri.common.WarehouseContext;
import com.zone.agri.dto.stock.*;
import com.zone.agri.entity.*;
import com.zone.agri.entity.enums.StockRequestStatus;
import com.zone.agri.entity.enums.TransactionType;
import com.zone.agri.exception.BadRequestException;
import com.zone.agri.exception.Forbidden;
import com.zone.agri.exception.NotFoundException;
import com.zone.agri.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StockRequestService {

    private static final Long MAIN_WAREHOUSE_ID = 1L;

    private final StockRequestRepository stockRequestRepository;
    private final StockRequestItemRepository stockRequestItemRepository;
    private final BranchRepository branchRepository;
    private final ProductVariantRepository productVariantRepository;
    private final InventoryRepository inventoryRepository;
    private final InventoryTransactionRepository inventoryTransactionRepository;
    private final WarehouseContext warehouseContext;

    // ─────────────────────────────────────────────
    // BRANCH_MANAGER: Tạo yêu cầu bổ sung kho
    // ─────────────────────────────────────────────
    @Transactional
    public StockRequestResponse createRequest(StockRequestCreateDto dto) {
        Long toBranchId = warehouseContext.resolveWarehouseId();
        // SUPER_ADMIN không tạo request cho chính mình (họ approve)
        if (toBranchId == null) {
            throw new BadRequestException("SUPER_ADMIN không thể tạo yêu cầu bổ sung kho");
        }
        if (toBranchId.equals(MAIN_WAREHOUSE_ID)) {
            throw new BadRequestException("Kho chính không thể tạo yêu cầu bổ sung");
        }

        Branch fromBranch = branchRepository.findById(MAIN_WAREHOUSE_ID)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy kho chính (id=1)"));
        Branch toBranch = branchRepository.findById(toBranchId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy chi nhánh id=" + toBranchId));

        String requestCode = generateRequestCode();

        StockRequest request = StockRequest.builder()
                .requestCode(requestCode)
                .fromBranch(fromBranch)
                .toBranch(toBranch)
                .status(StockRequestStatus.PENDING)
                .note(dto.getNote())
                .build();

        List<StockRequestItem> items = dto.getItems().stream().map(itemDto -> {
            ProductVariant variant = productVariantRepository.findById(itemDto.getProductVariantId())
                    .orElseThrow(() -> new NotFoundException("Variant không tồn tại id=" + itemDto.getProductVariantId()));
            return StockRequestItem.builder()
                    .stockRequest(request)
                    .productVariant(variant)
                    .requestedQty(itemDto.getRequestedQty())
                    .build();
        }).collect(Collectors.toList());

        request.setItems(items);
        StockRequest saved = stockRequestRepository.save(request);
        return mapToResponse(saved);
    }

    // ─────────────────────────────────────────────
    // GET ALL — SUPER_ADMIN thấy tất cả, BRANCH_MANAGER thấy của mình
    // ─────────────────────────────────────────────
    @Transactional(readOnly = true)
    public List<StockRequestResponse> getAll() {
        Long warehouseId = warehouseContext.resolveWarehouseId();
        List<StockRequest> list = (warehouseId == null)
                ? stockRequestRepository.findAllByOrderByCreatedAtDesc()
                : stockRequestRepository.findByToBranchIdOrderByCreatedAtDesc(warehouseId);
        return list.stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    // ─────────────────────────────────────────────
    // GET BY ID
    // ─────────────────────────────────────────────
    @Transactional(readOnly = true)
    public StockRequestResponse getById(Long id) {
        StockRequest request = findAndAssertAccess(id);
        return mapToResponse(request);
    }

    // ─────────────────────────────────────────────
    // SUPER_ADMIN: Duyệt yêu cầu → deduct Main, add Branch, write transactions
    // ─────────────────────────────────────────────
    @Transactional
    public StockRequestResponse approveRequest(Long id, StockRequestApproveDto dto) {
        StockRequest request = stockRequestRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy stock request id=" + id));

        if (request.getStatus() != StockRequestStatus.PENDING) {
            throw new BadRequestException("Chỉ có thể duyệt yêu cầu ở trạng thái PENDING");
        }

        Map<Long, StockRequestItem> itemMap = request.getItems().stream()
                .collect(Collectors.toMap(StockRequestItem::getId, Function.identity()));

        Branch mainBranch = request.getFromBranch();
        Branch toBranch = request.getToBranch();

        for (StockRequestApproveItemDto approveItem : dto.getItems()) {
            StockRequestItem item = itemMap.get(approveItem.getItemId());
            if (item == null) {
                throw new BadRequestException("Item id=" + approveItem.getItemId() + " không thuộc request này");
            }

            int approvedQty = approveItem.getApprovedQty();
            if (approvedQty <= 0) {
                item.setApprovedQty(0);
                continue;
            }

            ProductVariant variant = item.getProductVariant();

            // Kiểm tra tồn kho kho chính
            Inventory mainInventory = inventoryRepository
                    .findByBranchAndProductVariant(mainBranch, variant)
                    .orElseThrow(() -> new BadRequestException(
                            "Kho chính không có tồn kho cho SKU: " + variant.getSku()));

            if (mainInventory.getQuantity() < approvedQty) {
                throw new BadRequestException(
                        "Kho chính không đủ hàng cho SKU: " + variant.getSku() +
                        " (cần: " + approvedQty + ", hiện có: " + mainInventory.getQuantity() + ")");
            }

            // Trừ kho chính
            int mainNewBalance = mainInventory.getQuantity() - approvedQty;
            mainInventory.setQuantity(mainNewBalance);
            inventoryRepository.save(mainInventory);

            // Cộng kho chi nhánh
            Inventory branchInventory = inventoryRepository
                    .findByBranchAndProductVariant(toBranch, variant)
                    .orElse(Inventory.builder()
                            .branch(toBranch)
                            .productVariant(variant)
                            .quantity(0)

                            .build());

            int branchNewBalance = branchInventory.getQuantity() + approvedQty;
            branchInventory.setQuantity(branchNewBalance);
            branchInventory.setLastReceiptDate(LocalDateTime.now());
            inventoryRepository.save(branchInventory);

            // Ghi InventoryTransaction TRANSFER_OUT (kho chính)
            inventoryTransactionRepository.save(InventoryTransaction.builder()
                    .type(TransactionType.TRANSFER_OUT)
                    .quantityChange(-approvedQty)
                    .newBalance(mainNewBalance)
                    .referenceCode(request.getRequestCode())
                    .reason("Điều chuyển sang: " + toBranch.getName())
                    .inventory(mainInventory)
                    .createdAt(LocalDateTime.now())
                    .build());

            // Ghi InventoryTransaction TRANSFER_IN (chi nhánh)
            inventoryTransactionRepository.save(InventoryTransaction.builder()
                    .type(TransactionType.TRANSFER_IN)
                    .quantityChange(approvedQty)
                    .newBalance(branchNewBalance)
                    .referenceCode(request.getRequestCode())
                    .reason("Nhận từ kho chính: " + mainBranch.getName())
                    .inventory(branchInventory)
                    .createdAt(LocalDateTime.now())
                    .build());

            item.setApprovedQty(approvedQty);
        }

        request.setStatus(StockRequestStatus.APPROVED);
        request.setApprovedBy(AuthUtils.getCurrentUserId());
        request.setApprovedAt(LocalDateTime.now());

        return mapToResponse(stockRequestRepository.save(request));
    }

    // ─────────────────────────────────────────────
    // SUPER_ADMIN: Từ chối yêu cầu
    // ─────────────────────────────────────────────
    @Transactional
    public StockRequestResponse rejectRequest(Long id, String reason) {
        StockRequest request = stockRequestRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy stock request id=" + id));

        if (request.getStatus() != StockRequestStatus.PENDING) {
            throw new BadRequestException("Chỉ có thể từ chối yêu cầu ở trạng thái PENDING");
        }

        request.setStatus(StockRequestStatus.REJECTED);
        request.setRejectReason(reason);
        request.setApprovedBy(AuthUtils.getCurrentUserId());
        request.setApprovedAt(LocalDateTime.now());

        return mapToResponse(stockRequestRepository.save(request));
    }

    // ─────────────────────────────────────────────
    // BRANCH_MANAGER: Huỷ yêu cầu của mình (chỉ khi PENDING)
    // ─────────────────────────────────────────────
    @Transactional
    public StockRequestResponse cancelRequest(Long id) {
        StockRequest request = findAndAssertAccess(id);

        if (request.getStatus() != StockRequestStatus.PENDING) {
            throw new BadRequestException("Chỉ có thể huỷ yêu cầu ở trạng thái PENDING");
        }
        if (warehouseContext.isSuperAdmin()) {
            throw new Forbidden("SUPER_ADMIN dùng reject thay vì cancel");
        }

        request.setStatus(StockRequestStatus.CANCELLED);
        return mapToResponse(stockRequestRepository.save(request));
    }

    // ─────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────

    private StockRequest findAndAssertAccess(Long id) {
        StockRequest request = stockRequestRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy stock request id=" + id));
        warehouseContext.assertAccess(request.getToBranch().getId());
        return request;
    }

    private String generateRequestCode() {
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String base = "SR-" + date + "-";
        long count = stockRequestRepository.count() + 1;
        String code = base + String.format("%04d", count);
        // Đảm bảo unique
        while (stockRequestRepository.existsByRequestCode(code)) {
            count++;
            code = base + String.format("%04d", count);
        }
        return code;
    }

    private StockRequestResponse mapToResponse(StockRequest r) {
        List<StockRequestItemResponse> itemResponses = r.getItems().stream()
                .map(i -> StockRequestItemResponse.builder()
                        .id(i.getId())
                        .productVariantId(i.getProductVariant().getId())
                        .sku(i.getProductVariant().getSku())
                        .productName(i.getProductVariant().getProduct() != null
                                ? i.getProductVariant().getProduct().getName() : "")
                        .unit("")
                        .requestedQty(i.getRequestedQty())
                        .approvedQty(i.getApprovedQty())
                        .build())
                .collect(Collectors.toList());

        return StockRequestResponse.builder()
                .id(r.getId())
                .requestCode(r.getRequestCode())
                .fromBranchId(r.getFromBranch().getId())
                .fromBranchName(r.getFromBranch().getName())
                .toBranchId(r.getToBranch().getId())
                .toBranchName(r.getToBranch().getName())
                .status(r.getStatus())
                .note(r.getNote())
                .rejectReason(r.getRejectReason())
                .approvedBy(r.getApprovedBy())
                .approvedAt(r.getApprovedAt())
                .createdAt(r.getCreatedAt())
                .createdByUserId(r.getCreatedByUserId())
                .items(itemResponses)
                .build();
    }
}
