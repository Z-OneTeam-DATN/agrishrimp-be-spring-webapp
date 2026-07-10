package com.zone.agri.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.zone.agri.common.WarehouseContext;
import com.zone.agri.entity.Branch;
import com.zone.agri.entity.InventoryTransfer;
import com.zone.agri.entity.Order;
import com.zone.agri.entity.ProductVariant;
import com.zone.agri.entity.SubOrder;
import com.zone.agri.entity.SubOrderItem;
import com.zone.agri.entity.enums.InventoryTransferStatus;
import com.zone.agri.entity.enums.OrderStatus;
import com.zone.agri.repository.BranchRepository;
import com.zone.agri.repository.InventoryRepository;
import com.zone.agri.repository.InventoryTransactionRepository;
import com.zone.agri.repository.InventoryTransferRepository;
import com.zone.agri.repository.ProductVariantRepository;
import com.zone.agri.repository.SubOrderRepository;

@ExtendWith(MockitoExtension.class)
class InventoryTransferServiceTest {

    @Mock
    private InventoryTransferRepository transferRepo;

    @Mock
    private BranchRepository branchRepo;

    @Mock
    private ProductVariantRepository variantRepo;

    @Mock
    private InventoryRepository inventoryRepo;

    @Mock
    private InventoryTransactionRepository transactionRepo;

    @Mock
    private SubOrderRepository subOrderRepo;

    @Mock
    private BackorderService backorderService;

    @Mock
    private com.zone.agri.repository.InventoryTransferDetailRepository transferDetailRepo;

    @Mock
    private WarehouseContext warehouseContext;

    @Mock
    private InventoryCheckGuardService inventoryCheckGuardService;

    @InjectMocks
    private InventoryTransferService inventoryTransferService;

    private Branch warehouse;
    private Branch destinationBranch;
    private ProductVariant variant;
    private SubOrder replenishmentSubOrder;

    @BeforeEach
    void setUp() {
        warehouse = Branch.builder()
                .name("ArgiShrimp Kho Tổng")
                .build();
        setId(warehouse, 1L, "id");

        destinationBranch = Branch.builder()
                .name("ArgiShrimp Chi Nhanh Can Tho")
                .build();
        setId(destinationBranch, 2L, "id");

        variant = ProductVariant.builder()
                .sku("SP260404-479-V1")
                .build();
        setId(variant, 10L, "id");

        Order order = Order.builder()
                .code("ORDTEST001")
                .status(OrderStatus.AWAITING_REPLENISHMENT)
                .build();
        setId(order, 100L, "id");

        replenishmentSubOrder = SubOrder.builder()
                .status(OrderStatus.AWAITING_REPLENISHMENT)
                .branch(destinationBranch)
                .order(order)
                .items(List.of(SubOrderItem.builder()
                        .productVariant(variant)
                        .quantity(5)
                        .missingQuantity(2)
                        .unitPrice(new BigDecimal("210000"))
                        .build()))
                .build();
        setId(replenishmentSubOrder, 34L, "id");
    }

    @Test
    void createReplenishmentTransfersForSubOrder_createsPendingTransferWithoutBlockingOnStockCheck() {
        when(subOrderRepo.findByIdWithItems(34L)).thenReturn(Optional.of(replenishmentSubOrder));
        when(transferRepo.findByReferenceCodeAndStatusInOrderByCreatedAtDesc(any(), any())).thenReturn(List.of());
        when(branchRepo.findAll()).thenReturn(List.of(warehouse, destinationBranch));
        when(branchRepo.findById(1L)).thenReturn(Optional.of(warehouse));
        when(branchRepo.findById(2L)).thenReturn(Optional.of(destinationBranch));
        when(variantRepo.findBySku("SP260404-479-V1")).thenReturn(Optional.of(variant));
        when(inventoryRepo.findByProductVariantId(10L)).thenReturn(List.of());
        when(transferRepo.countTotalTransfers()).thenReturn(3L);
        when(transferRepo.save(any(InventoryTransfer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        List<InventoryTransfer> transfers = inventoryTransferService
                .createReplenishmentTransfersForSubOrder(replenishmentSubOrder);

        assertThat(transfers).hasSize(1);
        InventoryTransfer transfer = transfers.get(0);
        assertThat(transfer.getTransferCode()).isEqualTo("PDC-000004");
        assertThat(transfer.getTransferType()).isEqualTo("ORDER_REPLENISHMENT");
        assertThat(transfer.getStatus()).isEqualTo(InventoryTransferStatus.PENDING);
        assertThat(transfer.getReferenceCode()).isEqualTo("ORDTEST001-SUB-34");
        assertThat(transfer.getDescription()).contains("ORDTEST001");
        assertThat(transfer.getFromBranch().getId()).isEqualTo(1L);
        assertThat(transfer.getToBranch().getId()).isEqualTo(2L);
        assertThat(transfer.getTotalQuantity()).isEqualTo(2);
        assertThat(transfer.getDetails()).hasSize(1);
        assertThat(transfer.getDetails().get(0).getQuantityRequested()).isEqualTo(2);
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
