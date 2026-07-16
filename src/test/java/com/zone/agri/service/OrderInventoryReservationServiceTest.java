package com.zone.agri.service;

import com.zone.agri.entity.Inventory;
import com.zone.agri.entity.InventoryTransaction;
import com.zone.agri.exception.ConflictException;
import com.zone.agri.repository.InventoryRepository;
import com.zone.agri.repository.InventoryTransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

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
}
