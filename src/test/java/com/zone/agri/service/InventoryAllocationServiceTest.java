package com.zone.agri.service;

import com.zone.agri.dto.order.CartItemDto;
import com.zone.agri.dto.order.OutOfStockItemDto;
import com.zone.agri.dto.order.SubOrderDraftDto;
import com.zone.agri.entity.Branch;
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
        // Set id via reflection workaround - use setter
        setId(branch1, 1L, "id");
        branch1.setName("Chi Nhánh Cần Thơ");
        branch1.setAddressDetail("15 Mậu Thân, Cần Thơ");
        branch1.setDistrictId(1442);

        branch2 = Branch.builder().build();
        setId(branch2, 2L, "id");
        branch2.setName("Chi Nhánh Sóc Trăng");
        branch2.setAddressDetail("21 Trần Hưng Đạo, Sóc Trăng");
        branch2.setDistrictId(1444);

        varA = ProductVariant.builder().sku("SKU-A").price(new BigDecimal("100000")).build();
        setId(varA, 101L, "id");

        varB = ProductVariant.builder().sku("SKU-B").price(new BigDecimal("200000")).build();
        setId(varB, 102L, "id");

        variantMap = Map.of(101L, varA, 102L, varB);
    }

    // ── Case a: 1 chi nhánh đủ hàng ──────────────────────────────

    @Test
    void allocate_case_a_singleBranchEnoughStock() {
        List<CartItemDto> cart = List.of(
                new CartItemDto(101L, 5),
                new CartItemDto(102L, 3)
        );

        Map<Long, Map<Long, Integer>> matrix = new HashMap<>();
        matrix.put(1L, new HashMap<>(Map.of(101L, 10, 102L, 10)));

        List<BranchWithRealDistance> branches = List.of(
                new BranchWithRealDistance(branch1, 2.5, 300, 5.0)
        );

        AllocationResult result = allocationService.allocate(cart, variantMap, branches, matrix);

        assertThat(result.subOrders()).hasSize(1);
        assertThat(result.outOfStockItems()).isEmpty();

        SubOrderDraftDto subOrder = result.subOrders().get(0);
        assertThat(subOrder.getBranchId()).isEqualTo(1L);
        assertThat(subOrder.getItems()).hasSize(2);
        assertThat(subOrder.getSubtotal())
                .isEqualByComparingTo(new BigDecimal("100000").multiply(BigDecimal.valueOf(5))
                        .add(new BigDecimal("200000").multiply(BigDecimal.valueOf(3))));
    }

    // ── Case b: phải tách 2 chi nhánh ────────────────────────────

    @Test
    void allocate_case_b_splitAcrossTwoBranches() {
        List<CartItemDto> cart = List.of(
                new CartItemDto(101L, 10), // varA: branch1 chỉ có 6, branch2 có thêm 4
                new CartItemDto(102L, 5)   // varB: branch1 đủ
        );

        Map<Long, Map<Long, Integer>> matrix = new HashMap<>();
        matrix.put(1L, new HashMap<>(Map.of(101L, 6, 102L, 10)));
        matrix.put(2L, new HashMap<>(Map.of(101L, 8, 102L, 0)));

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

        Map<Long, Map<Long, Integer>> matrix = new HashMap<>();
        matrix.put(1L, new HashMap<>(Map.of(101L, 10))); // không có varB

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

        Map<Long, Map<Long, Integer>> matrix = new HashMap<>();
        matrix.put(1L, new HashMap<>(Map.of(101L, 8)));
        matrix.put(2L, new HashMap<>(Map.of(101L, 7)));

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
        assertThat(outOfStock.getAvailableQty()).isEqualTo(0);
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
