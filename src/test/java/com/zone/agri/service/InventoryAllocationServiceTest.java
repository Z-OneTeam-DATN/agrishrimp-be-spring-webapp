package com.zone.agri.service;

import com.zone.agri.dto.response.order.CartItemDto;
import com.zone.agri.dto.response.order.OrderItemDto;
import com.zone.agri.dto.response.order.OutOfStockItemDto;
import com.zone.agri.dto.response.order.SuggestedTransferDto;
import com.zone.agri.dto.response.order.SubOrderDraftDto;
import com.zone.agri.entity.Branch;
import com.zone.agri.entity.Inventory;
import com.zone.agri.entity.ProductVariant;
import com.zone.agri.entity.enums.VietnamRegion;
import com.zone.agri.repository.InventoryRepository;
import com.zone.agri.repository.InventoryTransactionRepository;
import com.zone.agri.service.BranchSearchService.BranchWithRealDistance;
import com.zone.agri.service.InventoryAllocationService.AllocationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class InventoryAllocationServiceTest {

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private InventoryTransactionRepository inventoryTransactionRepository;

    @Mock
    private SettingService settingService;

    @Mock
    private PublicSellingPriceService publicSellingPriceService;

    @Spy
    private VietnamRegionResolver vietnamRegionResolver = new VietnamRegionResolver();

    @InjectMocks
    private InventoryAllocationService allocationService;

    private Branch branch1;
    private Branch branch2;
    private ProductVariant varA;
    private ProductVariant varB;
    private Map<Long, ProductVariant> variantMap;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.when(settingService.getProfitMultiplier())
                .thenReturn(new BigDecimal("1.3"));
        org.mockito.Mockito.when(settingService.getProfitRoundingRuleRaw())
                .thenReturn("NONE");

        branch1 = Branch.builder()
                .name("Can Tho Store")
                .addressDetail("15 Mau Than, Can Tho")
                .branchType("STORE")
                .build();
        setId(branch1, 1L, "id");

        branch2 = Branch.builder()
                .name("Soc Trang Warehouse")
                .addressDetail("21 Tran Hung Dao, Soc Trang")
                .branchType("WAREHOUSE")
                .build();
        setId(branch2, 2L, "id");

        varA = ProductVariant.builder().sku("SKU-A").build();
        setId(varA, 101L, "id");

        varB = ProductVariant.builder().sku("SKU-B").build();
        setId(varB, 102L, "id");

        variantMap = Map.of(101L, varA, 102L, varB);

        org.mockito.Mockito.when(publicSellingPriceService.resolveDisplayedVariantPrice(
                org.mockito.ArgumentMatchers.any(ProductVariant.class)))
                .thenAnswer(invocation -> {
                    ProductVariant variant = invocation.getArgument(0);
                    if (variant == null || variant.getSku() == null) {
                        return BigDecimal.ZERO;
                    }
                    return switch (variant.getSku()) {
                        case "SKU-A" -> new BigDecimal("130000");
                        case "SKU-B" -> new BigDecimal("260000");
                        default -> BigDecimal.ZERO;
                    };
                });
    }

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

    @Test
    void allocate_case_a_singleBranchEnoughStock() {
        List<CartItemDto> cart = List.of(
                new CartItemDto(101L, 5),
                new CartItemDto(102L, 3)
        );

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

        BigDecimal expectedSubtotal = new BigDecimal("130000").multiply(BigDecimal.valueOf(5))
                .add(new BigDecimal("260000").multiply(BigDecimal.valueOf(3)));
        assertThat(subOrder.getSubtotal()).isEqualByComparingTo(expectedSubtotal);
    }

    @Disabled("Legacy split-across-branches expectation")
    @Test
    void allocate_case_b_choosesNearestBranchAndReportsMissingQuantity() {
        List<CartItemDto> cart = List.of(
                new CartItemDto(101L, 10),
                new CartItemDto(102L, 5)
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

        assertThat(result.subOrders()).hasSize(1);
        assertThat(result.outOfStockItems()).hasSize(1);

        SubOrderDraftDto subOrder1 = result.subOrders().get(0);
        assertThat(subOrder1.getBranchId()).isEqualTo(1L);

        OrderItemDto varAItem = subOrder1.getItems().stream()
                .filter(i -> i.getProductVariantId().equals(101L))
                .findFirst()
                .orElseThrow();
        assertThat(varAItem.getAllocatedQuantity()).isEqualTo(6);
        assertThat(varAItem.getMissingQuantity()).isEqualTo(4);

        OutOfStockItemDto outOfStock = result.outOfStockItems().get(0);
        assertThat(outOfStock.getProductVariantId()).isEqualTo(101L);
        assertThat(outOfStock.getRequestedQty()).isEqualTo(10);
        assertThat(outOfStock.getAvailableQty()).isEqualTo(8);
    }

    @Test
    void allocate_case_c_productNotAvailableAnywhere() {
        List<CartItemDto> cart = List.of(
                new CartItemDto(101L, 5),
                new CartItemDto(102L, 3)
        );

        Map<Long, Map<Long, List<Inventory>>> matrix = new HashMap<>();
        Map<Long, List<Inventory>> b1Stock = new HashMap<>();
        b1Stock.put(101L, new ArrayList<>(List.of(createBatch(1L, branch1, varA, 10, 100000))));
        matrix.put(1L, b1Stock);

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

    @Disabled("Legacy split-across-branches expectation")
    @Test
    void allocate_case_d_partialStockAcrossBranches() {
        List<CartItemDto> cart = List.of(
                new CartItemDto(101L, 20)
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

        assertThat(result.subOrders()).hasSize(1);
        assertThat(result.outOfStockItems()).hasSize(1);

        OrderItemDto allocatedItem = result.subOrders().get(0).getItems().get(0);
        assertThat(allocatedItem.getAllocatedQuantity()).isEqualTo(8);
        assertThat(allocatedItem.getMissingQuantity()).isEqualTo(12);

        OutOfStockItemDto outOfStock = result.outOfStockItems().get(0);
        assertThat(outOfStock.getRequestedQty()).isEqualTo(20);
        assertThat(outOfStock.getAvailableQty()).isEqualTo(7);
    }

    @Test
    void allocate_case_e_chooseFartherBranchWhenItAloneCanFulfillWholeCart() {
        List<CartItemDto> cart = List.of(
                new CartItemDto(101L, 5),
                new CartItemDto(102L, 5)
        );

        Map<Long, Map<Long, List<Inventory>>> matrix = new HashMap<>();

        Map<Long, List<Inventory>> b1Stock = new HashMap<>();
        b1Stock.put(101L, new ArrayList<>(List.of(createBatch(1L, branch1, varA, 5, 100000))));
        b1Stock.put(102L, new ArrayList<>(List.of(createBatch(2L, branch1, varB, 2, 200000))));
        matrix.put(1L, b1Stock);

        Map<Long, List<Inventory>> b2Stock = new HashMap<>();
        b2Stock.put(101L, new ArrayList<>(List.of(createBatch(3L, branch2, varA, 5, 100000))));
        b2Stock.put(102L, new ArrayList<>(List.of(createBatch(4L, branch2, varB, 5, 200000))));
        matrix.put(2L, b2Stock);

        List<BranchWithRealDistance> branches = List.of(
                new BranchWithRealDistance(branch1, 2.5, 300, 5.0),
                new BranchWithRealDistance(branch2, 50.0, 3600, 60.0)
        );

        AllocationResult result = allocationService.allocate(cart, variantMap, branches, matrix);

        assertThat(result.subOrders()).hasSize(1);
        assertThat(result.outOfStockItems()).isEmpty();

        SubOrderDraftDto subOrder = result.subOrders().get(0);
        assertThat(subOrder.getBranchId()).isEqualTo(2L);
        assertThat(subOrder.getItems()).allSatisfy(item -> assertThat(item.getMissingQuantity()).isZero());
    }

    @Test
    void allocate_case_f_noFullBranch_consolidatesAtNearestBranch() {
        List<CartItemDto> cart = List.of(
                new CartItemDto(101L, 10),
                new CartItemDto(102L, 5)
        );

        Map<Long, Map<Long, List<Inventory>>> matrix = new HashMap<>();

        Map<Long, List<Inventory>> b1Stock = new HashMap<>();
        b1Stock.put(101L, new ArrayList<>(List.of(createBatch(1L, branch1, varA, 6, 100000))));
        b1Stock.put(102L, new ArrayList<>(List.of(createBatch(2L, branch1, varB, 5, 200000))));
        matrix.put(1L, b1Stock);

        Map<Long, List<Inventory>> b2Stock = new HashMap<>();
        b2Stock.put(101L, new ArrayList<>(List.of(createBatch(3L, branch2, varA, 4, 100000))));
        matrix.put(2L, b2Stock);

        List<BranchWithRealDistance> branches = List.of(
                new BranchWithRealDistance(branch1, 2.5, 300, 5.0),
                new BranchWithRealDistance(branch2, 50.0, 3600, 60.0)
        );

        AllocationResult result = allocationService.allocate(cart, variantMap, branches, matrix);

        assertThat(result.subOrders()).hasSize(1);
        assertThat(result.outOfStockItems()).isEmpty();

        SubOrderDraftDto subOrder = result.subOrders().get(0);
        assertThat(subOrder.getBranchId()).isEqualTo(1L);

        OrderItemDto itemA = findItem(subOrder, 101L);
        OrderItemDto itemB = findItem(subOrder, 102L);
        assertThat(itemA.getAllocatedQuantity()).isEqualTo(6);
        assertThat(itemA.getMissingQuantity()).isEqualTo(4);
        assertThat(itemB.getAllocatedQuantity()).isEqualTo(5);
        assertThat(itemB.getMissingQuantity()).isZero();
    }

    @Test
    void allocate_case_g_networkShortage_marksOnlyTrueShortage() {
        List<CartItemDto> cart = List.of(
                new CartItemDto(101L, 20)
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

        assertThat(result.subOrders()).hasSize(1);
        assertThat(result.subOrders().get(0).getBranchId()).isEqualTo(1L);

        OrderItemDto itemA = findItem(result.subOrders().get(0), 101L);
        assertThat(itemA.getAllocatedQuantity()).isEqualTo(8);
        assertThat(itemA.getMissingQuantity()).isEqualTo(12);

        assertThat(result.outOfStockItems()).hasSize(1);
        OutOfStockItemDto outOfStock = result.outOfStockItems().get(0);
        assertThat(outOfStock.getProductVariantId()).isEqualTo(101L);
        assertThat(outOfStock.getRequestedQty()).isEqualTo(5);
        assertThat(outOfStock.getAvailableQty()).isZero();
    }

    @Test
    void allocate_case_h_reservedQuantityReducesPreparedAvailability() {
        List<CartItemDto> cart = List.of(
                new CartItemDto(101L, 5)
        );

        Map<Long, Map<Long, List<Inventory>>> matrix = new HashMap<>();
        Map<Long, List<Inventory>> b1Stock = new HashMap<>();
        Inventory reservedBatch = createBatch(1L, branch1, varA, 10, 100000);
        reservedBatch.setReservedQuantity(7);
        b1Stock.put(101L, new ArrayList<>(List.of(reservedBatch)));
        matrix.put(1L, b1Stock);

        List<BranchWithRealDistance> branches = List.of(
                new BranchWithRealDistance(branch1, 2.5, 300, 5.0)
        );

        AllocationResult result = allocationService.allocate(cart, variantMap, branches, matrix);

        assertThat(result.subOrders()).hasSize(1);
        OrderItemDto item = findItem(result.subOrders().get(0), 101L);
        assertThat(item.getAllocatedQuantity()).isEqualTo(3);
        assertThat(item.getMissingQuantity()).isEqualTo(2);

        assertThat(result.outOfStockItems()).singleElement().satisfies(outOfStock -> {
            assertThat(outOfStock.getProductVariantId()).isEqualTo(101L);
            assertThat(outOfStock.getRequestedQty()).isEqualTo(2);
            assertThat(outOfStock.getAvailableQty()).isZero();
        });
    }

    @Test
    void allocate_case_i_chooseNearestWarehouseWhenWarehouseAndStoreCanBothFulfill() {
        List<CartItemDto> cart = List.of(
                new CartItemDto(101L, 5),
                new CartItemDto(102L, 5)
        );

        Map<Long, Map<Long, List<Inventory>>> matrix = new HashMap<>();

        Map<Long, List<Inventory>> storeStock = new HashMap<>();
        storeStock.put(101L, new ArrayList<>(List.of(createBatch(1L, branch1, varA, 5, 100000))));
        storeStock.put(102L, new ArrayList<>(List.of(createBatch(2L, branch1, varB, 5, 200000))));
        matrix.put(1L, storeStock);

        Map<Long, List<Inventory>> warehouseStock = new HashMap<>();
        warehouseStock.put(101L, new ArrayList<>(List.of(createBatch(3L, branch2, varA, 5, 100000))));
        warehouseStock.put(102L, new ArrayList<>(List.of(createBatch(4L, branch2, varB, 5, 200000))));
        matrix.put(2L, warehouseStock);

        List<BranchWithRealDistance> branches = List.of(
                new BranchWithRealDistance(branch2, 1.5, 180, 3.0),
                new BranchWithRealDistance(branch1, 4.0, 480, 8.0)
        );

        AllocationResult result = allocationService.allocate(cart, variantMap, branches, matrix);

        assertThat(result.subOrders()).hasSize(1);
        assertThat(result.outOfStockItems()).isEmpty();
        assertThat(result.subOrders().get(0).getBranchId()).isEqualTo(2L);
    }

    @Test
    void allocate_case_j_chooseNearestStoreWhenStoreAndWarehouseCanBothFulfill() {
        List<CartItemDto> cart = List.of(
                new CartItemDto(101L, 5),
                new CartItemDto(102L, 5)
        );

        Map<Long, Map<Long, List<Inventory>>> matrix = new HashMap<>();

        Map<Long, List<Inventory>> storeStock = new HashMap<>();
        storeStock.put(101L, new ArrayList<>(List.of(createBatch(1L, branch1, varA, 5, 100000))));
        storeStock.put(102L, new ArrayList<>(List.of(createBatch(2L, branch1, varB, 5, 200000))));
        matrix.put(1L, storeStock);

        Map<Long, List<Inventory>> warehouseStock = new HashMap<>();
        warehouseStock.put(101L, new ArrayList<>(List.of(createBatch(3L, branch2, varA, 5, 100000))));
        warehouseStock.put(102L, new ArrayList<>(List.of(createBatch(4L, branch2, varB, 5, 200000))));
        matrix.put(2L, warehouseStock);

        List<BranchWithRealDistance> branches = List.of(
                new BranchWithRealDistance(branch1, 1.5, 180, 3.0),
                new BranchWithRealDistance(branch2, 4.0, 480, 8.0)
        );

        AllocationResult result = allocationService.allocate(cart, variantMap, branches, matrix);

        assertThat(result.subOrders()).hasSize(1);
        assertThat(result.outOfStockItems()).isEmpty();
        assertThat(result.subOrders().get(0).getBranchId()).isEqualTo(1L);
    }

    @Test
    void allocate_regionSelectsSouthServingBranchInsteadOfFarNorthFullStockBranch() {
        Branch caMauStore = branch(11L, "Chi nhanh Ca Mau", "STORE", "Tỉnh Cà Mau", 9.18, 105.15);
        Branch haNoiStore = branch(12L, "Chi nhanh Ha Noi", "STORE", "Thành phố Hà Nội", 21.03, 105.85);
        List<CartItemDto> cart = List.of(new CartItemDto(101L, 5));

        Map<Long, Map<Long, List<Inventory>>> matrix = new HashMap<>();
        matrix.put(11L, Map.of(101L, new ArrayList<>(List.of(createBatch(10L, caMauStore, varA, 2, 100000)))));
        matrix.put(12L, Map.of(101L, new ArrayList<>(List.of(createBatch(11L, haNoiStore, varA, 5, 100000)))));

        List<BranchWithRealDistance> branches = List.of(
                new BranchWithRealDistance(caMauStore, 2.0, 240, 4.0),
                new BranchWithRealDistance(haNoiStore, 1800.0, 86400, 1440.0)
        );

        AllocationResult result = allocationService.allocate(
                cart,
                Map.of(101L, varA),
                branches,
                matrix,
                VietnamRegion.SOUTH);

        assertThat(result.subOrders()).hasSize(1);
        assertThat(result.subOrders().get(0).getBranchId()).isEqualTo(11L);
        assertThat(result.customerRegion()).isEqualTo("SOUTH");
        assertThat(result.servingBranchRegion()).isEqualTo("SOUTH");
        assertThat(result.outOfStockItems()).singleElement().satisfies(outOfStock -> {
            assertThat(outOfStock.getProductVariantId()).isEqualTo(101L);
            assertThat(outOfStock.getRequestedQty()).isEqualTo(3);
        });
        assertThat(result.suggestedTransfers()).isEmpty();
    }

    @Test
    void allocate_regionUsesCentralAdjacentOnlyForSouthCustomer() {
        Branch caMauStore = branch(21L, "Chi nhanh Ca Mau", "STORE", "Tỉnh Cà Mau", 9.18, 105.15);
        Branch daNangWarehouse = branch(22L, "Kho Da Nang", "WAREHOUSE", "Thành phố Đà Nẵng", 16.05, 108.20);
        Branch haNoiStore = branch(23L, "Chi nhanh Ha Noi", "STORE", "Thành phố Hà Nội", 21.03, 105.85);
        List<CartItemDto> cart = List.of(new CartItemDto(101L, 5));

        Map<Long, Map<Long, List<Inventory>>> matrix = new HashMap<>();
        matrix.put(21L, Map.of(101L, new ArrayList<>(List.of(createBatch(20L, caMauStore, varA, 1, 100000)))));
        matrix.put(22L, Map.of(101L, new ArrayList<>(List.of(createBatch(21L, daNangWarehouse, varA, 4, 100000)))));
        matrix.put(23L, Map.of(101L, new ArrayList<>(List.of(createBatch(22L, haNoiStore, varA, 10, 100000)))));

        List<BranchWithRealDistance> branches = List.of(
                new BranchWithRealDistance(caMauStore, 1.0, 120, 2.0),
                new BranchWithRealDistance(daNangWarehouse, 900.0, 36000, 600.0),
                new BranchWithRealDistance(haNoiStore, 1800.0, 86400, 1440.0)
        );

        AllocationResult result = allocationService.allocate(
                cart,
                Map.of(101L, varA),
                branches,
                matrix,
                VietnamRegion.SOUTH);

        assertThat(result.subOrders()).hasSize(1);
        assertThat(result.subOrders().get(0).getBranchId()).isEqualTo(21L);
        assertThat(result.adjacentRegionUsed()).isEqualTo("CENTRAL");
        assertThat(result.outOfStockItems()).isEmpty();
        assertThat(result.suggestedTransfers()).singleElement().satisfies(transfer -> {
            assertThat(transfer.getFromBranchId()).isEqualTo(22L);
            assertThat(transfer.getQuantity()).isEqualTo(4);
            assertThat(transfer.getFromRegion()).isEqualTo("CENTRAL");
            assertThat(transfer.getToRegion()).isEqualTo("SOUTH");
        });
    }

    @Test
    void allocate_regionAllowsWarehouseServingBranchWhenNearestInCustomerRegion() {
        Branch southWarehouse = branch(31L, "Kho Can Tho", "WAREHOUSE", "Thành phố Cần Thơ", 10.02, 105.78);
        Branch southStore = branch(32L, "Chi nhanh Bac Lieu", "STORE", "Tỉnh Bạc Liêu", 9.29, 105.72);
        List<CartItemDto> cart = List.of(new CartItemDto(101L, 3));

        Map<Long, Map<Long, List<Inventory>>> matrix = new HashMap<>();
        matrix.put(31L, Map.of(101L, new ArrayList<>(List.of(createBatch(30L, southWarehouse, varA, 3, 100000)))));
        matrix.put(32L, Map.of(101L, new ArrayList<>(List.of(createBatch(31L, southStore, varA, 3, 100000)))));

        List<BranchWithRealDistance> branches = List.of(
                new BranchWithRealDistance(southWarehouse, 1.0, 120, 2.0),
                new BranchWithRealDistance(southStore, 3.0, 360, 6.0)
        );

        AllocationResult result = allocationService.allocate(
                cart,
                Map.of(101L, varA),
                branches,
                matrix,
                VietnamRegion.SOUTH);

        assertThat(result.subOrders()).hasSize(1);
        assertThat(result.subOrders().get(0).getBranchId()).isEqualTo(31L);
        assertThat(result.subOrders().get(0).getBranchRegion()).isEqualTo("SOUTH");
        assertThat(result.outOfStockItems()).isEmpty();
    }

    @Test
    void allocate_regionChoosesCloserAdjacentRegionForCentralCustomer() {
        Branch hueStore = branch(41L, "Chi nhanh Hue", "STORE", "Thừa Thiên Huế", 16.46, 107.59);
        Branch daNangWarehouse = branch(42L, "Kho Da Nang", "WAREHOUSE", "Thành phố Đà Nẵng", 16.05, 108.20);
        Branch haNoiStore = branch(43L, "Chi nhanh Ha Noi", "STORE", "Thành phố Hà Nội", 21.03, 105.85);
        Branch hcmStore = branch(44L, "Chi nhanh HCM", "STORE", "Thành phố Hồ Chí Minh", 10.78, 106.70);
        List<CartItemDto> cart = List.of(new CartItemDto(101L, 5));

        Map<Long, Map<Long, List<Inventory>>> matrix = new HashMap<>();
        matrix.put(42L, Map.of(101L, new ArrayList<>(List.of(createBatch(40L, daNangWarehouse, varA, 1, 100000)))));
        matrix.put(43L, Map.of(101L, new ArrayList<>(List.of(createBatch(41L, haNoiStore, varA, 4, 100000)))));
        matrix.put(44L, Map.of(101L, new ArrayList<>(List.of(createBatch(42L, hcmStore, varA, 4, 100000)))));

        List<BranchWithRealDistance> branches = List.of(
                new BranchWithRealDistance(hueStore, 1.0, 120, 2.0),
                new BranchWithRealDistance(daNangWarehouse, 2.0, 240, 4.0),
                new BranchWithRealDistance(haNoiStore, 700.0, 36000, 600.0),
                new BranchWithRealDistance(hcmStore, 1050.0, 54000, 900.0)
        );

        AllocationResult result = allocationService.allocate(
                cart,
                Map.of(101L, varA),
                branches,
                matrix,
                VietnamRegion.CENTRAL);

        assertThat(result.subOrders()).hasSize(1);
        assertThat(result.subOrders().get(0).getBranchId()).isEqualTo(41L);
        assertThat(result.adjacentRegionUsed()).isEqualTo("NORTH");
        assertThat(result.outOfStockItems()).isEmpty();
        assertThat(result.suggestedTransfers()).hasSize(2);
        assertThat(result.suggestedTransfers())
                .extracting(SuggestedTransferDto::getFromBranchId)
                .containsExactlyInAnyOrder(42L, 43L);
        assertThat(result.suggestedTransfers())
                .extracting(SuggestedTransferDto::getFromRegion)
                .containsExactlyInAnyOrder("CENTRAL", "NORTH");
    }

    @Test
    void allocate_regionUsesPartialAdjacentSupplyAndLeavesRemainingShortageForPr() {
        Branch caMauStore = branch(51L, "Chi nhanh Ca Mau", "STORE", "Tỉnh Cà Mau", 9.18, 105.15);
        Branch canThoWarehouse = branch(52L, "Kho Can Tho", "WAREHOUSE", "Thành phố Cần Thơ", 10.02, 105.78);
        Branch daNangWarehouse = branch(53L, "Kho Da Nang", "WAREHOUSE", "Thành phố Đà Nẵng", 16.05, 108.20);
        Branch haNoiStore = branch(54L, "Chi nhanh Ha Noi", "STORE", "Thành phố Hà Nội", 21.03, 105.85);
        List<CartItemDto> cart = List.of(new CartItemDto(101L, 6));

        Map<Long, Map<Long, List<Inventory>>> matrix = new HashMap<>();
        matrix.put(52L, Map.of(101L, new ArrayList<>(List.of(createBatch(50L, canThoWarehouse, varA, 1, 100000)))));
        matrix.put(53L, Map.of(101L, new ArrayList<>(List.of(createBatch(51L, daNangWarehouse, varA, 2, 100000)))));
        matrix.put(54L, Map.of(101L, new ArrayList<>(List.of(createBatch(52L, haNoiStore, varA, 10, 100000)))));

        List<BranchWithRealDistance> branches = List.of(
                new BranchWithRealDistance(caMauStore, 1.0, 120, 2.0),
                new BranchWithRealDistance(canThoWarehouse, 180.0, 7200, 120.0),
                new BranchWithRealDistance(daNangWarehouse, 900.0, 36000, 600.0),
                new BranchWithRealDistance(haNoiStore, 1800.0, 86400, 1440.0)
        );

        AllocationResult result = allocationService.allocate(
                cart,
                Map.of(101L, varA),
                branches,
                matrix,
                VietnamRegion.SOUTH);

        assertThat(result.subOrders()).hasSize(1);
        assertThat(result.subOrders().get(0).getBranchId()).isEqualTo(51L);
        assertThat(result.adjacentRegionUsed()).isEqualTo("CENTRAL");
        assertThat(result.suggestedTransfers()).extracting(SuggestedTransferDto::getFromBranchId)
                .containsExactly(52L, 53L);
        assertThat(result.outOfStockItems()).singleElement().satisfies(outOfStock -> {
            assertThat(outOfStock.getProductVariantId()).isEqualTo(101L);
            assertThat(outOfStock.getRequestedQty()).isEqualTo(3);
        });
    }

    private OrderItemDto findItem(SubOrderDraftDto subOrder, Long variantId) {
        return subOrder.getItems().stream()
                .filter(item -> variantId.equals(item.getProductVariantId()))
                .findFirst()
                .orElseThrow();
    }

    private Branch branch(Long id, String name, String branchType, String provinceName, Double lat, Double lng) {
        Branch branch = Branch.builder()
                .name(name)
                .branchType(branchType)
                .provinceName(provinceName)
                .lat(lat)
                .lng(lng)
                .build();
        setId(branch, id, "id");
        return branch;
    }

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
