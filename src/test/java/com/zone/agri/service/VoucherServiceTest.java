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
    void createVoucher_shouldRejectZeroPercentDiscount() {
        percentVoucherRequest.setValue(BigDecimal.ZERO);
        when(voucherRepository.existsByCode(anyString())).thenReturn(false);

        assertThatThrownBy(() -> voucherService.createVoucher(percentVoucherRequest))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Mức giảm phần trăm phải lớn hơn 0%");
    }

    @Test
    void createVoucher_shouldRejectFixedDiscountAtOrBelowThousand() {
        VoucherRequest request = buildFixedVoucherRequest(new BigDecimal("1000"));
        when(voucherRepository.existsByCode(anyString())).thenReturn(false);

        assertThatThrownBy(() -> voucherService.createVoucher(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Mức giảm (VNĐ) phải lớn hơn 1.000đ");
    }

    @Test
    void updateVoucher_shouldRejectZeroPercentDiscount() {
        percentVoucherRequest.setValue(BigDecimal.ZERO);
        when(voucherRepository.findById(1L)).thenReturn(Optional.of(buildPercentEntity()));
        when(voucherRepository.existsByCodeAndIdNot(anyString(), any())).thenReturn(false);

        assertThatThrownBy(() -> voucherService.updateVoucher(1L, percentVoucherRequest))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Mức giảm phần trăm phải lớn hơn 0%");
    }

    @Test
    void updateVoucher_shouldRejectFixedDiscountAtOrBelowThousand() {
        VoucherRequest request = buildFixedVoucherRequest(new BigDecimal("1000"));
        when(voucherRepository.findById(1L)).thenReturn(Optional.of(buildFixedEntity()));
        when(voucherRepository.existsByCodeAndIdNot(anyString(), any())).thenReturn(false);

        assertThatThrownBy(() -> voucherService.updateVoucher(1L, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Mức giảm (VNĐ) phải lớn hơn 1.000đ");
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
    void createVoucher_shouldRejectEndDateEqualStartDate() {
        percentVoucherRequest.setEndDate(percentVoucherRequest.getStartDate());

        assertThatThrownBy(() -> voucherService.createVoucher(percentVoucherRequest))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Ngày kết thúc không được nhỏ hơn ngày bắt đầu");
    }

    @Test
    void createVoucher_shouldAllowFixedVoucherWithZeroMinOrderValue() {
        VoucherRequest request = VoucherRequest.builder()
                .code("FIXEDZERO")
                .title("Fixed zero min order")
                .discountType(VoucherDiscountType.FIXED)
                .value(new BigDecimal("10000"))
                .maxDiscount(null)
                .maxUsagePerUser(1)
                .minOrderValue(BigDecimal.ZERO)
                .startDate(LocalDateTime.now().plusDays(1))
                .endDate(LocalDateTime.now().plusDays(2))
                .quantity(10)
                .status(VoucherStatus.ACTIVE)
                .build();

        when(voucherRepository.existsByCode(anyString())).thenReturn(false);
        when(voucherRepository.save(any(Voucher.class))).thenAnswer(invocation -> invocation.getArgument(0));

        VoucherResponse response = voucherService.createVoucher(request);

        assertThat(response.getCode()).isEqualTo("FIXEDZERO");
        assertThat(response.getValue()).isEqualByComparingTo(new BigDecimal("10000"));
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
    void getAllVouchers_shouldFilterByDerivedActiveStatus() {
        Voucher activeVoucher = Voucher.builder()
                .id(1L)
                .code("ACTIVE10")
                .title("Active voucher")
                .discountType(VoucherDiscountType.FIXED)
                .value(new BigDecimal("10000"))
                .maxUsagePerUser(1)
                .minOrderValue(BigDecimal.ZERO)
                .startDate(LocalDateTime.now().minusDays(1))
                .endDate(LocalDateTime.now().plusDays(1))
                .quantity(10)
                .status(VoucherStatus.ACTIVE)
                .build();

        Voucher inactiveVoucher = Voucher.builder()
                .id(2L)
                .code("INACTIVE10")
                .title("Inactive voucher")
                .discountType(VoucherDiscountType.FIXED)
                .value(new BigDecimal("10000"))
                .maxUsagePerUser(1)
                .minOrderValue(BigDecimal.ZERO)
                .startDate(LocalDateTime.now().minusDays(1))
                .endDate(LocalDateTime.now().plusDays(1))
                .quantity(10)
                .status(VoucherStatus.INACTIVE)
                .build();

        Voucher expiredVoucher = Voucher.builder()
                .id(3L)
                .code("EXPIRED10")
                .title("Expired voucher")
                .discountType(VoucherDiscountType.FIXED)
                .value(new BigDecimal("10000"))
                .maxUsagePerUser(1)
                .minOrderValue(BigDecimal.ZERO)
                .startDate(LocalDateTime.now().minusDays(3))
                .endDate(LocalDateTime.now().minusHours(1))
                .quantity(10)
                .status(VoucherStatus.ACTIVE)
                .build();

        when(voucherRepository.searchVouchers(any(), any())).thenReturn(List.of(
                activeVoucher,
                inactiveVoucher,
                expiredVoucher));

        List<VoucherResponse> responses = voucherService.getAllVouchers(null, VoucherStatus.ACTIVE);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getCode()).isEqualTo("ACTIVE10");
        assertThat(responses.get(0).getStatus()).isEqualTo(VoucherStatus.ACTIVE);
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

    @Test
    void createVoucher_shouldRejectZeroUsageLimit() {
        percentVoucherRequest.setMaxUsagePerUser(0);
        when(voucherRepository.existsByCode(anyString())).thenReturn(false);

        assertThatThrownBy(() -> voucherService.createVoucher(percentVoucherRequest))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("0");
    }

    private VoucherRequest buildFixedVoucherRequest(BigDecimal value) {
        return VoucherRequest.builder()
                .code("FIXED10")
                .title("Fixed 10")
                .discountType(VoucherDiscountType.FIXED)
                .value(value)
                .maxDiscount(null)
                .maxUsagePerUser(1)
                .minOrderValue(new BigDecimal("50000"))
                .startDate(LocalDateTime.now().plusDays(1))
                .endDate(LocalDateTime.now().plusDays(2))
                .quantity(10)
                .status(VoucherStatus.ACTIVE)
                .build();
    }

    private Voucher buildPercentEntity() {
        return Voucher.builder()
                .id(1L)
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

    private Voucher buildFixedEntity() {
        return Voucher.builder()
                .id(1L)
                .code("FIXED10")
                .title("Fixed 10")
                .discountType(VoucherDiscountType.FIXED)
                .value(new BigDecimal("10000"))
                .maxUsagePerUser(1)
                .minOrderValue(new BigDecimal("50000"))
                .startDate(LocalDateTime.now().plusDays(1))
                .endDate(LocalDateTime.now().plusDays(2))
                .quantity(10)
                .status(VoucherStatus.ACTIVE)
                .build();
    }
}
