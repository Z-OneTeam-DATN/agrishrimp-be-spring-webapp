package com.zone.agri.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.zone.agri.entity.enums.TransactionType;
import com.zone.agri.repository.InventoryRepository;
import com.zone.agri.repository.InventoryTransactionRepository;

@ExtendWith(MockitoExtension.class)
class InventoryReportServiceTest {

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private InventoryTransactionRepository inventoryTransactionRepository;

    @InjectMocks
    private InventoryReportService inventoryReportService;

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void getLedger_shouldUseOnlyPhysicalStockTransactionTypes() {
        when(inventoryTransactionRepository.findLedger(any(), isNull(), isNull(), isNull()))
                .thenReturn(List.of());

        inventoryReportService.getLedger(null, null, null, "all");

        ArgumentCaptor<Collection<TransactionType>> typesCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(inventoryTransactionRepository).findLedger(typesCaptor.capture(), isNull(), isNull(), isNull());

        assertThat(typesCaptor.getValue())
                .contains(TransactionType.IMPORT,
                        TransactionType.SALE,
                        TransactionType.TRANSFER_IN,
                        TransactionType.TRANSFER_OUT,
                        TransactionType.TRANSFER_LOSS,
                        TransactionType.ADJUSTMENT,
                        TransactionType.RETURN,
                        TransactionType.CANCEL_RELEASE,
                        TransactionType.DAMAGED)
                .doesNotContain(TransactionType.ORDER_RESERVE, TransactionType.ORDER_RELEASE);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void getIOSummary_shouldExcludeTransferLossFromStockMovementTotals() {
        when(inventoryTransactionRepository.findMovementSummary(any(), isNull(), any(), any()))
                .thenReturn(List.of());
        when(inventoryTransactionRepository.findMovementAfterDate(any(), isNull(), any()))
                .thenReturn(List.of());
        when(inventoryRepository.findCurrentStockByBranch(isNull())).thenReturn(List.of());

        inventoryReportService.getIOSummary(null, LocalDate.now().minusDays(1), LocalDate.now());

        ArgumentCaptor<Collection<TransactionType>> typesCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(inventoryTransactionRepository).findMovementSummary(typesCaptor.capture(), isNull(), any(), any());

        assertThat(typesCaptor.getValue())
                .contains(TransactionType.TRANSFER_IN, TransactionType.TRANSFER_OUT)
                .doesNotContain(TransactionType.TRANSFER_LOSS);
    }
}
