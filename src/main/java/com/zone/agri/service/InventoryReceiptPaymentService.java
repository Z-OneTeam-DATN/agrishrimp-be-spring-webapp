package com.zone.agri.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.zone.agri.common.AuthUtils;
import com.zone.agri.common.RoleUtils;
import com.zone.agri.dto.request.inventory.ReceiptPaymentRequest;
import com.zone.agri.dto.response.inventory.ReceiptPaymentResponse;
import com.zone.agri.entity.InventoryNote;
import com.zone.agri.entity.InventoryReceiptPayment;
import com.zone.agri.entity.User;
import com.zone.agri.entity.enums.InventoryNoteStatus;
import com.zone.agri.entity.enums.InventoryNoteType;
import com.zone.agri.exception.BadRequestException;
import com.zone.agri.exception.NotFoundException;
import com.zone.agri.repository.InventoryNoteRepository;
import com.zone.agri.repository.InventoryReceiptPaymentRepository;
import com.zone.agri.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class InventoryReceiptPaymentService {

    private final InventoryNoteRepository inventoryNoteRepository;
    private final InventoryReceiptPaymentRepository inventoryReceiptPaymentRepository;
    private final UserRepository userRepository;
    private final com.zone.agri.common.WarehouseContext warehouseContext;
    private final CashflowRiskService cashflowRiskService;

    private User getCurrentUser() {
        org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }
        return userRepository.findByEmail(auth.getName()).orElse(null);
    }

    private boolean canReadReceiptsAcrossBranches() {
        com.zone.agri.dto.response.user.UserDetail user = AuthUtils.getUserDetail();
        String roleSlug = user != null && user.getRole() != null ? user.getRole().getSlug() : null;
        return RoleUtils.isAdminLikeRole(roleSlug)
                || RoleUtils.hasAdminLikeAuthority(AuthUtils.getAuthorities());
    }

    private void assertReceiptReadAccess(InventoryNote note) {
        if (!canReadReceiptsAcrossBranches()) {
            warehouseContext.assertAccess(note.getBranch().getId());
        }
    }

    @Transactional(readOnly = true)
    public List<ReceiptPaymentResponse> getReceiptPayments(Long receiptId) {
        InventoryNote note = inventoryNoteRepository.findById(receiptId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy phiếu nhập ID: " + receiptId));
        assertReceiptReadAccess(note);

        return inventoryReceiptPaymentRepository.findByReceiptIdWithDetails(receiptId).stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional
    public ReceiptPaymentResponse createReceiptPayment(Long receiptId, ReceiptPaymentRequest request) {
        InventoryNote note = inventoryNoteRepository.findById(receiptId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy phiếu nhập ID: " + receiptId));
        warehouseContext.assertAccess(note.getBranch().getId());

        if (note.getType() != InventoryNoteType.IMPORT || note.getSupplier() == null) {
            throw new BadRequestException("Chỉ phiếu nhập từ nhà cung cấp mới được ghi nhận thanh toán.");
        }
        if (note.getStatus() != InventoryNoteStatus.COMPLETED) {
            throw new BadRequestException("Chỉ có thể thanh toán sau khi phiếu nhập đã hoàn tất kiểm hàng.");
        }

        BigDecimal totalAmount = Objects.requireNonNullElse(note.getTotalAmount(), BigDecimal.ZERO);
        BigDecimal totalPaidBefore = Objects.requireNonNullElse(
                inventoryReceiptPaymentRepository.sumAmountByReceiptId(receiptId),
                BigDecimal.ZERO);
        BigDecimal remainingDebt = totalAmount.subtract(totalPaidBefore);

        if (remainingDebt.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Phiếu nhập này đã được thanh toán đủ.");
        }

        BigDecimal amount = Objects.requireNonNullElse(request.getAmount(), BigDecimal.ZERO);
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Số tiền thanh toán phải lớn hơn 0.");
        }
        if (amount.compareTo(remainingDebt) > 0) {
            throw new BadRequestException("Số tiền thanh toán vượt quá công nợ còn lại của phiếu nhập.");
        }

        LocalDateTime paymentDate = (request.getPaymentDate() == null || request.getPaymentDate().isBlank())
                ? LocalDateTime.now()
                : LocalDate.parse(request.getPaymentDate()).atStartOfDay();
        BigDecimal totalPaidAfter = totalPaidBefore.add(amount);
        BigDecimal remainingDebtAfter = totalAmount.subtract(totalPaidAfter).max(BigDecimal.ZERO);

        InventoryReceiptPayment payment = InventoryReceiptPayment.builder()
                .inventoryNote(note)
                .supplier(note.getSupplier())
                .branch(note.getBranch())
                .createdBy(getCurrentUser())
                .paymentDate(paymentDate)
                .amount(amount)
                .remainingDebtAfter(remainingDebtAfter)
                .paymentMethod(request.getPaymentMethod())
                .referenceCode(request.getReferenceCode())
                .note(request.getNote())
                .createdAt(LocalDateTime.now())
                .build();

        InventoryReceiptPayment savedPayment = inventoryReceiptPaymentRepository.save(payment);
        rebuildPaymentLedger(note);
        try {
            cashflowRiskService.clearCache();
        } catch (Exception e) {
            // Ignore cache eviction failure
        }
        InventoryReceiptPayment refreshedPayment = inventoryReceiptPaymentRepository.findById(savedPayment.getId())
                .orElse(savedPayment);
        return mapToResponse(refreshedPayment);
    }

    @Transactional(readOnly = true)
    public List<ReceiptPaymentResponse> getAllPayments(LocalDate startDate, LocalDate endDate, Long branchId) {
        Long finalBranchId = resolveBranchId(branchId);
        LocalDateTime start = startDate != null ? startDate.atStartOfDay() : null;
        LocalDateTime end = endDate != null ? endDate.atTime(23, 59, 59) : null;

        return inventoryReceiptPaymentRepository.findAllWithFilters(start, end, finalBranchId).stream()
                .map(this::mapToResponse)
                .toList();
    }

    private Long resolveBranchId(Long requestBranchId) {
        return AuthUtils.resolveRequestedOrUserBranch(
                requestBranchId, "REPORT_FINANCE_VIEW", "REPORT_FINANCE_VIEW_ALL_BRANCHES");
    }

    private ReceiptPaymentResponse mapToResponse(InventoryReceiptPayment payment) {
        InventoryNote note = payment.getInventoryNote();
        return ReceiptPaymentResponse.builder()
                .id(payment.getId())
                .receiptId(note != null ? note.getId() : null)
                .receiptCode(note != null ? note.getCode() : "")
                .supplierId(payment.getSupplier() != null ? payment.getSupplier().getId() : null)
                .supplierName(payment.getSupplier() != null ? payment.getSupplier().getName() : "")
                .branchId(payment.getBranch() != null ? payment.getBranch().getId() : null)
                .branchName(payment.getBranch() != null ? payment.getBranch().getName() : "")
                .amount(Objects.requireNonNullElse(payment.getAmount(), BigDecimal.ZERO))
                .remainingDebtAfter(Objects.requireNonNullElse(payment.getRemainingDebtAfter(), BigDecimal.ZERO))
                .paymentMethod(payment.getPaymentMethod() != null ? payment.getPaymentMethod().name() : "")
                .referenceCode(payment.getReferenceCode())
                .note(payment.getNote())
                .paymentDate(payment.getPaymentDate())
                .createdAt(payment.getCreatedAt())
                .createdByName(payment.getCreatedBy() != null ? payment.getCreatedBy().getFullName() : "")
                .build();
    }

    private void rebuildPaymentLedger(InventoryNote note) {
        BigDecimal totalAmount = Objects.requireNonNullElse(note.getTotalAmount(), BigDecimal.ZERO);
        BigDecimal runningPaid = BigDecimal.ZERO;
        List<InventoryReceiptPayment> payments = inventoryReceiptPaymentRepository
                .findByReceiptIdOrderByPaymentDateAscIdAsc(note.getId());

        for (InventoryReceiptPayment item : payments) {
            BigDecimal amount = Objects.requireNonNullElse(item.getAmount(), BigDecimal.ZERO);
            runningPaid = runningPaid.add(amount);
            BigDecimal remaining = totalAmount.subtract(runningPaid).max(BigDecimal.ZERO);
            item.setRemainingDebtAfter(remaining);
        }

        inventoryReceiptPaymentRepository.saveAll(payments);
        note.setPaymentAmount(runningPaid);
        note.setDebtAmount(totalAmount.subtract(runningPaid).max(BigDecimal.ZERO));
        inventoryNoteRepository.save(note);
    }
}
