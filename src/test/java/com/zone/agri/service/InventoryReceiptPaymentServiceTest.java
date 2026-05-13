package com.zone.agri.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.zone.agri.dto.request.inventory.ReceiptPaymentRequest;
import com.zone.agri.dto.response.inventory.ReceiptPaymentResponse;
import com.zone.agri.entity.Branch;
import com.zone.agri.entity.InventoryNote;
import com.zone.agri.entity.InventoryReceiptPayment;
import com.zone.agri.entity.Supplier;
import com.zone.agri.entity.User;
import com.zone.agri.entity.enums.InventoryNoteStatus;
import com.zone.agri.entity.enums.InventoryNoteType;
import com.zone.agri.entity.enums.SupplierPaymentMethod;
import com.zone.agri.repository.InventoryNoteRepository;
import com.zone.agri.repository.InventoryReceiptPaymentRepository;
import com.zone.agri.repository.UserRepository;
import com.zone.agri.security.CustomUserDetail;

@ExtendWith(MockitoExtension.class)
class InventoryReceiptPaymentServiceTest {

    @Mock
    private InventoryNoteRepository inventoryNoteRepository;

    @Mock
    private InventoryReceiptPaymentRepository inventoryReceiptPaymentRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private com.zone.agri.common.WarehouseContext warehouseContext;

    @InjectMocks
    private InventoryReceiptPaymentService inventoryReceiptPaymentService;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createReceiptPayment_shouldRebuildRemainingDebtByPaymentDateOrder() {
        Supplier supplier = Supplier.builder().id(5L).name("NCC A").build();
        Branch branch = Branch.builder().id(3L).name("Kho Tong").build();
        InventoryNote note = InventoryNote.builder()
                .id(100L)
                .code("PN-00100")
                .type(InventoryNoteType.IMPORT)
                .status(InventoryNoteStatus.COMPLETED)
                .supplier(supplier)
                .branch(branch)
                .totalAmount(new BigDecimal("1000"))
                .paymentAmount(new BigDecimal("200"))
                .debtAmount(new BigDecimal("800"))
                .build();

        User creator = User.builder().id(9L).email("tester@example.com").fullName("Tester").build();
        ReceiptPaymentRequest request = ReceiptPaymentRequest.builder()
                .amount(new BigDecimal("100"))
                .paymentDate("2026-05-01")
                .paymentMethod(SupplierPaymentMethod.CASH)
                .referenceCode("TM-001")
                .note("Chen thanh toan backdate")
                .build();

        InventoryReceiptPayment savedPayment = InventoryReceiptPayment.builder()
                .id(1001L)
                .inventoryNote(note)
                .supplier(supplier)
                .branch(branch)
                .createdBy(creator)
                .paymentDate(LocalDateTime.of(2026, 5, 1, 0, 0))
                .amount(new BigDecimal("100"))
                .remainingDebtAfter(new BigDecimal("700"))
                .paymentMethod(SupplierPaymentMethod.CASH)
                .referenceCode("TM-001")
                .note("Chen thanh toan backdate")
                .createdAt(LocalDateTime.of(2026, 5, 8, 8, 0))
                .build();

        InventoryReceiptPayment existingPayment = InventoryReceiptPayment.builder()
                .id(1002L)
                .inventoryNote(note)
                .supplier(supplier)
                .branch(branch)
                .createdBy(creator)
                .paymentDate(LocalDateTime.of(2026, 5, 10, 0, 0))
                .amount(new BigDecimal("200"))
                .remainingDebtAfter(new BigDecimal("800"))
                .paymentMethod(SupplierPaymentMethod.TRANSFER)
                .referenceCode("UNC-002")
                .note("Thanh toan dot sau")
                .createdAt(LocalDateTime.of(2026, 5, 10, 8, 0))
                .build();

        setAuthenticatedUser();
        when(userRepository.findByEmail("tester@example.com")).thenReturn(Optional.of(creator));
        when(inventoryNoteRepository.findById(100L)).thenReturn(Optional.of(note));
        doNothing().when(warehouseContext).assertAccess(3L);
        when(inventoryReceiptPaymentRepository.sumAmountByReceiptId(100L)).thenReturn(new BigDecimal("200"));
        when(inventoryReceiptPaymentRepository.save(any(InventoryReceiptPayment.class))).thenReturn(savedPayment);
        when(inventoryReceiptPaymentRepository.findByReceiptIdOrderByPaymentDateAscIdAsc(100L))
                .thenReturn(List.of(savedPayment, existingPayment));
        when(inventoryReceiptPaymentRepository.findById(1001L)).thenReturn(Optional.of(savedPayment));

        ReceiptPaymentResponse response = inventoryReceiptPaymentService.createReceiptPayment(100L, request);

        assertThat(savedPayment.getRemainingDebtAfter()).isEqualByComparingTo("900");
        assertThat(existingPayment.getRemainingDebtAfter()).isEqualByComparingTo("700");
        assertThat(note.getPaymentAmount()).isEqualByComparingTo("300");
        assertThat(note.getDebtAmount()).isEqualByComparingTo("700");
        assertThat(response.getRemainingDebtAfter()).isEqualByComparingTo("900");
    }

    private void setAuthenticatedUser() {
        com.zone.agri.dto.response.user.UserDetail userDetail = com.zone.agri.dto.response.user.UserDetail.builder()
                .id(9L)
                .email("tester@example.com")
                .build();
        CustomUserDetail principal = new CustomUserDetail(
                "tester@example.com",
                "password",
                true,
                true,
                userDetail,
                List.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
}
