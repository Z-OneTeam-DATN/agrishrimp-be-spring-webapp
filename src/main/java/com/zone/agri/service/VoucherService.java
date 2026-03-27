package com.zone.agri.service;

import com.zone.agri.dto.request.voucher.VoucherRequest;
import com.zone.agri.dto.response.voucher.VoucherResponse;
import com.zone.agri.entity.Voucher;
import com.zone.agri.entity.enums.VoucherStatus;
import com.zone.agri.exception.ConflictException;
import com.zone.agri.exception.NotFoundException;
import com.zone.agri.repository.VoucherRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class VoucherService {

    private final VoucherRepository voucherRepository;

    public List<VoucherResponse> getAllVouchers(String keyword, VoucherStatus status) {
        List<Voucher> vouchers = voucherRepository.searchVouchers(keyword, status);
        return vouchers.stream().map(this::convertToResponse).collect(Collectors.toList());
    }

    public VoucherResponse getVoucherById(Long id) {
        Voucher voucher = voucherRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy voucher với ID: " + id));
        return convertToResponse(voucher);
    }

    @Transactional
    public VoucherResponse createVoucher(VoucherRequest request) {
        if (voucherRepository.existsByCode(request.getCode())) {
            throw new ConflictException("Mã voucher '" + request.getCode() + "' đã tồn tại!");
        }

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
        entity.setDiscountType(request.getDiscountType());
        entity.setValue(request.getValue());
        entity.setMaxUsagePerUser(request.getMaxUsagePerUser());
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
                .discountType(entity.getDiscountType())
                .value(entity.getValue())
                .maxUsagePerUser(entity.getMaxUsagePerUser())
                .minOrderValue(entity.getMinOrderValue())
                .startDate(entity.getStartDate())
                .endDate(entity.getEndDate())
                .quantity(entity.getQuantity())
                .status(entity.getStatus())
                .build();
    }

    public List<VoucherResponse> getPublicVouchers() {
        List<Voucher> vouchers = voucherRepository.findByStatus(VoucherStatus.ACTIVE);
        return vouchers.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    public VoucherResponse getVoucherByCode(String code) {
        Voucher voucher = voucherRepository.findByCode(code)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy voucher với mã: " + code));
        return convertToResponse(voucher);
    }
}
