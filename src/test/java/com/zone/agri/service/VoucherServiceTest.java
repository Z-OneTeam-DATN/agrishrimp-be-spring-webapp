package com.zone.agri.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.zone.agri.dto.request.voucher.VoucherRequest;
import com.zone.agri.dto.response.voucher.VoucherResponse;
import com.zone.agri.entity.Voucher;
import com.zone.agri.entity.enums.VoucherDiscountType;
import com.zone.agri.entity.enums.VoucherStatus;
import com.zone.agri.exception.BadRequestException;
import com.zone.agri.repository.VoucherRepository;

@ExtendWith(MockitoExtension.class)
class VoucherServiceTest {

    @Mock
    private VoucherRepository voucherRepository;

    @InjectMocks
    private VoucherService voucherService;

    private VoucherRequest percentVoucherRequest;

    @BeforeEach
    void setUp() {
        percentVoucherRequest = VoucherRequest.builder()
                .code("SALE50")
                .title("Sale 50")
                .discountType(VoucherDiscountType.PERCENT)
                .value(new BigDecimal("50"))
                .maxDiscount(new BigDecimal("50000"))
                .maxUsagePerUser(1)
                .minOrderValue(BigDecimal.ZERO)
                .startDate(LocalDateTime.now().plusDays(1))
                .endDate(LocalDateTime.now().plusDays(2))
                .quantity(10)
                .status(VoucherStatus.ACTIVE)
                .build();
    }

    @Test
    void createVoucher_shouldRequireMaxDiscountForPercentVoucher() {
        percentVoucherRequest.setMaxDiscount(null);

        assertThatThrownBy(() -> voucherService.createVoucher(percentVoucherRequest))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("BẮT BUỘC phải có mức Giảm tối đa");
    }

    @Test
    void createVoucher_shouldRejectEndDateBeforeStartDate() {
        percentVoucherRequest.setStartDate(LocalDateTime.now().plusDays(2));
        percentVoucherRequest.setEndDate(LocalDateTime.now().plusDays(1));

        assertThatThrownBy(() -> voucherService.createVoucher(percentVoucherRequest))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Ngày kết thúc không được nhỏ hơn ngày bắt đầu");
    }

    @Test
    void getAllVouchers_shouldReturnExpiredStatusForPastActiveVoucher() {
        Voucher voucher = Voucher.builder()
                .id(1L)
                .code("SALE50")
                .title("Sale 50")
                .discountType(VoucherDiscountType.PERCENT)
                .value(new BigDecimal("50"))
                .maxDiscount(new BigDecimal("50000"))
                .maxUsagePerUser(1)
                .minOrderValue(BigDecimal.ZERO)
                .startDate(LocalDateTime.now().minusDays(2))
                .endDate(LocalDateTime.now().minusHours(1))
                .quantity(10)
                .status(VoucherStatus.ACTIVE)
                .build();

        when(voucherRepository.searchVouchers(any(), any())).thenReturn(List.of(voucher));

        List<VoucherResponse> responses = voucherService.getAllVouchers(null, null);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getStatus()).isEqualTo(VoucherStatus.EXPIRED);
    }

    @Test
    void getVoucherByCode_shouldReturnDerivedExpiredStatus() {
        Voucher voucher = Voucher.builder()
                .id(2L)
                .code("EXPIRED")
                .title("Expired Voucher")
                .discountType(VoucherDiscountType.FIXED)
                .value(new BigDecimal("10000"))
                .maxUsagePerUser(1)
                .minOrderValue(BigDecimal.ZERO)
                .startDate(LocalDateTime.now().minusDays(2))
                .endDate(LocalDateTime.now().minusHours(1))
                .quantity(1)
                .status(VoucherStatus.ACTIVE)
                .build();

        when(voucherRepository.findByCode(anyString())).thenReturn(Optional.of(voucher));

        VoucherResponse response = voucherService.getVoucherByCode("expired");

        assertThat(response.getStatus()).isEqualTo(VoucherStatus.EXPIRED);
    }
}