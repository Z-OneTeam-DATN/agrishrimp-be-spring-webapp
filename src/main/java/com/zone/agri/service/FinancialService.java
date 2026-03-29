package com.zone.agri.service;

import com.zone.agri.dto.response.financial.ProfitLossResponse;
import com.zone.agri.dto.response.supplier.SupplierDebtResponse;
import com.zone.agri.repository.OrderRepository;
import com.zone.agri.repository.SupplierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

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
        LocalDateTime start = (startDate != null) ? startDate.atStartOfDay() : LocalDateTime.of(2000, 1, 1, 0, 0);
        LocalDateTime end = (endDate != null) ? endDate.atTime(23, 59, 59) : LocalDateTime.now();

        BigDecimal revenue = getSafeBigDecimal(orderRepository.sumTotalRevenue(start, end, branchId));
        BigDecimal returnedGoods = getSafeBigDecimal(orderRepository.sumReturnedGoods(start, end, branchId));
        BigDecimal shippingFee = getSafeBigDecimal(orderRepository.sumShippingFee(start, end, branchId));
        BigDecimal discount = getSafeBigDecimal(orderRepository.sumDiscount(start, end, branchId));

        // Tính giá vốn (COGS) chuẩn từ Repository
        BigDecimal cogs = getSafeBigDecimal(orderRepository.sumTotalCost(start, end, branchId));

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
    public List<SupplierDebtResponse> getSupplierDebts(String search) {
        List<SupplierRepository.SupplierDebtProjection> projections = supplierRepository.findSuppliersWithDebt(search);

        return projections.stream().map(p -> SupplierDebtResponse.builder()
                .id(p.getId())
                .supplierCode(p.getSupplierCode())
                .supplierName(p.getSupplierName())
                .phone(p.getPhone())
                .totalDebt(p.getTotalDebt())
                .build()
        ).collect(Collectors.toList());
    }
}