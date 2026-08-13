package com.zone.agri.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.zone.agri.dto.response.inventory.InventoryIOSummaryResponse;
import com.zone.agri.dto.response.inventory.InventoryLedgerEntryResponse;
import com.zone.agri.dto.response.inventory.StockSummaryResponse;
import com.zone.agri.entity.InventoryTransaction;
import com.zone.agri.entity.enums.TransactionType;
import com.zone.agri.repository.InventoryRepository;
import com.zone.agri.repository.InventoryTransactionRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class InventoryReportService {

    private final InventoryRepository inventoryRepository;
    private final InventoryTransactionRepository inventoryTransactionRepository;

    // Chỉ các loại giao dịch làm thay đổi tồn kho vật lý thật sự — xem giải thích ở
    // InventoryTransactionRepository. ORDER_RESERVE/ORDER_RELEASE/CANCEL_RELEASE bị loại vì chỉ
    // tác động đến số lượng "tạm giữ" (reservedQuantity), không phải tồn kho thực tế.
    private static final Set<TransactionType> PHYSICAL_TRANSACTION_TYPES = EnumSet.of(
            TransactionType.IMPORT,
            TransactionType.SALE,
            TransactionType.TRANSFER_IN,
            TransactionType.TRANSFER_OUT,
            TransactionType.ADJUSTMENT,
            TransactionType.RETURN,
            TransactionType.DAMAGED);

    @Transactional(readOnly = true)
    public List<StockSummaryResponse> getStockSummary(Long branchId) {
        List<InventoryRepository.StockSummaryProjection> rows = inventoryRepository.findStockSummary(branchId);

        BigDecimal totalBranchValue = rows.stream()
                .map(InventoryRepository.StockSummaryProjection::getBranchValue)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<StockSummaryResponse> result = new ArrayList<>();
        for (InventoryRepository.StockSummaryProjection row : rows) {
            long systemQty = safeLong(row.getSystemQuantity());
            if (systemQty <= 0) {
                continue; // Biến thể chưa từng có tồn kho thực tế — không đưa vào báo cáo.
            }

            BigDecimal branchValue = safeDecimal(row.getBranchValue());
            long branchQty = safeLong(row.getBranchQuantity());

            BigDecimal avgCost = branchQty > 0
                    ? branchValue.divide(BigDecimal.valueOf(branchQty), 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            BigDecimal weightPercent = totalBranchValue.compareTo(BigDecimal.ZERO) > 0
                    ? branchValue.multiply(BigDecimal.valueOf(100))
                            .divide(totalBranchValue, 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            result.add(StockSummaryResponse.builder()
                    .variantId(row.getVariantId())
                    .sku(row.getSku())
                    .productName(row.getProductName())
                    .categoryName(row.getCategoryName())
                    .branchQuantity((int) branchQty)
                    .branchValue(branchValue)
                    .branchAvgCost(avgCost)
                    .branchWeightPercent(weightPercent)
                    .systemQuantity((int) systemQty)
                    .systemValue(safeDecimal(row.getSystemValue()))
                    .build());
        }

        result.sort((a, b) -> b.getBranchValue().compareTo(a.getBranchValue()));
        return result;
    }

    @Transactional(readOnly = true)
    public List<InventoryLedgerEntryResponse> getLedger(
            Long branchId, LocalDate startDate, LocalDate endDate, String direction) {
        LocalDateTime start = startDate != null ? startDate.atStartOfDay() : null;
        LocalDateTime end = endDate != null ? endDate.atTime(LocalTime.MAX) : null;

        List<InventoryTransaction> rows = inventoryTransactionRepository
                .findLedger(PHYSICAL_TRANSACTION_TYPES, branchId, start, end);

        return rows.stream()
                .filter(tx -> matchesDirection(tx, direction))
                .map(this::mapLedgerEntry)
                .toList();
    }

    // Trước đây lọc theo DẤU của quantityChange (change>0 = "nhập", change<0 = "xuất") thay vì loại
    // giao dịch thật — hậu quả là 1 dòng TRANSFER_IN vừa hiện ở "Nhập kho" vừa hiện ở "Điều chuyển"
    // (đếm/xem trùng 2 lần), và RETURN (khách trả hàng) bị gộp nhầm vào "Nhập kho" dù không phải
    // nhập từ NCC. Nay lọc theo đúng loại giao dịch, mỗi giao dịch chỉ thuộc đúng 1 nhóm cụ thể (hoặc
    // không nhóm nào — vẫn luôn thấy được qua bộ lọc "Tất cả giao dịch").
    private boolean matchesDirection(InventoryTransaction tx, String direction) {
        if (direction == null || direction.isBlank() || "all".equalsIgnoreCase(direction)) {
            return true;
        }
        if ("import".equalsIgnoreCase(direction)) {
            return tx.getType() == TransactionType.IMPORT;
        }
        if ("export".equalsIgnoreCase(direction)) {
            return tx.getType() == TransactionType.SALE || tx.getType() == TransactionType.DAMAGED;
        }
        if ("transfer".equalsIgnoreCase(direction)) {
            return tx.getType() == TransactionType.TRANSFER_IN || tx.getType() == TransactionType.TRANSFER_OUT;
        }
        return true;
    }

    private InventoryLedgerEntryResponse mapLedgerEntry(InventoryTransaction tx) {
        int change = tx.getQuantityChange() != null ? tx.getQuantityChange() : 0;
        int after = tx.getNewBalance() != null ? tx.getNewBalance() : 0;
        int before = after - change;

        return InventoryLedgerEntryResponse.builder()
                .id(tx.getId())
                .createdAt(tx.getCreatedAt())
                .productName(tx.getInventory().getProductVariant().getProduct() != null
                        ? tx.getInventory().getProductVariant().getProduct().getName()
                        : null)
                .sku(tx.getInventory().getProductVariant().getSku())
                .type(translateTransactionType(tx.getType()))
                .quantityChange(change)
                .balanceBefore(before)
                .balanceAfter(after)
                .branchName(tx.getInventory().getBranch() != null ? tx.getInventory().getBranch().getName() : null)
                .createdByName(tx.getCreatedBy() != null ? tx.getCreatedBy().getFullName() : "Hệ thống")
                .reason(tx.getReason())
                .build();
    }

    private String translateTransactionType(TransactionType type) {
        if (type == null) {
            return "";
        }
        return switch (type) {
            case IMPORT -> "Nhập kho";
            case SALE -> "Bán hàng";
            case TRANSFER_IN -> "Điều chuyển đến";
            case TRANSFER_OUT -> "Điều chuyển đi";
            case ADJUSTMENT -> "Điều chỉnh";
            case RETURN -> "Hoàn trả";
            case DAMAGED -> "Hàng hỏng";
            case ORDER_RESERVE -> "Tạm giữ đơn hàng";
            case ORDER_RELEASE -> "Giải phóng tạm giữ";
            case CANCEL_RELEASE -> "Hủy tạm giữ";
        };
    }

    @Transactional(readOnly = true)
    public List<InventoryIOSummaryResponse> getIOSummary(Long branchId, LocalDate startDate, LocalDate endDate) {
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(LocalTime.MAX);

        List<InventoryTransactionRepository.MovementProjection> movements = inventoryTransactionRepository
                .findMovementSummary(PHYSICAL_TRANSACTION_TYPES, branchId, start, end);
        // "Tồn cuối kỳ" trước đây lấy thẳng tồn kho HIỆN TẠI (bây giờ), bất kể endDate chọn là ngày
        // nào — đúng khi endDate = hôm nay, nhưng SAI ngay khi xem 1 kỳ trong quá khứ (mọi giao dịch
        // phát sinh sau endDate vẫn bị tính vào, làm cả "tồn cuối kỳ" lẫn "tồn đầu kỳ" suy ra từ đó
        // đều lệch). Cách sửa: lấy tồn hiện tại rồi TRỪ NGƯỢC toàn bộ biến động phát sinh SAU
        // endDate — ra đúng tồn tại đúng thời điểm endDate mà không cần dựng lại lịch sử theo từng lô.
        List<InventoryTransactionRepository.MovementProjection> movementsAfterEnd = inventoryTransactionRepository
                .findMovementAfterDate(PHYSICAL_TRANSACTION_TYPES, branchId, end);
        List<InventoryRepository.VariantStockProjection> currentStock = inventoryRepository
                .findCurrentStockByBranch(branchId);

        Map<Long, InventoryTransactionRepository.MovementProjection> movementByVariant = new HashMap<>();
        for (InventoryTransactionRepository.MovementProjection m : movements) {
            movementByVariant.put(m.getVariantId(), m);
        }
        Map<Long, InventoryTransactionRepository.MovementProjection> movementAfterEndByVariant = new HashMap<>();
        for (InventoryTransactionRepository.MovementProjection m : movementsAfterEnd) {
            movementAfterEndByVariant.put(m.getVariantId(), m);
        }
        Map<Long, InventoryRepository.VariantStockProjection> stockByVariant = new HashMap<>();
        for (InventoryRepository.VariantStockProjection s : currentStock) {
            stockByVariant.put(s.getVariantId(), s);
        }

        Set<Long> variantIds = new java.util.LinkedHashSet<>();
        variantIds.addAll(movementByVariant.keySet());
        variantIds.addAll(movementAfterEndByVariant.keySet());
        variantIds.addAll(stockByVariant.keySet());

        if (variantIds.isEmpty()) {
            return List.of();
        }

        List<InventoryIOSummaryResponse> result = new ArrayList<>();
        for (Long variantId : variantIds) {
            InventoryTransactionRepository.MovementProjection movement = movementByVariant.get(variantId);
            InventoryTransactionRepository.MovementProjection movementAfterEnd = movementAfterEndByVariant.get(variantId);
            InventoryRepository.VariantStockProjection stock = stockByVariant.get(variantId);

            long importedQty = movement != null ? safeLong(movement.getImportedQuantity()) : 0;
            long exportedQty = movement != null ? safeLong(movement.getExportedQuantity()) : 0;
            BigDecimal importedValue = movement != null ? safeDecimal(movement.getImportedValue()) : BigDecimal.ZERO;
            BigDecimal exportedValue = movement != null ? safeDecimal(movement.getExportedValue()) : BigDecimal.ZERO;

            long currentQty = stock != null ? safeLong(stock.getQuantity()) : 0;
            BigDecimal currentValue = stock != null ? safeDecimal(stock.getValue()) : BigDecimal.ZERO;

            long importedAfterEndQty = movementAfterEnd != null ? safeLong(movementAfterEnd.getImportedQuantity()) : 0;
            long exportedAfterEndQty = movementAfterEnd != null ? safeLong(movementAfterEnd.getExportedQuantity()) : 0;
            BigDecimal importedAfterEndValue = movementAfterEnd != null
                    ? safeDecimal(movementAfterEnd.getImportedValue()) : BigDecimal.ZERO;
            BigDecimal exportedAfterEndValue = movementAfterEnd != null
                    ? safeDecimal(movementAfterEnd.getExportedValue()) : BigDecimal.ZERO;

            long closingQty = currentQty - importedAfterEndQty + exportedAfterEndQty;
            BigDecimal closingValue = currentValue.subtract(importedAfterEndValue).add(exportedAfterEndValue);

            long openingQty = closingQty - importedQty + exportedQty;
            BigDecimal openingValue = closingValue.subtract(importedValue).add(exportedValue);

            String sku = stock != null ? stock.getSku()
                    : (movement != null ? movement.getSku() : (movementAfterEnd != null ? movementAfterEnd.getSku() : ""));
            String productName = stock != null ? stock.getProductName()
                    : (movement != null ? movement.getProductName()
                            : (movementAfterEnd != null ? movementAfterEnd.getProductName() : ""));

            result.add(InventoryIOSummaryResponse.builder()
                    .variantId(variantId)
                    .sku(sku)
                    .productName(productName)
                    .openingQuantity((int) openingQty)
                    .openingValue(openingValue)
                    .importedQuantity((int) importedQty)
                    .importedValue(importedValue)
                    .exportedQuantity((int) exportedQty)
                    .exportedValue(exportedValue)
                    .closingQuantity((int) closingQty)
                    .closingValue(closingValue)
                    .build());
        }

        result.sort((a, b) -> {
            String left = a.getProductName() != null ? a.getProductName() : "";
            String right = b.getProductName() != null ? b.getProductName() : "";
            return left.compareToIgnoreCase(right);
        });
        return result;
    }

    private long safeLong(Long value) {
        return value == null ? 0L : value;
    }

    private BigDecimal safeDecimal(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
