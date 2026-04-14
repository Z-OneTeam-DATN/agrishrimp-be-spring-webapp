package com.zone.agri.service;

import com.zone.agri.dto.response.order.CartItemDto;
import com.zone.agri.dto.response.order.OrderItemDto;
import com.zone.agri.dto.response.order.OutOfStockItemDto;
import com.zone.agri.dto.response.order.SubOrderDraftDto;
import com.zone.agri.entity.Inventory;
import com.zone.agri.entity.ProductVariant;
import com.zone.agri.repository.InventoryRepository;
import com.zone.agri.service.BranchSearchService.BranchWithRealDistance;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

/**
 * Thuật toán Greedy kết hợp Lô hàng (FIFO).
 * Tự động tính giá bán = giá vốn của lô * % Lợi nhuận hệ thống
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryAllocationService {

    private final InventoryRepository inventoryRepository;
    private final SettingService settingService; // 👉 BỔ SUNG SETTING SERVICE Ở ĐÂY

    public record AllocationResult(
            List<SubOrderDraftDto> subOrders,
            List<OutOfStockItemDto> outOfStockItems) {
    }

    // ──────────────────────────────────────────────────────────────
    // Step 1: Build Inventory Matrix (Lưu Danh sách Lô hàng thay vì số lượng)
    // ──────────────────────────────────────────────────────────────
    public Map<Long, Map<Long, List<Inventory>>> buildInventoryMatrix(List<Long> branchIds, List<Long> variantIds) {
        // Dùng raw query để giữ nguyên từng lô riêng lẻ với importPrice đúng.
        // KHÔNG dùng findInventoryMatrix vì hàm đó gom lô → importPrice bị null → giá =
        // 0đ.
        List<Inventory> inventories = inventoryRepository.rawFindInventoryMatrix(branchIds, variantIds);

        // Map<BranchId, Map<VariantId, Danh_Sách_Lô_FIFO>>
        Map<Long, Map<Long, List<Inventory>>> matrix = new HashMap<>();
        for (Inventory inv : inventories) {
            Long branchId = inv.getBranch().getId();
            Long variantId = inv.getProductVariant().getId();

            matrix.computeIfAbsent(branchId, k -> new HashMap<>())
                    .computeIfAbsent(variantId, k -> new ArrayList<>())
                    .add(inv); // ORDER BY i.id ASC từ query → lô cũ nhất (nhập trước) xuất trước
        }
        return matrix;
    }

    // ──────────────────────────────────────────────────────────────
    // Step 2: Greedy Allocation với Lô Hàng
    // ──────────────────────────────────────────────────────────────
    public AllocationResult allocate(
            List<CartItemDto> cart,
            Map<Long, ProductVariant> variantMap,
            List<BranchWithRealDistance> branchesSortedByDist,
            Map<Long, Map<Long, List<Inventory>>> inventoryMatrix) {
        // Lấy hệ số lợi nhuận 1 lần duy nhất — tránh N query DB trong vòng lặp lô
        BigDecimal profitMultiplier = settingService.getProfitMultiplier();
        String roundingRule = settingService.getProfitRoundingRuleRaw();

        List<SubOrderDraftDto> subOrders = new ArrayList<>();
        List<OutOfStockItemDto> outOfStockItems = new ArrayList<>();

        if (branchesSortedByDist.isEmpty() || cart.isEmpty()) {
            return new AllocationResult(subOrders, outOfStockItems);
        }

        // 👉 THUẬT TOÁN MỚI: Ưu tiên chọn chi nhánh ĐỦ HÀNG trước (không quan trọng xa gần)
        BranchWithRealDistance selectedBwr = null;
        for (BranchWithRealDistance bwr : branchesSortedByDist) {
            if (isBranchFullyStocked(bwr.branch().getId(), cart, inventoryMatrix)) {
                selectedBwr = bwr;
                break; // Tìm thấy chi nhánh đủ 100% hàng -> Chốt ngay!
            }
        }
        
        // Nếu không có chi nhánh nào đủ 100% hàng -> Fallback về chi nhánh gần nhất để chờ điều chuyển
        if (selectedBwr == null) {
            selectedBwr = branchesSortedByDist.get(0);
        }

        Long selectedBranchId = selectedBwr.branch().getId();
        Map<Long, List<Inventory>> branchBatches = inventoryMatrix.getOrDefault(selectedBranchId, Collections.emptyMap());

        List<OrderItemDto> allocatedItems = new ArrayList<>();

        for (CartItemDto item : cart) {
            Long variantId = item.getProductVariantId();
            int requested = item.getQuantity();
            int originalRequested = requested;

            List<Inventory> batches = branchBatches.getOrDefault(variantId, new ArrayList<>());
            ProductVariant variant = variantMap.get(variantId);
            String variantName = (variant != null && variant.getSku() != null) ? variant.getSku() : "Unknown";
            String variantSku = variant != null ? variant.getSku() : "";

            int totalAllocatedForThisItem = 0;
            BigDecimal lastUnitPrice = BigDecimal.ZERO;

            // Duyệt FIFO lấy hàng trong kho gần nhất
            Iterator<Inventory> batchIterator = batches.iterator();
            while (batchIterator.hasNext() && requested > 0) {
                Inventory batch = batchIterator.next();
                int availableInBatch = batch.getQuantity();
                if (availableInBatch <= 0)
                    continue;

                int quantityToTake = Math.min(requested, availableInBatch);

                BigDecimal importPrice = batch.getImportPrice() != null ? batch.getImportPrice() : BigDecimal.ZERO;
                lastUnitPrice = settingService.calculateSellingPrice(importPrice, profitMultiplier, roundingRule);

                totalAllocatedForThisItem += quantityToTake;
                batch.setQuantity(availableInBatch - quantityToTake);
                requested -= quantityToTake;
            }

            // Nếu chi nhánh này hoàn toàn hết hàng, tìm giá dự kiến từ các chi nhánh khác
            if (lastUnitPrice.compareTo(BigDecimal.ZERO) == 0) {
                lastUnitPrice = resolveFallbackUnitPrice(variantId, inventoryMatrix, profitMultiplier, roundingRule);
            }

            allocatedItems.add(OrderItemDto.builder()
                    .productVariantId(variantId)
                    .variantName(variantName)
                    .variantSku(variantSku)
                    .quantity(originalRequested)
                    .allocatedQuantity(totalAllocatedForThisItem)
                    .missingQuantity(requested) // Ghi nhận số lượng thiếu để xin điều chuyển
                    .unitPrice(lastUnitPrice)
                    .subtotal(lastUnitPrice.multiply(BigDecimal.valueOf(originalRequested)))
                    .build());

            if (requested > 0) {
                int totalAvailableSystemWide = calculateTotalAvailable(variantId, inventoryMatrix);
                outOfStockItems.add(OutOfStockItemDto.builder()
                        .productVariantId(variantId)
                        .variantName(variantName)
                        .variantSku(variantSku)
                        .requestedQty(originalRequested)
                        .availableQty(totalAvailableSystemWide)
                        .build());
            }
        }

        if (!allocatedItems.isEmpty()) {
            BigDecimal subtotal = allocatedItems.stream()
                    .map(OrderItemDto::getSubtotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            subOrders.add(SubOrderDraftDto.builder()
                    .branchId(selectedBranchId)
                    .branchName(selectedBwr.branch().getName())
                    .branchAddress(selectedBwr.branch().getAddressDetail())
                    .fromDistrictId(selectedBwr.branch().getDistrictId())
                    .durationMinutes(selectedBwr.durationMinutes())
                    .distanceKm(selectedBwr.distanceKm())
                    .items(allocatedItems)
                    .subtotal(subtotal)
                    .shippingFee(BigDecimal.ZERO)
                    .build());
        }

        return new AllocationResult(subOrders, outOfStockItems);
    }

    private BigDecimal resolveFallbackUnitPrice(Long variantId, Map<Long, Map<Long, List<Inventory>>> matrix, BigDecimal multiplier, String roundingRule) {
        for (Map<Long, List<Inventory>> branchMap : matrix.values()) {
            List<Inventory> batches = branchMap.getOrDefault(variantId, Collections.emptyList());
            for (Inventory batch : batches) {
                if (batch.getImportPrice() != null && batch.getImportPrice().compareTo(BigDecimal.ZERO) > 0) {
                    return settingService.calculateSellingPrice(batch.getImportPrice(), multiplier, roundingRule);
                }
            }
        }
        return BigDecimal.ZERO;
    }

    // Hàm helper kiểm tra xem một chi nhánh có đủ 100% số lượng cho toàn bộ giỏ hàng hay không
    private boolean isBranchFullyStocked(Long branchId, List<CartItemDto> cart, Map<Long, Map<Long, List<Inventory>>> matrix) {
        Map<Long, List<Inventory>> branchBatches = matrix.getOrDefault(branchId, Collections.emptyMap());
        for (CartItemDto item : cart) {
            int totalAvailable = branchBatches.getOrDefault(item.getProductVariantId(), Collections.emptyList())
                    .stream().mapToInt(inv -> inv.getQuantity() != null ? inv.getQuantity() : 0).sum();
            if (totalAvailable < item.getQuantity()) {
                return false; // Chỉ cần 1 sản phẩm bị thiếu là coi như chi nhánh này không đủ hàng
            }
        }
        return true;
    }

    private int calculateTotalAvailable(Long variantId, Map<Long, Map<Long, List<Inventory>>> matrix) {
        return matrix.values().stream()
                .flatMap(branchMap -> branchMap.getOrDefault(variantId, Collections.emptyList()).stream())
                .mapToInt(Inventory::getQuantity)
                .sum();
    }
}
