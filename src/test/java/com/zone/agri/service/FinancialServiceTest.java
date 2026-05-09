package com.zone.agri.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.zone.agri.dto.response.financial.CashbookReportResponse;
import com.zone.agri.dto.response.financial.ProfitLossResponse;
import com.zone.agri.dto.response.supplier.SupplierDebtResponse;
import com.zone.agri.dto.response.user.RoleDto;
import com.zone.agri.dto.response.user.UserDetail;
import com.zone.agri.entity.Branch;
import com.zone.agri.entity.InventoryNote;
import com.zone.agri.entity.InventoryReceiptPayment;
import com.zone.agri.entity.Supplier;
import com.zone.agri.entity.User;
import com.zone.agri.entity.enums.InventoryNoteType;
import com.zone.agri.entity.enums.SupplierPaymentMethod;
import com.zone.agri.repository.InventoryReceiptPaymentRepository;
import com.zone.agri.repository.InventoryTransactionRepository;
import com.zone.agri.repository.OrderRepository;
import com.zone.agri.repository.SubOrderRepository;
import com.zone.agri.repository.SupplierRepository;
import com.zone.agri.security.CustomUserDetail;

@ExtendWith(MockitoExtension.class)
class FinancialServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private SubOrderRepository subOrderRepository;

    @Mock
    private SupplierRepository supplierRepository;

    @Mock
    private InventoryReceiptPaymentRepository inventoryReceiptPaymentRepository;

    @Mock
    private InventoryTransactionRepository inventoryTransactionRepository;

    @InjectMocks
    private FinancialService financialService;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getProfitLossReport_shouldUseLifecycleTimestampsAndSplitOrderCostsWithoutDoubleCounting() {
        setAuthenticatedUser("ADMIN", 99L);

        when(orderRepository.findLegacyFinancialOrders(
                eq(LocalDateTime.of(2026, 5, 31, 23, 59, 59)),
                eq(12L)))
                .thenReturn(List.of(
                        legacyOrderProjection(
                                1L,
                                "ORD-LEGACY-1",
                                "1000",
                                "50",
                                "40",
                                LocalDateTime.of(2026, 5, 10, 10, 0),
                                null,
                                LocalDateTime.of(2026, 5, 20, 10, 0)),
                        legacyOrderProjection(
                                2L,
                                "ORD-LEGACY-2",
                                "300",
                                "20",
                                "10",
                                LocalDateTime.of(2026, 4, 25, 10, 0),
                                LocalDateTime.of(2026, 4, 27, 10, 0),
                                null)));

        when(subOrderRepository.findFinancialSubOrders(
                eq(LocalDateTime.of(2026, 5, 31, 23, 59, 59)),
                eq(12L)))
                .thenReturn(List.of(
                        subOrderProjection(
                                21L,
                                2001L,
                                "ORD-SPLIT-1",
                                "600",
                                "30",
                                "1000",
                                "100",
                                LocalDateTime.of(2026, 5, 12, 9, 0),
                                null,
                                null),
                        subOrderProjection(
                                22L,
                                2001L,
                                "ORD-SPLIT-1",
                                "400",
                                "20",
                                "1000",
                                "100",
                                LocalDateTime.of(2026, 5, 12, 9, 0),
                                LocalDateTime.of(2026, 5, 14, 8, 0),
                                null),
                        subOrderProjection(
                                31L,
                                2002L,
                                "ORD-SPLIT-2",
                                "500",
                                "25",
                                "500",
                                "50",
                                LocalDateTime.of(2026, 4, 28, 9, 0),
                                LocalDateTime.of(2026, 4, 29, 8, 0),
                                LocalDateTime.of(2026, 5, 22, 11, 0))));

        when(inventoryTransactionRepository.sumSaleCostByReferenceCodes(
                eq(java.util.Set.of("ORD-LEGACY-1", "ORD-SPLIT-1-SUB-21", "ORD-SPLIT-1-SUB-22")),
                eq(12L)))
                .thenReturn(List.of(
                        costProjection("ORD-LEGACY-1", "600"),
                        costProjection("ORD-SPLIT-1-SUB-21", "200"),
                        costProjection("ORD-SPLIT-1-SUB-22", "150")));

        ProfitLossResponse response = financialService.getProfitLossReport(
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 31),
                12L);

        assertThat(response.getGrossRevenue()).isEqualByComparingTo("2000");
        assertThat(response.getReturnedGoods()).isEqualByComparingTo("1500");
        assertThat(response.getShippingFeeCollected()).isEqualByComparingTo("100");
        assertThat(response.getShippingFeeReturned()).isEqualByComparingTo("75");
        assertThat(response.getDiscount()).isEqualByComparingTo("140");
        assertThat(response.getDiscountReturned()).isEqualByComparingTo("90");
        assertThat(response.getNetProductRevenue()).isEqualByComparingTo("500");
        assertThat(response.getNetRevenue()).isEqualByComparingTo("475");
        assertThat(response.getCogs()).isEqualByComparingTo("950");
        assertThat(response.getGrossProfit()).isEqualByComparingTo("-475");
        assertThat(response.getNetProfit()).isEqualByComparingTo("-475");

        verify(orderRepository).findLegacyFinancialOrders(
                LocalDateTime.of(2026, 5, 31, 23, 59, 59),
                12L);
        verify(subOrderRepository).findFinancialSubOrders(
                LocalDateTime.of(2026, 5, 31, 23, 59, 59),
                12L);
    }

    @Test
    void getSupplierDebts_shouldReturnOutstandingAtEndDateAfterPaymentsAndReturns() {
        setAuthenticatedUser("ADMIN", 77L);

        when(supplierRepository.findSupplierDebtLedger(
                eq("tom"),
                eq(LocalDateTime.of(2026, 5, 31, 23, 59, 59)),
                eq(7L),
                eq(9L)))
                .thenReturn(List.of(
                        ledgerProjection(1L, "NCC001", "Tom A", "0901", 101L, InventoryNoteType.IMPORT, "1000", "400"),
                        ledgerProjection(1L, "NCC001", "Tom A", "0901", 102L, InventoryNoteType.IMPORT, "500", "700"),
                        ledgerProjection(1L, "NCC001", "Tom A", "0901", 103L, InventoryNoteType.EXPORT, "200", "0"),
                        ledgerProjection(2L, "NCC002", "Tom B", "0902", 201L, InventoryNoteType.IMPORT, "300", "300")));

        List<SupplierDebtResponse> response = financialService.getSupplierDebts(
                "tom",
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 31),
                7L,
                9L,
                "not_zero");

        assertThat(response).hasSize(1);
        assertThat(response.getFirst().getSupplierCode()).isEqualTo("NCC001");
        assertThat(response.getFirst().getTotalDebt()).isEqualByComparingTo("400");

        verify(supplierRepository).findSupplierDebtLedger(
                "tom",
                LocalDateTime.of(2026, 5, 31, 23, 59, 59),
                7L,
                9L);
    }

    @Test
    void getCashbookReport_shouldUseRecordedPaymentsOnlyAndComputeOpeningClosingByPeriod() {
        setAuthenticatedUser("STAFF", 5L);

        InventoryReceiptPayment payment = InventoryReceiptPayment.builder()
                .id(11L)
                .inventoryNote(InventoryNote.builder().id(88L).code("PN-00088").build())
                .supplier(Supplier.builder().id(3L).name("Tom Supplier").build())
                .branch(Branch.builder().id(5L).name("Chi nhanh A").build())
                .createdBy(User.builder().id(10L).fullName("Nguyen Van A").build())
                .paymentDate(LocalDateTime.of(2026, 5, 10, 9, 30))
                .createdAt(LocalDateTime.of(2026, 5, 10, 9, 0))
                .amount(new BigDecimal("150"))
                .remainingDebtAfter(new BigDecimal("350"))
                .paymentMethod(SupplierPaymentMethod.TRANSFER)
                .referenceCode("UNC-001")
                .note("Thanh toan dot 1")
                .build();

        when(inventoryReceiptPaymentRepository.findAllWithFilters(
                eq(LocalDateTime.of(2026, 5, 1, 0, 0)),
                eq(LocalDateTime.of(2026, 5, 31, 23, 59, 59)),
                eq(5L)))
                .thenReturn(List.of(payment));
        when(inventoryReceiptPaymentRepository.sumAmountBeforeDate(
                eq(LocalDateTime.of(2026, 5, 1, 0, 0)),
                eq(5L)))
                .thenReturn(new BigDecimal("400"));
        when(inventoryReceiptPaymentRepository.sumAmountInRange(
                eq(LocalDateTime.of(2026, 5, 1, 0, 0)),
                eq(LocalDateTime.of(2026, 5, 31, 23, 59, 59)),
                eq(5L)))
                .thenReturn(new BigDecimal("150"));

        CashbookReportResponse response = financialService.getCashbookReport(
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 31),
                999L);

        assertThat(response.getSummary().getOpeningBalance()).isEqualByComparingTo("-400");
        assertThat(response.getSummary().getTotalIncome()).isEqualByComparingTo("0");
        assertThat(response.getSummary().getTotalExpense()).isEqualByComparingTo("150");
        assertThat(response.getSummary().getClosingBalance()).isEqualByComparingTo("-550");
        assertThat(response.getEntries()).hasSize(1);
        assertThat(response.getEntries().getFirst().getDirection()).isEqualTo("OUT");
        assertThat(response.getEntries().getFirst().getSource()).isEqualTo("SUPPLIER_PAYMENT");
        assertThat(response.getEntries().getFirst().getCode()).isEqualTo("PN-00088");
        assertThat(response.getEntries().getFirst().getPaymentMethod()).isEqualTo("TRANSFER");
        assertThat(response.getEntries().getFirst().getAmount()).isEqualByComparingTo("150");
    }

    @Test
    void getProfitLossReport_shouldUseAuthenticatedStaffBranchForAllQueries() {
        setAuthenticatedUser("STAFF", 5L);

        when(orderRepository.findLegacyFinancialOrders(
                eq(LocalDateTime.of(2026, 5, 31, 23, 59, 59)),
                eq(5L)))
                .thenReturn(List.of());
        when(subOrderRepository.findFinancialSubOrders(
                eq(LocalDateTime.of(2026, 5, 31, 23, 59, 59)),
                eq(5L)))
                .thenReturn(List.of());

        ProfitLossResponse response = financialService.getProfitLossReport(
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 31),
                999L);

        assertThat(response.getNetRevenue()).isEqualByComparingTo("0");
        verify(orderRepository).findLegacyFinancialOrders(
                LocalDateTime.of(2026, 5, 31, 23, 59, 59),
                5L);
        verify(subOrderRepository).findFinancialSubOrders(
                LocalDateTime.of(2026, 5, 31, 23, 59, 59),
                5L);
    }

    private void setAuthenticatedUser(String roleSlug, Long branchId) {
        UserDetail userDetail = UserDetail.builder()
                .id(1L)
                .branchId(branchId)
                .role(RoleDto.builder().slug(roleSlug).build())
                .build();

        CustomUserDetail principal = new CustomUserDetail(
                "tester",
                "password",
                true,
                true,
                userDetail,
                List.of());

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private OrderRepository.LegacyFinancialOrderProjection legacyOrderProjection(
            Long id,
            String orderCode,
            String productAmount,
            String shippingAmount,
            String discountAmount,
            LocalDateTime receivedAt,
            LocalDateTime completedAt,
            LocalDateTime returnedAt) {
        return new OrderRepository.LegacyFinancialOrderProjection() {
            @Override
            public Long getId() {
                return id;
            }

            @Override
            public String getOrderCode() {
                return orderCode;
            }

            @Override
            public BigDecimal getProductAmount() {
                return new BigDecimal(productAmount);
            }

            @Override
            public BigDecimal getShippingAmount() {
                return new BigDecimal(shippingAmount);
            }

            @Override
            public BigDecimal getDiscountAmount() {
                return new BigDecimal(discountAmount);
            }

            @Override
            public LocalDateTime getReceivedAt() {
                return receivedAt;
            }

            @Override
            public LocalDateTime getCompletedAt() {
                return completedAt;
            }

            @Override
            public LocalDateTime getReturnedAt() {
                return returnedAt;
            }
        };
    }

    private SubOrderRepository.FinancialSubOrderProjection subOrderProjection(
            Long subOrderId,
            Long orderId,
            String orderCode,
            String subtotal,
            String shippingFee,
            String orderSubtotal,
            String orderDiscountAmount,
            LocalDateTime receivedAt,
            LocalDateTime completedAt,
            LocalDateTime returnedAt) {
        return new SubOrderRepository.FinancialSubOrderProjection() {
            @Override
            public Long getSubOrderId() {
                return subOrderId;
            }

            @Override
            public Long getOrderId() {
                return orderId;
            }

            @Override
            public String getOrderCode() {
                return orderCode;
            }

            @Override
            public BigDecimal getSubtotal() {
                return new BigDecimal(subtotal);
            }

            @Override
            public BigDecimal getShippingFee() {
                return new BigDecimal(shippingFee);
            }

            @Override
            public BigDecimal getOrderSubtotal() {
                return new BigDecimal(orderSubtotal);
            }

            @Override
            public BigDecimal getOrderDiscountAmount() {
                return new BigDecimal(orderDiscountAmount);
            }

            @Override
            public LocalDateTime getReceivedAt() {
                return receivedAt;
            }

            @Override
            public LocalDateTime getCompletedAt() {
                return completedAt;
            }

            @Override
            public LocalDateTime getReturnedAt() {
                return returnedAt;
            }
        };
    }

    private InventoryTransactionRepository.ReferenceCostProjection costProjection(String referenceCode, String cost) {
        return new InventoryTransactionRepository.ReferenceCostProjection() {
            @Override
            public String getReferenceCode() {
                return referenceCode;
            }

            @Override
            public BigDecimal getCost() {
                return new BigDecimal(cost);
            }
        };
    }

    private SupplierRepository.SupplierDebtLedgerProjection ledgerProjection(
            Long supplierId,
            String supplierCode,
            String supplierName,
            String phone,
            Long noteId,
            InventoryNoteType noteType,
            String totalAmount,
            String paidAmount) {
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
                return new BigDecimal(totalAmount);
            }

            @Override
            public BigDecimal getPaidAmount() {
                return new BigDecimal(paidAmount);
            }
        };
    }
}
