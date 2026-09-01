package com.zone.agri.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.zone.agri.dto.response.supplier.SupplierDebtInsightResponse;
import com.zone.agri.entity.enums.InventoryNoteType;
import com.zone.agri.repository.SupplierRepository;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class SupplierDebtInsightServiceTest {

        @Mock
        private SupplierRepository supplierRepository;

        @Mock
        private SettingService settingService;

        @InjectMocks
        private SupplierDebtInsightService supplierDebtInsightService;

        @BeforeEach
        void setUp() {
                when(settingService.getDebtAgeWarningDays()).thenReturn(45);
                when(settingService.getDebtAgeCriticalDays()).thenReturn(90);
                when(settingService.getDebtWeightAge()).thenReturn(BigDecimal.valueOf(0.5));
                when(settingService.getDebtWeightValue()).thenReturn(BigDecimal.valueOf(0.5));
        }

        @Test
        void getSupplierDebtInsights_shouldReturnEmptyWhenNoDebts() {
                when(supplierRepository.findSupplierDebtLedger(any(), any(), any(), any()))
                                .thenReturn(new ArrayList<>());

                SupplierDebtInsightResponse response = supplierDebtInsightService.getSupplierDebtInsights(
                                LocalDate.now().minusDays(30), LocalDate.now(), null, false);

                assertThat(response.isInsufficientData()).isFalse();
                assertThat(response.getTotalOutstandingDebt()).isZero();
                assertThat(response.getSupplierRanking()).isEmpty();
        }

        @Test
        void getSupplierDebtInsights_shouldCalculateWeightedAvgDebtAgeAndPriorityRank() {
                LocalDateTime now = LocalDateTime.now();

                SupplierRepository.SupplierDebtLedgerProjection proj1 = ledgerProjection(
                                1L, "NCC001", "NCC A", "0900000001", 101L, InventoryNoteType.IMPORT,
                                new BigDecimal("15000000.00"), new BigDecimal("5000000.00"), now.minusDays(10),
                                999L, "Staff X"); // nợ còn 10M, tuổi 10

                SupplierRepository.SupplierDebtLedgerProjection proj2 = ledgerProjection(
                                1L, "NCC001", "NCC A", "0900000001", 102L, InventoryNoteType.IMPORT,
                                new BigDecimal("8000000.00"), new BigDecimal("3000000.00"), now.minusDays(20),
                                999L, "Staff X"); // nợ còn 5M, tuổi 20

                SupplierRepository.SupplierDebtLedgerProjection proj3 = ledgerProjection(
                                2L, "NCC002", "NCC B", "0900000002", 103L, InventoryNoteType.IMPORT,
                                new BigDecimal("10000000.00"), BigDecimal.ZERO, now.minusDays(100),
                                888L, "Staff Y"); // nợ 10M, tuổi 100

                when(supplierRepository.findSupplierDebtLedger(any(), any(), any(), any()))
                                .thenReturn(List.of(proj1, proj2, proj3));

                SupplierDebtInsightResponse response = supplierDebtInsightService.getSupplierDebtInsights(
                                LocalDate.now().minusDays(30), LocalDate.now(), null, false);

                assertThat(response.isInsufficientData()).isFalse();
                assertThat(response.getTotalOutstandingDebt()).isEqualByComparingTo("25000000.00");

                assertThat(response.getSupplierRanking()).hasSize(2);

                SupplierDebtInsightResponse.SupplierRankingItem item1 = response.getSupplierRanking().get(0);
                assertThat(item1.getSupplierId()).isEqualTo(2L); // NCC B (CRITICAL tuổi 100)
                assertThat(item1.getPriorityRank()).isEqualTo(1);
                assertThat(item1.getAgeStatus()).isEqualTo("CRITICAL");

                SupplierDebtInsightResponse.SupplierRankingItem item2 = response.getSupplierRanking().get(1);
                assertThat(item2.getSupplierId()).isEqualTo(1L); // NCC A (NORMAL tuổi 13.33)
                assertThat(item2.getPriorityRank()).isEqualTo(2);
                assertThat(item2.getAgeStatus()).isEqualTo("NORMAL");

                assertThat(response.getStaffDebtSummary()).hasSize(2);
                assertThat(response.getStaffDebtSummary().get(0).getStaffId()).isEqualTo(999L);
                assertThat(response.getStaffDebtSummary().get(0).getTotalDebtFromOrders())
                                .isEqualByComparingTo("15000000.00");
        }

        @Test
        void getSupplierDebtInsights_shouldReturnInsufficientDataOnDatabaseException() {
                when(supplierRepository.findSupplierDebtLedger(any(), any(), any(), any()))
                                .thenThrow(new RuntimeException("Database connection failure"));

                SupplierDebtInsightResponse response = supplierDebtInsightService.getSupplierDebtInsights(
                                LocalDate.now().minusDays(30), LocalDate.now(), null, false);

                assertThat(response.isInsufficientData()).isTrue();
                assertThat(response.getWarnings())
                                .contains("Không thể lấy dữ liệu công nợ do lỗi kết nối cơ sở dữ liệu.");
        }

        private SupplierRepository.SupplierDebtLedgerProjection ledgerProjection(
                        Long supplierId, String supplierCode, String supplierName, String phone,
                        Long noteId, InventoryNoteType noteType, BigDecimal totalAmount,
                        BigDecimal paidAmount, LocalDateTime createdAt, Long createdById, String createdByName) {
                return new SupplierRepository.SupplierDebtLedgerProjection() {
                        @Override
                        public Long getSupplierId() {
                                return supplierId;
                        }

                        @Override
                        public String getSupplierCode() {
                                return supplierCode;
                        }

                        @Override
                        public String getSupplierName() {
                                return supplierName;
                        }

                        @Override
                        public String getPhone() {
                                return phone;
                        }

                        @Override
                        public Long getNoteId() {
                                return noteId;
                        }

                        @Override
                        public InventoryNoteType getNoteType() {
                                return noteType;
                        }

                        @Override
                        public BigDecimal getTotalAmount() {
                                return totalAmount;
                        }

                        @Override
                        public BigDecimal getPaidAmount() {
                                return paidAmount;
                        }

                        @Override
                        public LocalDateTime getCreatedAt() {
                                return createdAt;
                        }

                        @Override
                        public Long getCreatedById() {
                                return createdById;
                        }

                        @Override
                        public String getCreatedByName() {
                                return createdByName;
                        }
                };
        }
}
