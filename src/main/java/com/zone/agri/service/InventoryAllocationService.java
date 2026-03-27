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
            List<OutOfStockItemDto> outOfStockItems
    ) {}

    // ──────────────────────────────────────────────────────────────
    // Step 1: Build Inventory Matrix (Lưu Danh sách Lô hàng thay vì số lượng)
    // ──────────────────────────────────────────────────────────────
    public Map<Long, Map<Long, List<Inventory>>> buildInventoryMatrix(List<Long> branchIds, List<Long> variantIds) {
        // Dùng raw query để giữ nguyên từng lô riêng lẻ với importPrice đúng.
        // KHÔNG dùng findInventoryMatrix vì hàm đó gom lô → importPrice bị null → giá = 0đ.
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
            Map<Long, Map<Long, List<Inventory>>> inventoryMatrix
    ) {
        // Lấy hệ số lợi nhuận 1 lần duy nhất — tránh N query DB trong vòng lặp lô
        BigDecimal profitMultiplier = settingService.getProfitMultiplier();

        List<CartItemDto> remaining = new ArrayList<>(cart.stream()
                .map(item -> new CartItemDto(item.getProductVariantId(), item.getQuantity()))
                .toList());

        List<SubOrderDraftDto> subOrders = new ArrayList<>();

        for (BranchWithRealDistance bwr : branchesSortedByDist) {
            if (remaining.isEmpty()) break;

            Long branchId = bwr.branch().getId();
            Map<Long, List<Inventory>> branchBatches = inventoryMatrix.getOrDefault(branchId, Collections.emptyMap());

            List<OrderItemDto> allocated = new ArrayList<>();
            List<CartItemDto> stillRemaining = new ArrayList<>();

            for (CartItemDto item : remaining) {
                Long variantId = item.getProductVariantId();
                int requested = item.getQuantity();

                List<Inventory> batches = branchBatches.getOrDefault(variantId, new ArrayList<>());
                ProductVariant variant = variantMap.get(variantId);
                String variantName = (variant != null && variant.getSku() != null) ? variant.getSku() : "Unknown";
                String variantSku = variant != null ? variant.getSku() : "";

                // Duyệt qua từng LÔ HÀNG để lấy hàng (FIFO)
                Iterator<Inventory> batchIterator = batches.iterator();
                while (batchIterator.hasNext() && requested > 0) {
                    Inventory batch = batchIterator.next();
                    int availableInBatch = batch.getQuantity();
                    if (availableInBatch <= 0) continue;

                    int quantityToTake = Math.min(requested, availableInBatch);

                    // Giá bán = giá vốn lô này × biên lợi nhuận admin cài (FIFO: lô cũ nhất ra trước)
                    BigDecimal importPrice = batch.getImportPrice() != null ? batch.getImportPrice() : BigDecimal.ZERO;
                    BigDecimal unitPrice = importPrice.multiply(profitMultiplier);

                    allocated.add(OrderItemDto.builder()
                            .productVariantId(variantId)
                            .variantName(variantName)
                            .variantSku(variantSku)
                            .quantity(quantityToTake)
                            .unitPrice(unitPrice)
                            .subtotal(unitPrice.multiply(BigDecimal.valueOf(quantityToTake)))
                            .build());

                    // Trừ tồn kho ảo trên RAM
                    batch.setQuantity(availableInBatch - quantityToTake);
                    requested -= quantityToTake;
                }

                if (requested > 0) {
                    stillRemaining.add(new CartItemDto(variantId, requested)); // Vẫn còn thiếu
                }
            }

            if (!allocated.isEmpty()) {
                BigDecimal subtotal = allocated.stream()
                        .map(OrderItemDto::getSubtotal)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                subOrders.add(SubOrderDraftDto.builder()
                        .branchId(branchId)
                        .branchName(bwr.branch().getName())
                        .branchAddress(bwr.branch().getAddressDetail())
                        .fromDistrictId(bwr.branch().getDistrictId())
                        .durationMinutes(bwr.durationMinutes())
                        .distanceKm(bwr.distanceKm())
                        .items(allocated)
                        .subtotal(subtotal)
                        .shippingFee(BigDecimal.ZERO)
                        .build());
            }

            remaining = stillRemaining; // Chuyển phần thiếu sang chi nhánh tiếp theo
        }

        // Tạo danh sách OutOfStock cho các item không thể đáp ứng đủ
        List<OutOfStockItemDto> outOfStockItems = remaining.stream()
                .map(item -> {
                    ProductVariant v = variantMap.get(item.getProductVariantId());
                    int totalAvailable = calculateTotalAvailable(item.getProductVariantId(), inventoryMatrix);
                    return OutOfStockItemDto.builder()
                            .productVariantId(item.getProductVariantId())
                            .variantName(v != null ? v.getSku() : "Unknown")
                            .variantSku(v != null ? v.getSku() : "")
                            .requestedQty(item.getQuantity())
                            .availableQty(totalAvailable)
                            .build();
                })
                .toList();

        return new AllocationResult(subOrders, outOfStockItems);
    }

    private int calculateTotalAvailable(Long variantId, Map<Long, Map<Long, List<Inventory>>> matrix) {
        return matrix.values().stream()
                .flatMap(branchMap -> branchMap.getOrDefault(variantId, Collections.emptyList()).stream())
                .mapToInt(Inventory::getQuantity)
                .sum();
    }
}