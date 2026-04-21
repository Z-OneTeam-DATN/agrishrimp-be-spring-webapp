package com.zone.agri.service;

import com.zone.agri.dto.request.voucher.VoucherRequest;
import com.zone.agri.dto.response.voucher.VoucherResponse;
import com.zone.agri.entity.Voucher;
import com.zone.agri.entity.enums.VoucherStatus;
import com.zone.agri.exception.BadRequestException;
import com.zone.agri.exception.ConflictException;
import com.zone.agri.exception.NotFoundException;
import com.zone.agri.repository.VoucherRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class VoucherService {

    private static final BigDecimal MAX_PERCENT_ALLOW = new BigDecimal("50");

    private final VoucherRepository voucherRepository;

    public List<VoucherResponse> getAllVouchers(String keyword, VoucherStatus status) {
        List<Voucher> vouchers = voucherRepository.searchVouchers(keyword, status);
        return vouchers.stream().map(this::convertToResponseWithDerivedStatus).collect(Collectors.toList());
    }

    public VoucherResponse getVoucherById(Long id) {
        Voucher voucher = voucherRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy voucher với ID: " + id));
        return convertToResponseWithDerivedStatus(voucher);
    }

    private void validateBusinessRules(VoucherRequest request) {
        if (request.getEndDate().isEqual(request.getStartDate())
                || request.getEndDate().isBefore(request.getStartDate())) {
            throw new BadRequestException("Ngày kết thúc không được nhỏ hơn ngày bắt đầu.");
        }

        if (request.getEndDate().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("Ngày kết thúc không được ở trong quá khứ.");
        }

        if (request.getDiscountType() == com.zone.agri.entity.enums.VoucherDiscountType.PERCENT) {
            if (request.getValue() == null || request.getValue().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BadRequestException("Mức giảm phần trăm phải lớn hơn 0%");
            }

            if (request.getMaxDiscount() == null || request.getMaxDiscount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BadRequestException("Voucher theo phần trăm BẮT BUỘC phải có mức Giảm tối đa (VNĐ) để tránh lỗ!");
            }

            if (request.getValue().compareTo(MAX_PERCENT_ALLOW) > 0) {
                throw new BadRequestException("Mức giảm phần trăm không được vượt quá 50%");
            }
        } else if (request.getDiscountType() == com.zone.agri.entity.enums.VoucherDiscountType.FIXED) {
            if (request.getValue() == null || request.getValue().compareTo(new BigDecimal("1000")) <= 0) {
                throw new BadRequestException("Mức giảm (VNĐ) phải lớn hơn 1.000đ");
            }

            BigDecimal minOrder = request.getMinOrderValue() != null ? request.getMinOrderValue() : BigDecimal.ZERO;
            if (minOrder.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal halfMinOrder = minOrder.divide(new BigDecimal("2"), 2, java.math.RoundingMode.HALF_UP);
                if (request.getValue().compareTo(halfMinOrder) > 0) {
                    throw new BadRequestException("Mức giảm (VNĐ) không được vượt quá một nửa Đơn tối thiểu");
                }
            }
        }
    }

    @Transactional
    public VoucherResponse createVoucher(VoucherRequest request) {
        if (voucherRepository.existsByCode(request.getCode())) {
            throw new ConflictException("Mã voucher '" + request.getCode() + "' đã tồn tại!");
        }
        
        validateBusinessRules(request);

        Voucher voucher = new Voucher();
        mapToEntity(voucher, request);
        return convertToResponse(voucherRepository.save(voucher));
    }

    @Transactional
    public VoucherResponse updateVoucher(Long id, VoucherRequest request) {
        Voucher voucher = voucherRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy voucher với ID: " + id));

        if (voucherRepository.existsByCodeAndIdNot(request.getCode(), id)) {
            throw new ConflictException("Mã voucher '" + request.getCode() + "' đã tồn tại!");
        }
        
        validateBusinessRules(request);

        mapToEntity(voucher, request);
        return convertToResponse(voucherRepository.save(voucher));
    }

    @Transactional
    public void deleteVoucher(Long id) {
        Voucher voucher = voucherRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy voucher với ID: " + id));
        voucherRepository.delete(voucher);
    }

    private void mapToEntity(Voucher entity, VoucherRequest request) {
        entity.setCode(request.getCode());
        entity.setTitle(request.getTitle());
        entity.setDiscountType(request.getDiscountType());
        entity.setValue(request.getValue());
        entity.setMaxUsagePerUser(request.getMaxUsagePerUser());
        entity.setMaxDiscount(request.getMaxDiscount());
        entity.setMinOrderValue(request.getMinOrderValue());
        entity.setStartDate(request.getStartDate());
        entity.setEndDate(request.getEndDate());
        entity.setQuantity(request.getQuantity());
        entity.setStatus(request.getStatus() != null ? request.getStatus() : VoucherStatus.ACTIVE);
    }

    private VoucherResponse convertToResponse(Voucher entity) {
        return VoucherResponse.builder()
                .id(entity.getId())
                .code(entity.getCode())
                .title(entity.getTitle())
                .discountType(entity.getDiscountType())
                .value(entity.getValue())
                .maxDiscount(entity.getMaxDiscount())
                .maxUsagePerUser(entity.getMaxUsagePerUser())
                .minOrderValue(entity.getMinOrderValue())
                .startDate(entity.getStartDate())
                .endDate(entity.getEndDate())
                .quantity(entity.getQuantity())
                .status(entity.getStatus())
                .build();
    }

    private VoucherResponse convertToResponseWithDerivedStatus(Voucher entity) {
        return VoucherResponse.builder()
                .id(entity.getId())
                .code(entity.getCode())
                .title(entity.getTitle())
                .discountType(entity.getDiscountType())
                .value(entity.getValue())
                .maxDiscount(entity.getMaxDiscount())
                .maxUsagePerUser(entity.getMaxUsagePerUser())
                .minOrderValue(entity.getMinOrderValue())
                .startDate(entity.getStartDate())
                .endDate(entity.getEndDate())
                .quantity(entity.getQuantity())
                .status(deriveVoucherStatus(entity))
                .build();
    }

    private VoucherStatus deriveVoucherStatus(Voucher entity) {
        if (entity.getStatus() == VoucherStatus.ACTIVE
                && entity.getEndDate() != null
                && entity.getEndDate().isBefore(LocalDateTime.now())) {
            return VoucherStatus.EXPIRED;
        }
        return entity.getStatus();
    }

    public List<VoucherResponse> getPublicVouchers() {
        LocalDateTime now = LocalDateTime.now();
        List<Voucher> vouchers = voucherRepository.findByStatus(VoucherStatus.ACTIVE);
        return vouchers.stream()
                .filter(v -> v.getQuantity() == null || v.getQuantity() > 0)
                .filter(v -> v.getStartDate() == null || !v.getStartDate().isAfter(now))
                .filter(v -> v.getEndDate() == null || !v.getEndDate().isBefore(now))
                .map(this::convertToResponseWithDerivedStatus)
                .collect(Collectors.toList());
    }

    public VoucherResponse getVoucherByCode(String code) {
        Voucher voucher = voucherRepository.findByCode(code)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy voucher với mã: " + code));
        return convertToResponseWithDerivedStatus(voucher);
    }
}