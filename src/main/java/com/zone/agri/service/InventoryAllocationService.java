package com.zone.agri.service;

import com.zone.agri.dto.order.CartItemDto;
import com.zone.agri.dto.order.OrderItemDto;
import com.zone.agri.dto.order.OutOfStockItemDto;
import com.zone.agri.dto.order.SubOrderDraftDto;
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
 * Thuật toán Greedy tách đơn thông minh.
 * Duyệt danh sách chi nhánh từ gần → xa, phân bổ hàng hóa tối đa tại từng chi nhánh.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryAllocationService {

    private final InventoryRepository inventoryRepository;

    /**
     * Kết quả phân bổ tồn kho.
     */
    public record AllocationResult(
            List<SubOrderDraftDto> subOrders,
            List<OutOfStockItemDto> outOfStockItems
    ) {}

    // ──────────────────────────────────────────────────────────────
    // Step 1: Build Inventory Matrix từ DB (1 query duy nhất)
    // ──────────────────────────────────────────────────────────────

    /**
     * @return Map&lt;branchId, Map&lt;variantId, quantity&gt;&gt;
     */
    public Map<Long, Map<Long, Integer>> buildInventoryMatrix(List<Long> branchIds, List<Long> variantIds) {
        List<Inventory> inventories = inventoryRepository.findInventoryMatrix(branchIds, variantIds);

        Map<Long, Map<Long, Integer>> matrix = new HashMap<>();
        for (Inventory inv : inventories) {
            Long branchId = inv.getBranch().getId();
            Long variantId = inv.getProductVariant().getId();
            matrix.computeIfAbsent(branchId, k -> new HashMap<>())
                    .put(variantId, inv.getQuantity() != null ? inv.getQuantity() : 0);
        }
        return matrix;
    }

    // ──────────────────────────────────────────────────────────────
    // Step 2: Greedy Allocation
    // ──────────────────────────────────────────────────────────────

    /**
     * Phân bổ giỏ hàng vào các chi nhánh theo thứ tự gần → xa.
     *
     * @param cart                  giỏ hàng (variantId + quantity)
     * @param variantMap            map variantId → ProductVariant entity (để lấy tên, giá)
     * @param branchesSortedByDist  danh sách chi nhánh đã sort theo duration
     * @param inventoryMatrix       kết quả buildInventoryMatrix()
     * @return AllocationResult (subOrders + outOfStockItems)
     */
    public AllocationResult allocate(
            List<CartItemDto> cart,
            Map<Long, ProductVariant> variantMap,
            List<BranchWithRealDistance> branchesSortedByDist,
            Map<Long, Map<Long, Integer>> inventoryMatrix
    ) {
        // remaining = deep copy của giỏ hàng còn chưa phân bổ
        List<CartItemDto> remaining = new ArrayList<>(cart.stream()
                .map(item -> new CartItemDto(item.getProductVariantId(), item.getQuantity()))
                .toList());

        List<SubOrderDraftDto> subOrders = new ArrayList<>();

        for (BranchWithRealDistance bwr : branchesSortedByDist) {
            if (remaining.isEmpty()) break;

            Long branchId = bwr.branch().getId();
            Map<Long, Integer> branchStock = inventoryMatrix.getOrDefault(branchId, Collections.emptyMap());

            List<OrderItemDto> allocated = new ArrayList<>();
            List<CartItemDto> stillRemaining = new ArrayList<>();

            for (CartItemDto item : remaining) {
                Long variantId = item.getProductVariantId();
                int requested = item.getQuantity();
                int stock = branchStock.getOrDefault(variantId, 0);

                ProductVariant variant = variantMap.get(variantId);
                BigDecimal unitPrice = (variant != null && variant.getPrice() != null)
                        ? variant.getPrice() : BigDecimal.ZERO;
                String variantName = (variant != null && variant.getSku() != null)
                        ? variant.getSku() : "Unknown";
                String variantSku = variant != null ? variant.getSku() : "";

                if (stock >= requested) {
                    // Chi nhánh đủ hàng — phân bổ toàn bộ
                    allocated.add(OrderItemDto.builder()
                            .productVariantId(variantId)
                            .variantName(variantName)
                            .variantSku(variantSku)
                            .quantity(requested)
                            .unitPrice(unitPrice)
                            .subtotal(unitPrice.multiply(BigDecimal.valueOf(requested)))
                            .build());
                    branchStock.put(variantId, stock - requested); // cập nhật tồn kho đã tiêu thụ
                } else if (stock > 0) {
                    // Chi nhánh có hàng nhưng không đủ — phân bổ một phần
                    allocated.add(OrderItemDto.builder()
                            .productVariantId(variantId)
                            .variantName(variantName)
                            .variantSku(variantSku)
                            .quantity(stock)
                            .unitPrice(unitPrice)
                            .subtotal(unitPrice.multiply(BigDecimal.valueOf(stock)))
                            .build());
                    branchStock.put(variantId, 0); // toàn bộ tồn kho đã tiêu thụ
                    stillRemaining.add(new CartItemDto(variantId, requested - stock));
                } else {
                    // Chi nhánh không có hàng
                    stillRemaining.add(item);
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
                        .shippingFee(BigDecimal.ZERO) // sẽ được fill bởi ShippingService
                        .build());
            }

            remaining = stillRemaining;
        }

        // remaining còn lại = hàng không có ở bất kỳ chi nhánh nào
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

    private int calculateTotalAvailable(Long variantId, Map<Long, Map<Long, Integer>> matrix) {
        return matrix.values().stream()
                .mapToInt(branchMap -> branchMap.getOrDefault(variantId, 0))
                .sum();
    }
}
