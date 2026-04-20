package com.zone.agri.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import com.zone.agri.common.AuthUtils;
import com.zone.agri.dto.response.financial.ProfitLossResponse;
import com.zone.agri.dto.response.supplier.SupplierDebtResponse;
import com.zone.agri.dto.response.user.UserDetail;
import com.zone.agri.repository.OrderRepository;
import com.zone.agri.repository.SupplierRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FinancialService {

    private final OrderRepository orderRepository;
    private final SupplierRepository supplierRepository;

    private BigDecimal getSafeBigDecimal(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    // 1. BÁO CÁO LÃI LỖ
    public ProfitLossResponse getProfitLossReport(LocalDate startDate, LocalDate endDate, Long branchId) {
        Long finalBranchId = resolveBranchId(branchId);
        LocalDateTime start = (startDate != null) ? startDate.atStartOfDay() : LocalDateTime.of(2000, 1, 1, 0, 0);
        LocalDateTime end = (endDate != null) ? endDate.atTime(23, 59, 59) : LocalDateTime.now();

        BigDecimal revenue = getSafeBigDecimal(orderRepository.sumTotalRevenue(start, end, finalBranchId));
        BigDecimal returnedGoods = getSafeBigDecimal(orderRepository.sumReturnedGoods(start, end, finalBranchId));
        BigDecimal shippingFee = getSafeBigDecimal(orderRepository.sumShippingFee(start, end, finalBranchId));
        BigDecimal discount = getSafeBigDecimal(orderRepository.sumDiscount(start, end, finalBranchId));

        // Tính giá vốn (COGS) chuẩn từ Repository
        BigDecimal cogs = getSafeBigDecimal(orderRepository.sumTotalCost(start, end, finalBranchId));

        return ProfitLossResponse.builder()
                .revenue(revenue)
                .returnedGoods(returnedGoods)
                .vat(BigDecimal.ZERO)
                .shippingFeeCollected(shippingFee)
                .discount(discount)
                .cogs(cogs)
                .pointPayment(BigDecimal.ZERO)
                .shippingFeePaid(BigDecimal.ZERO)
                .otherIncome(BigDecimal.ZERO)
                .customerReturnFee(BigDecimal.ZERO)
                .otherExpenses(BigDecimal.ZERO)
                .build();
    }

    // 2. BÁO CÁO CÔNG NỢ NHÀ CUNG CẤP
    public List<SupplierDebtResponse> getSupplierDebts(
            String search,
            LocalDate startDate,
            LocalDate endDate,
            Long branchId,
            Long staffId,
            String debtFilter) {
        Long finalBranchId = resolveBranchId(branchId);
        LocalDateTime start = startDate != null ? startDate.atStartOfDay() : null;
        LocalDateTime end = endDate != null ? endDate.atTime(23, 59, 59) : null;

        List<SupplierRepository.SupplierDebtProjection> projections = supplierRepository.findSuppliersWithDebt(
                search,
                start,
                end,
                finalBranchId,
                staffId);

        return projections.stream()
                .map(p -> SupplierDebtResponse.builder()
                        .id(p.getId())
                        .supplierCode(p.getSupplierCode())
                        .supplierName(p.getSupplierName())
                        .phone(p.getPhone())
                        .totalDebt(getSafeBigDecimal(p.getTotalDebt()))
                        .build())
                .filter(item -> {
                    if ("zero".equalsIgnoreCase(debtFilter)) {
                        return item.getTotalDebt().compareTo(BigDecimal.ZERO) == 0;
                    }
                    if ("all".equalsIgnoreCase(debtFilter)) {
                        return true;
                    }
                    return item.getTotalDebt().compareTo(BigDecimal.ZERO) > 0;
                })
                .filter(item -> Objects.nonNull(item.getId()))
                .collect(Collectors.toList());
    }

    private Long resolveBranchId(Long requestBranchId) {
        UserDetail currentUser = AuthUtils.getUserDetail();
        if (currentUser == null) {
            throw new AccessDeniedException("Người dùng chưa đăng nhập.");
        }

        String roleSlug = normalizeRoleSlug(currentUser);
        if ("ADMIN".equals(roleSlug) || "SUPER_ADMIN".equals(roleSlug)) {
            return requestBranchId;
        }

        Long userBranchId = currentUser.getBranchId();
        if (userBranchId == null) {
            throw new AccessDeniedException("Người dùng không thuộc chi nhánh nào.");
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
}