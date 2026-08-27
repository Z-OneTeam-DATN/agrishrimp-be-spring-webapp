package com.zone.agri.service;

import com.zone.agri.entity.Inventory;
import com.zone.agri.entity.InventoryTransaction;
import com.zone.agri.entity.enums.TransactionType;
import com.zone.agri.exception.ConflictException;
import com.zone.agri.repository.InventoryRepository;
import com.zone.agri.repository.InventoryTransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderInventoryReservationServiceTest {

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private InventoryTransactionRepository transactionRepository;

    @InjectMocks
    private OrderInventoryReservationService reservationService;

    @Test
    void reserveInventoryUpTo_shouldReserveOnlyAvailableQuantityWithoutThrowing() {
        Inventory batch = Inventory.builder()
                .id(1L)
                .quantity(10)
                .reservedQuantity(8)
                .build();

        when(inventoryRepository.findForUpdateFIFO(1L, 101L)).thenReturn(List.of(batch));
        when(inventoryRepository.save(any(Inventory.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(transactionRepository.save(any(InventoryTransaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        int reserved = reservationService.reserveInventoryUpTo(1L, 101L, 5, "ORD-1", "reserve partial");

        assertThat(reserved).isEqualTo(2);
        assertThat(batch.getReservedQuantity()).isEqualTo(10);
        verify(transactionRepository).save(any(InventoryTransaction.class));
    }

    @Test
    void reserveInventory_shouldStillThrowWhenCallerRequiresFullReservation() {
        Inventory batch = Inventory.builder()
                .id(1L)
                .quantity(10)
                .reservedQuantity(8)
                .build();

        when(inventoryRepository.findForUpdateFIFO(1L, 101L)).thenReturn(List.of(batch));
        when(inventoryRepository.save(any(Inventory.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(transactionRepository.save(any(InventoryTransaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        assertThatThrownBy(() -> reservationService.reserveInventory(1L, 101L, 5, "ORD-2", "reserve full"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Ton kho da thay doi");
    }

    @Test
    void shipReservedInventory_shouldRecordPhysicalBalanceIncludingDefectiveStock() {
        Inventory batch = Inventory.builder()
                .id(1L)
                .quantity(10)
                .defectiveQuantity(2)
                .reservedQuantity(3)
                .build();
        InventoryTransaction reservation = InventoryTransaction.builder()
                .id(10L)
                .type(TransactionType.ORDER_RESERVE)
                .quantityChange(3)
                .inventory(batch)
                .build();

        when(transactionRepository.findByReferenceCodeAndType("ORD-3", TransactionType.ORDER_RESERVE))
                .thenReturn(List.of(reservation));
        when(inventoryRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(batch));
        when(inventoryRepository.save(any(Inventory.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(transactionRepository.save(any(InventoryTransaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        reservationService.shipReservedInventory("ORD-3", "ship reserved");

        assertThat(batch.getQuantity()).isEqualTo(7);
        assertThat(batch.getReservedQuantity()).isZero();

        ArgumentCaptor<InventoryTransaction> transactionCaptor = ArgumentCaptor.forClass(InventoryTransaction.class);
        verify(transactionRepository).save(transactionCaptor.capture());
        InventoryTransaction saleTransaction = transactionCaptor.getValue();
        assertThat(saleTransaction.getType()).isEqualTo(TransactionType.SALE);
        assertThat(saleTransaction.getQuantityChange()).isEqualTo(-3);
        assertThat(saleTransaction.getNewBalance()).isEqualTo(9);
    }
}
