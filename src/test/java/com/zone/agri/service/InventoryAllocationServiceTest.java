package com.zone.agri.service;

import com.zone.agri.dto.order.CartItemDto;
import com.zone.agri.dto.order.OutOfStockItemDto;
import com.zone.agri.dto.order.SubOrderDraftDto;
import com.zone.agri.entity.Branch;
import com.zone.agri.entity.Inventory;
import com.zone.agri.entity.ProductVariant;
import com.zone.agri.repository.InventoryRepository;
import com.zone.agri.service.BranchSearchService.BranchWithRealDistance;
import com.zone.agri.service.InventoryAllocationService.AllocationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class InventoryAllocationServiceTest {

    @Mock
    private InventoryRepository inventoryRepository;

    @InjectMocks
    private InventoryAllocationService allocationService;

    private Branch branch1, branch2;
    private ProductVariant varA, varB;
    private Map<Long, ProductVariant> variantMap;

    @BeforeEach
    void setUp() {
        branch1 = Branch.builder().build();
        setId(branch1, 1L, "id");
        branch1.setName("Chi Nhánh Cần Thơ");
        branch1.setAddressDetail("15 Mậu Thân, Cần Thơ");
        branch1.setDistrictId(1442);

        branch2 = Branch.builder().build();
        setId(branch2, 2L, "id");
        branch2.setName("Chi Nhánh Sóc Trăng");
        branch2.setAddressDetail("21 Trần Hưng Đạo, Sóc Trăng");
        branch2.setDistrictId(1444);

        // Biến thể không còn lưu giá, nhưng vẫn setup giả định để map
        varA = ProductVariant.builder().sku("SKU-A").build();
        setId(varA, 101L, "id");

        varB = ProductVariant.builder().sku("SKU-B").build();
        setId(varB, 102L, "id");

        variantMap = Map.of(101L, varA, 102L, varB);
    }

    // Helper tạo Lô hàng (Batch)
    private Inventory createBatch(Long id, Branch branch, ProductVariant variant, int qty, double importPrice) {
        Inventory inv = Inventory.builder()
                .branch(branch)
                .productVariant(variant)
                .quantity(qty)
                .importPrice(new BigDecimal(importPrice))
                .build();
        setId(inv, id, "id");
        return inv;
    }

    // ── Case a: 1 chi nhánh đủ hàng ──────────────────────────────

    @Test
    void allocate_case_a_singleBranchEnoughStock() {
        List<CartItemDto> cart = List.of(
                new CartItemDto(101L, 5),
                new CartItemDto(102L, 3)
        );

        // Map giả lập Lô Hàng:
        // Branch 1 có: varA(10 món, vốn 100k), varB(10 món, vốn 200k)
        Map<Long, Map<Long, List<Inventory>>> matrix = new HashMap<>();
        Map<Long, List<Inventory>> b1Stock = new HashMap<>();
        b1Stock.put(101L, new ArrayList<>(List.of(createBatch(1L, branch1, varA, 10, 100000))));
        b1Stock.put(102L, new ArrayList<>(List.of(createBatch(2L, branch1, varB, 10, 200000))));
        matrix.put(1L, b1Stock);

        List<BranchWithRealDistance> branches = List.of(
                new BranchWithRealDistance(branch1, 2.5, 300, 5.0)
        );

        AllocationResult result = allocationService.allocate(cart, variantMap, branches, matrix);

        assertThat(result.subOrders()).hasSize(1);
        assertThat(result.outOfStockItems()).isEmpty();

        SubOrderDraftDto subOrder = result.subOrders().get(0);
        assertThat(subOrder.getBranchId()).isEqualTo(1L);
        assertThat(subOrder.getItems()).hasSize(2);

        // Giá bán = vốn * 1.3 -> varA = 130k, varB = 260k
        BigDecimal expectedSubtotal = new BigDecimal("130000").multiply(BigDecimal.valueOf(5))
                .add(new BigDecimal("260000").multiply(BigDecimal.valueOf(3)));
        assertThat(subOrder.getSubtotal()).isEqualByComparingTo(expectedSubtotal);
    }

    // ── Case b: phải tách 2 chi nhánh ────────────────────────────

    @Test
    void allocate_case_b_splitAcrossTwoBranches() {
        List<CartItemDto> cart = List.of(
                new CartItemDto(101L, 10), // varA: branch1 chỉ có 6, branch2 có thêm 4
                new CartItemDto(102L, 5)   // varB: branch1 đủ
        );

        Map<Long, Map<Long, List<Inventory>>> matrix = new HashMap<>();

        Map<Long, List<Inventory>> b1Stock = new HashMap<>();
        b1Stock.put(101L, new ArrayList<>(List.of(createBatch(1L, branch1, varA, 6, 100000))));
        b1Stock.put(102L, new ArrayList<>(List.of(createBatch(2L, branch1, varB, 10, 200000))));
        matrix.put(1L, b1Stock);

        Map<Long, List<Inventory>> b2Stock = new HashMap<>();
        b2Stock.put(101L, new ArrayList<>(List.of(createBatch(3L, branch2, varA, 8, 100000))));
        matrix.put(2L, b2Stock);

        List<BranchWithRealDistance> branches = List.of(
                new BranchWithRealDistance(branch1, 2.5, 300, 5.0),
                new BranchWithRealDistance(branch2, 50.0, 3600, 60.0)
        );

        AllocationResult result = allocationService.allocate(cart, variantMap, branches, matrix);

        assertThat(result.subOrders()).hasSize(2);
        assertThat(result.outOfStockItems()).isEmpty();

        SubOrderDraftDto subOrder1 = result.subOrders().get(0);
        assertThat(subOrder1.getBranchId()).isEqualTo(1L);

        SubOrderDraftDto subOrder2 = result.subOrders().get(1);
        assertThat(subOrder2.getBranchId()).isEqualTo(2L);

        // Branch2 chỉ giao phần còn thiếu của varA (4 items)
        int varAInBranch2 = subOrder2.getItems().stream()
                .filter(i -> i.getProductVariantId().equals(101L))
                .mapToInt(i -> i.getQuantity())
                .sum();
        assertThat(varAInBranch2).isEqualTo(4);
    }

    // ── Case c: 1 sản phẩm không có ở đâu ───────────────────────

    @Test
    void allocate_case_c_productNotAvailableAnywhere() {
        List<CartItemDto> cart = List.of(
                new CartItemDto(101L, 5),  // có hàng
                new CartItemDto(102L, 3)   // không có ở đâu
        );

        Map<Long, Map<Long, List<Inventory>>> matrix = new HashMap<>();
        Map<Long, List<Inventory>> b1Stock = new HashMap<>();
        b1Stock.put(101L, new ArrayList<>(List.of(createBatch(1L, branch1, varA, 10, 100000))));
        matrix.put(1L, b1Stock); // Không có varB

        List<BranchWithRealDistance> branches = List.of(
                new BranchWithRealDistance(branch1, 2.5, 300, 5.0)
        );

        AllocationResult result = allocationService.allocate(cart, variantMap, branches, matrix);

        assertThat(result.subOrders()).hasSize(1);
        assertThat(result.outOfStockItems()).hasSize(1);

        OutOfStockItemDto outOfStock = result.outOfStockItems().get(0);
        assertThat(outOfStock.getProductVariantId()).isEqualTo(102L);
        assertThat(outOfStock.getRequestedQty()).isEqualTo(3);
        assertThat(outOfStock.getAvailableQty()).isEqualTo(0);
    }

    // ── Case d: có hàng nhưng không đủ số lượng (partial) ────────

    @Test
    void allocate_case_d_partialStockAcrossBranches() {
        List<CartItemDto> cart = List.of(
                new CartItemDto(101L, 20) // cần 20, branch1 có 8, branch2 có 7 → tổng 15 < 20
        );

        Map<Long, Map<Long, List<Inventory>>> matrix = new HashMap<>();
        Map<Long, List<Inventory>> b1Stock = new HashMap<>();
        b1Stock.put(101L, new ArrayList<>(List.of(createBatch(1L, branch1, varA, 8, 100000))));
        matrix.put(1L, b1Stock);

        Map<Long, List<Inventory>> b2Stock = new HashMap<>();
        b2Stock.put(101L, new ArrayList<>(List.of(createBatch(2L, branch2, varA, 7, 100000))));
        matrix.put(2L, b2Stock);

        List<BranchWithRealDistance> branches = List.of(
                new BranchWithRealDistance(branch1, 2.5, 300, 5.0),
                new BranchWithRealDistance(branch2, 50.0, 3600, 60.0)
        );

        AllocationResult result = allocationService.allocate(cart, variantMap, branches, matrix);

        // 2 sub-orders: branch1 giao 8, branch2 giao 7
        assertThat(result.subOrders()).hasSize(2);
        // 5 cái còn lại không có ở đâu
        assertThat(result.outOfStockItems()).hasSize(1);

        OutOfStockItemDto outOfStock = result.outOfStockItems().get(0);
        assertThat(outOfStock.getRequestedQty()).isEqualTo(5); // 20 - 8 - 7
        assertThat(outOfStock.getAvailableQty()).isEqualTo(0); // Số dư còn lại là 0
    }

    // ── Helper method ─────────────────────────────────────────────

    @SuppressWarnings("SameParameterValue")
    private void setId(Object obj, Long id, String fieldName) {
        try {
            java.lang.reflect.Field f = getField(obj.getClass(), fieldName);
            f.setAccessible(true);
            f.set(obj, id);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set id via reflection: " + e.getMessage(), e);
        }
    }

    private java.lang.reflect.Field getField(Class<?> clazz, String name) throws NoSuchFieldException {
        try {
            return clazz.getDeclaredField(name);
        } catch (NoSuchFieldException e) {
            if (clazz.getSuperclass() != null) {
                return getField(clazz.getSuperclass(), name);
            }
            throw e;
        }
    }
}