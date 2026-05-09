package com.zone.agri.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import com.zone.agri.common.AuthUtils;
import com.zone.agri.dto.response.financial.CashbookEntryResponse;
import com.zone.agri.dto.response.financial.CashbookReportResponse;
import com.zone.agri.dto.response.financial.CashbookSummaryResponse;
import com.zone.agri.dto.response.financial.ProfitLossResponse;
import com.zone.agri.dto.response.supplier.SupplierDebtResponse;
import com.zone.agri.dto.response.user.UserDetail;
import com.zone.agri.entity.InventoryReceiptPayment;
import com.zone.agri.entity.enums.InventoryNoteType;
import com.zone.agri.repository.InventoryReceiptPaymentRepository;
import com.zone.agri.repository.InventoryTransactionRepository;
import com.zone.agri.repository.OrderRepository;
import com.zone.agri.repository.SubOrderRepository;
import com.zone.agri.repository.SupplierRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FinancialService {

    private final OrderRepository orderRepository;
    private final SubOrderRepository subOrderRepository;
    private final SupplierRepository supplierRepository;
    private final InventoryReceiptPaymentRepository inventoryReceiptPaymentRepository;
    private final InventoryTransactionRepository inventoryTransactionRepository;

    public ProfitLossResponse getProfitLossReport(LocalDate startDate, LocalDate endDate, Long branchId) {
        Long finalBranchId = resolveBranchId(branchId);
        LocalDateTime start = startDate != null
                ? startDate.atStartOfDay()
                : LocalDateTime.of(2000, 1, 1, 0, 0);
        LocalDateTime end = endDate != null
                ? endDate.atTime(23, 59, 59)
                : LocalDateTime.now();

        FinancialAccumulator totals = new FinancialAccumulator();
        Set<String> recognizedReferenceCodes = new LinkedHashSet<>();

        List<OrderRepository.LegacyFinancialOrderProjection> legacyOrders = orderRepository
                .findLegacyFinancialOrders(end, finalBranchId);
        for (OrderRepository.LegacyFinancialOrderProjection order : legacyOrders) {
            processFinancialFlow(
                    getSafeBigDecimal(order.getProductAmount()),
                    getSafeBigDecimal(order.getShippingAmount()),
                    getSafeBigDecimal(order.getDiscountAmount()),
                    resolveRecognitionAt(order.getReceivedAt(), order.getCompletedAt()),
                    order.getReturnedAt(),
                    order.getOrderCode(),
                    start,
                    end,
                    totals,
                    recognizedReferenceCodes);
        }

        List<SubOrderRepository.FinancialSubOrderProjection> subOrders = subOrderRepository
                .findFinancialSubOrders(end, finalBranchId);
        for (SubOrderRepository.FinancialSubOrderProjection subOrder : subOrders) {
            BigDecimal allocatedDiscount = allocateDiscount(
                    getSafeBigDecimal(subOrder.getSubtotal()),
                    getSafeBigDecimal(subOrder.getOrderSubtotal()),
                    getSafeBigDecimal(subOrder.getOrderDiscountAmount()));

            processFinancialFlow(
                    getSafeBigDecimal(subOrder.getSubtotal()),
                    getSafeBigDecimal(subOrder.getShippingFee()),
                    allocatedDiscount,
                    resolveRecognitionAt(subOrder.getReceivedAt(), subOrder.getCompletedAt()),
                    subOrder.getReturnedAt(),
                    buildSubOrderReferenceCode(subOrder.getOrderCode(), subOrder.getSubOrderId()),
                    start,
                    end,
                    totals,
                    recognizedReferenceCodes);
        }

        BigDecimal vat = BigDecimal.ZERO;
        BigDecimal cogs = sumRecognizedCosts(recognizedReferenceCodes, finalBranchId);
        BigDecimal pointPayment = BigDecimal.ZERO;
        BigDecimal shippingFeePaid = BigDecimal.ZERO;
        BigDecimal otherIncome = BigDecimal.ZERO;
        BigDecimal customerReturnFee = BigDecimal.ZERO;
        BigDecimal otherExpenses = BigDecimal.ZERO;

        BigDecimal netProductRevenue = totals.grossRevenue.subtract(totals.returnedGoods);
        BigDecimal netRevenue = netProductRevenue
                .add(vat)
                .add(totals.shippingFeeCollected)
                .subtract(totals.shippingFeeReturned)
                .subtract(totals.discount)
                .add(totals.discountReturned);
        BigDecimal grossProfit = netRevenue.subtract(cogs);
        BigDecimal netProfit = grossProfit
                .add(otherIncome)
                .add(customerReturnFee)
                .subtract(pointPayment)
                .subtract(shippingFeePaid)
                .subtract(otherExpenses);

        return ProfitLossResponse.builder()
                .grossRevenue(totals.grossRevenue)
                .returnedGoods(totals.returnedGoods)
                .vat(vat)
                .shippingFeeCollected(totals.shippingFeeCollected)
                .shippingFeeReturned(totals.shippingFeeReturned)
                .discount(totals.discount)
                .discountReturned(totals.discountReturned)
                .netProductRevenue(netProductRevenue)
                .netRevenue(netRevenue)
                .cogs(cogs)
                .pointPayment(pointPayment)
                .shippingFeePaid(shippingFeePaid)
                .grossProfit(grossProfit)
                .otherIncome(otherIncome)
                .customerReturnFee(customerReturnFee)
                .otherExpenses(otherExpenses)
                .netProfit(netProfit)
                .build();
    }

    public List<SupplierDebtResponse> getSupplierDebts(
            String search,
            LocalDate startDate,
            LocalDate endDate,
            Long branchId,
            Long staffId,
            String debtFilter) {
        Long finalBranchId = resolveBranchId(branchId);
        String normalizedSearch = normalizeSearch(search);
        LocalDateTime snapshotDate = endDate != null
                ? endDate.atTime(23, 59, 59)
                : LocalDateTime.now();

        List<SupplierRepository.SupplierDebtLedgerProjection> ledgerRows = supplierRepository
                .findSupplierDebtLedger(normalizedSearch, snapshotDate, finalBranchId, staffId);

        Map<Long, BigDecimal> debtBySupplier = ledgerRows.stream()
                .filter(row -> row.getSupplierId() != null)
                .collect(Collectors.groupingBy(
                        SupplierRepository.SupplierDebtLedgerProjection::getSupplierId,
                        Collectors.reducing(BigDecimal.ZERO, this::mapOutstandingContribution, BigDecimal::add)));

        Map<Long, SupplierRepository.SupplierDebtLedgerProjection> supplierInfoById = ledgerRows.stream()
                .filter(row -> row.getSupplierId() != null)
                .collect(Collectors.toMap(
                        SupplierRepository.SupplierDebtLedgerProjection::getSupplierId,
                        row -> row,
                        (left, right) -> left));

        return supplierInfoById.values().stream()
                .map(row -> SupplierDebtResponse.builder()
                        .id(row.getSupplierId())
                        .supplierCode(row.getSupplierCode())
                        .supplierName(row.getSupplierName())
                        .phone(row.getPhone())
                        .totalDebt(maxZero(debtBySupplier.getOrDefault(row.getSupplierId(), BigDecimal.ZERO)))
                        .build())
                .filter(item -> item.getId() != null)
                .filter(item -> matchesDebtFilter(item.getTotalDebt(), debtFilter))
                .sorted(Comparator
                        .comparing(SupplierDebtResponse::getTotalDebt, Comparator.reverseOrder())
                        .thenComparing(item -> Objects.toString(item.getSupplierName(), "")))
                .toList();
    }

    public CashbookReportResponse getCashbookReport(LocalDate startDate, LocalDate endDate, Long branchId) {
        Long finalBranchId = resolveBranchId(branchId);
        LocalDateTime start = startDate != null ? startDate.atStartOfDay() : null;
        LocalDateTime end = endDate != null ? endDate.atTime(23, 59, 59) : LocalDateTime.now();

        List<CashbookEntryResponse> entries = inventoryReceiptPaymentRepository
                .findAllWithFilters(start, end, finalBranchId)
                .stream()
                .map(this::mapCashbookEntry)
                .toList();

        BigDecimal openingExpense = start != null
                ? getSafeBigDecimal(inventoryReceiptPaymentRepository.sumAmountBeforeDate(start, finalBranchId))
                : BigDecimal.ZERO;
        BigDecimal totalExpense = getSafeBigDecimal(
                inventoryReceiptPaymentRepository.sumAmountInRange(start, end, finalBranchId));
        BigDecimal openingBalance = openingExpense.negate();
        BigDecimal totalIncome = BigDecimal.ZERO;
        BigDecimal closingBalance = openingBalance.add(totalIncome).subtract(totalExpense);

        return CashbookReportResponse.builder()
                .summary(CashbookSummaryResponse.builder()
                        .openingBalance(openingBalance)
                        .totalIncome(totalIncome)
                        .totalExpense(totalExpense)
                        .closingBalance(closingBalance)
                        .build())
                .entries(entries)
                .build();
    }

    private void processFinancialFlow(
            BigDecimal productAmount,
            BigDecimal shippingAmount,
            BigDecimal discountAmount,
            LocalDateTime recognitionAt,
            LocalDateTime returnedAt,
            String referenceCode,
            LocalDateTime start,
            LocalDateTime end,
            FinancialAccumulator totals,
            Set<String> recognizedReferenceCodes) {
        if (isInRange(recognitionAt, start, end)) {
            totals.grossRevenue = totals.grossRevenue.add(productAmount);
            totals.shippingFeeCollected = totals.shippingFeeCollected.add(shippingAmount);
            totals.discount = totals.discount.add(discountAmount);
            if (referenceCode != null && !referenceCode.isBlank()) {
                recognizedReferenceCodes.add(referenceCode);
            }
        }

        if (recognitionAt != null
                && returnedAt != null
                && !recognitionAt.isAfter(returnedAt)
                && isInRange(returnedAt, start, end)) {
            totals.returnedGoods = totals.returnedGoods.add(productAmount);
            totals.shippingFeeReturned = totals.shippingFeeReturned.add(shippingAmount);
            totals.discountReturned = totals.discountReturned.add(discountAmount);
        }
    }

    private BigDecimal sumRecognizedCosts(Set<String> referenceCodes, Long branchId) {
        if (referenceCodes.isEmpty()) {
            return BigDecimal.ZERO;
        }

        return inventoryTransactionRepository.sumSaleCostByReferenceCodes(referenceCodes, branchId).stream()
                .map(InventoryTransactionRepository.ReferenceCostProjection::getCost)
                .map(this::getSafeBigDecimal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal allocateDiscount(BigDecimal subtotal, BigDecimal orderSubtotal, BigDecimal orderDiscount) {
        if (subtotal.compareTo(BigDecimal.ZERO) <= 0
                || orderSubtotal.compareTo(BigDecimal.ZERO) <= 0
                || orderDiscount.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        return orderDiscount.multiply(subtotal)
                .divide(orderSubtotal, 2, RoundingMode.HALF_UP);
    }

    private LocalDateTime resolveRecognitionAt(LocalDateTime receivedAt, LocalDateTime completedAt) {
        if (receivedAt == null) {
            return completedAt;
        }
        if (completedAt == null) {
            return receivedAt;
        }
        return receivedAt.isBefore(completedAt) ? receivedAt : completedAt;
    }

    private boolean isInRange(LocalDateTime value, LocalDateTime start, LocalDateTime end) {
        return value != null && !value.isBefore(start) && !value.isAfter(end);
    }

    private BigDecimal mapOutstandingContribution(SupplierRepository.SupplierDebtLedgerProjection row) {
        BigDecimal totalAmount = getSafeBigDecimal(row.getTotalAmount());
        BigDecimal paidAmount = getSafeBigDecimal(row.getPaidAmount());
        InventoryNoteType noteType = row.getNoteType();

        if (noteType == InventoryNoteType.IMPORT) {
            BigDecimal outstanding = totalAmount.subtract(paidAmount);
            return outstanding.compareTo(BigDecimal.ZERO) > 0 ? outstanding : BigDecimal.ZERO;
        }
        if (noteType == InventoryNoteType.EXPORT) {
            return totalAmount.negate();
        }
        return BigDecimal.ZERO;
    }

    private CashbookEntryResponse mapCashbookEntry(InventoryReceiptPayment payment) {
        LocalDate paymentLocalDate = payment.getPaymentDate() != null
                ? payment.getPaymentDate().toLocalDate()
                : payment.getCreatedAt().toLocalDate();
        String description = firstNonBlank(
                payment.getReferenceCode(),
                payment.getNote(),
                payment.getSupplier() != null ? payment.getSupplier().getName() : null,
                "Thanh toan phieu nhap");

        return CashbookEntryResponse.builder()
                .id("supplier-payment-" + payment.getId())
                .date(paymentLocalDate)
                .branchId(payment.getBranch() != null ? payment.getBranch().getId() : null)
                .direction("OUT")
                .source("SUPPLIER_PAYMENT")
                .code(payment.getInventoryNote() != null ? payment.getInventoryNote().getCode() : "PAY-" + payment.getId())
                .title("Thanh toan NCC")
                .description(description)
                .branchName(payment.getBranch() != null ? payment.getBranch().getName() : "")
                .partnerName(payment.getSupplier() != null ? payment.getSupplier().getName() : "")
                .creatorName(payment.getCreatedBy() != null ? payment.getCreatedBy().getFullName() : "")
                .paymentMethod(payment.getPaymentMethod() != null ? payment.getPaymentMethod().name() : "")
                .amount(getSafeBigDecimal(payment.getAmount()))
                .debtAmount(getSafeBigDecimal(payment.getRemainingDebtAfter()))
                .paymentAmount(getSafeBigDecimal(payment.getAmount()))
                .build();
    }

    private boolean matchesDebtFilter(BigDecimal totalDebt, String debtFilter) {
        if ("zero".equalsIgnoreCase(debtFilter)) {
            return totalDebt.compareTo(BigDecimal.ZERO) == 0;
        }
        if ("all".equalsIgnoreCase(debtFilter)) {
            return true;
        }
        return totalDebt.compareTo(BigDecimal.ZERO) > 0;
    }

    private BigDecimal maxZero(BigDecimal value) {
        return value.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : value;
    }

    private String normalizeSearch(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        return search.trim();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private String buildSubOrderReferenceCode(String orderCode, Long subOrderId) {
        return orderCode + "-SUB-" + subOrderId;
    }

    private BigDecimal getSafeBigDecimal(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private Long resolveBranchId(Long requestBranchId) {
        UserDetail currentUser = AuthUtils.getUserDetail();
        if (currentUser == null) {
            throw new AccessDeniedException("Nguoi dung chua dang nhap.");
        }

        String roleSlug = normalizeRoleSlug(currentUser);
        if ("ADMIN".equals(roleSlug) || "SUPER_ADMIN".equals(roleSlug)) {
            return requestBranchId;
        }

        Long userBranchId = currentUser.getBranchId();
        if (userBranchId == null) {
            throw new AccessDeniedException("Nguoi dung khong thuoc chi nhanh nao.");
        }

        return userBranchId;
    }

    private String normalizeRoleSlug(UserDetail userDetail) {
        if (userDetail.getRole() == null || userDetail.getRole().getSlug() == null) {
            return "";
        }
        String slug = userDetail.getRole().getSlug().trim().toUpperCase();
        return slug.startsWith("ROLE_") ? slug.substring(5) : slug;
    }

    private static final class FinancialAccumulator {
        private BigDecimal grossRevenue = BigDecimal.ZERO;
        private BigDecimal returnedGoods = BigDecimal.ZERO;
        private BigDecimal shippingFeeCollected = BigDecimal.ZERO;
        private BigDecimal shippingFeeReturned = BigDecimal.ZERO;
        private BigDecimal discount = BigDecimal.ZERO;
        private BigDecimal discountReturned = BigDecimal.ZERO;
    }
}
