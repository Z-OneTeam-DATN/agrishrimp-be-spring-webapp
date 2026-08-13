package com.zone.agri.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zone.agri.entity.Branch;
import com.zone.agri.entity.Order;
import com.zone.agri.entity.Product;
import com.zone.agri.entity.ProductVariant;
import com.zone.agri.entity.SubOrder;
import com.zone.agri.entity.SubOrderItem;
import com.zone.agri.entity.enums.OrderStatus;
import com.zone.agri.repository.SubOrderItemRepository;
import com.zone.agri.repository.SubOrderRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BackorderServiceTest {

    @Mock
    private SubOrderItemRepository subOrderItemRepository;

    @Mock
    private SubOrderRepository subOrderRepository;

    @Mock
    private InventoryCheckGuardService inventoryCheckGuardService;

    @Mock
    private OrderInventoryReservationService orderInventoryReservationService;

    @Mock
    private OrderStatusSyncService orderStatusSyncService;

    @InjectMocks
    private BackorderService backorderService;

    @Test
    void fulfillBackordersOnStockReceive_fullFulfillment_resumesSubOrderToProcessing() {
        Order order = Order.builder()
                .id(200L)
                .code("ORD-200")
                .status(OrderStatus.AWAITING_REPLENISHMENT)
                .build();
        Branch branch = Branch.builder().id(3L).name("Chi nhanh 3").build();
        ProductVariant variant = ProductVariant.builder()
                .id(10L)
                .sku("SKU-10")
                .product(Product.builder().id(99L).name("San pham test").build())
                .build();
        SubOrder subOrder = SubOrder.builder()
                .id(100L)
                .order(order)
                .branch(branch)
                .status(OrderStatus.AWAITING_REPLENISHMENT)
                .build();
        SubOrderItem item = SubOrderItem.builder()
                .id(1L)
                .subOrder(subOrder)
                .productVariant(variant)
                .quantity(5)
                .allocatedQuantity(3)
                .missingQuantity(2)
                .unitPrice(BigDecimal.TEN)
                .build();

        when(subOrderItemRepository.findBackorderItemsForFulfillment(branch.getId(), variant.getId()))
                .thenReturn(List.of(item));
        when(subOrderItemRepository.save(any(SubOrderItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(subOrderItemRepository.findBySubOrderId(subOrder.getId())).thenReturn(List.of(item));
        when(subOrderRepository.findById(subOrder.getId())).thenReturn(Optional.of(subOrder));
        when(subOrderRepository.save(any(SubOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        backorderService.fulfillBackordersOnStockReceive(branch.getId(), variant.getId(), 2);

        assertThat(item.getAllocatedQuantity()).isEqualTo(5);
        assertThat(item.getMissingQuantity()).isZero();
        assertThat(subOrder.getStatus()).isEqualTo(OrderStatus.PROCESSING);
        verify(orderInventoryReservationService).reserveInventory(
                eq(branch.getId()),
                eq(variant.getId()),
                eq(2),
                eq("ORD-200-SUB-100"),
                eq("Giu hang bo sung cho phan don ORD-200"));
        verify(subOrderRepository).save(subOrder);
        verify(orderStatusSyncService).syncMasterOrderStatus(order.getId());
    }

    @Test
    void fulfillBackordersOnStockReceive_partialFulfillment_keepsSubOrderAwaitingReplenishment() {
        Order order = Order.builder()
                .id(201L)
                .code("ORD-201")
                .status(OrderStatus.AWAITING_REPLENISHMENT)
                .build();
        Branch branch = Branch.builder().id(4L).name("Chi nhanh 4").build();
        ProductVariant variant = ProductVariant.builder()
                .id(11L)
                .sku("SKU-11")
                .product(Product.builder().id(98L).name("San pham test 2").build())
                .build();
        SubOrder subOrder = SubOrder.builder()
                .id(101L)
                .order(order)
                .branch(branch)
                .status(OrderStatus.AWAITING_REPLENISHMENT)
                .build();
        SubOrderItem item = SubOrderItem.builder()
                .id(2L)
                .subOrder(subOrder)
                .productVariant(variant)
                .quantity(5)
                .allocatedQuantity(0)
                .missingQuantity(5)
                .unitPrice(BigDecimal.ONE)
                .build();

        when(subOrderItemRepository.findBackorderItemsForFulfillment(branch.getId(), variant.getId()))
                .thenReturn(List.of(item));
        when(subOrderItemRepository.save(any(SubOrderItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(subOrderItemRepository.findBySubOrderId(subOrder.getId())).thenReturn(List.of(item));

        backorderService.fulfillBackordersOnStockReceive(branch.getId(), variant.getId(), 2);

        assertThat(item.getAllocatedQuantity()).isEqualTo(2);
        assertThat(item.getMissingQuantity()).isEqualTo(3);
        assertThat(subOrder.getStatus()).isEqualTo(OrderStatus.AWAITING_REPLENISHMENT);
        verify(subOrderRepository, never()).findById(subOrder.getId());
        verify(subOrderRepository, never()).save(any(SubOrder.class));
        verify(orderStatusSyncService).syncMasterOrderStatus(order.getId());
    }
}
