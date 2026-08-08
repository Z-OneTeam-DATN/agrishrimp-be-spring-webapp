package com.zone.agri.service;

import com.zone.agri.dto.request.voucher.VoucherRequest;
import com.zone.agri.dto.response.voucher.UserVoucherResponse;
import com.zone.agri.dto.response.voucher.VoucherResponse;
import com.zone.agri.entity.Order;
import com.zone.agri.entity.User;
import com.zone.agri.entity.UserVoucher;
import com.zone.agri.entity.Voucher;
import com.zone.agri.entity.enums.VoucherDiscountType;
import com.zone.agri.entity.enums.VoucherStatus;
import com.zone.agri.exception.BadRequestException;
import com.zone.agri.exception.ConflictException;
import com.zone.agri.exception.NotFoundException;
import com.zone.agri.repository.UserRepository;
import com.zone.agri.repository.UserVoucherRepository;
import com.zone.agri.repository.VoucherRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class VoucherService {

    private static final BigDecimal MAX_PERCENT_ALLOW = new BigDecimal("50");

    private final VoucherRepository voucherRepository;
    private final UserRepository userRepository;
    private final UserVoucherRepository userVoucherRepository;

    public record VoucherOrderEvaluation(
            Voucher voucher,
            UserVoucher userVoucher,
            BigDecimal discountAmount) {
    }

    private record VoucherAvailability(
            boolean visibleToUser,
            boolean canApply,
            String reason,
            BigDecimal previewDiscountAmount,
            int usageCount,
            int remainingUsageCount) {
    }

    public List<VoucherResponse> getAllVouchers(String keyword, VoucherStatus status) {
        String normalizedKeyword = keyword != null ? keyword.trim() : null;
        if (normalizedKeyword != null && normalizedKeyword.isBlank()) {
            normalizedKeyword = null;
        }

        List<Voucher> vouchers = voucherRepository.searchVouchers(normalizedKeyword, null);
        return vouchers.stream()
                .filter(voucher -> status == null || deriveVoucherStatus(voucher) == status)
                .map(this::convertToResponseWithDerivedStatus)
                .toList();
    }

    public VoucherResponse getVoucherById(Long id) {
        Voucher voucher = voucherRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy voucher với ID: " + id));
        return convertToResponseWithDerivedStatus(voucher);
    }

    public String normalizeVoucherCode(String code) {
        if (code == null) {
            return null;
        }

        String normalized = code.trim().toUpperCase();
        return normalized.isBlank() ? null : normalized;
    }

    private void validateUsageLimit(Integer maxUsagePerUser) {
        if (maxUsagePerUser == null || maxUsagePerUser <= 0) {
            throw new BadRequestException("Lượt dùng tối đa mỗi người phải lớn hơn 0");
        }
    }

    private void validateBusinessRules(VoucherRequest request, boolean allowPastEndDate) {
        if (request.getEndDate().isEqual(request.getStartDate())
                || request.getEndDate().isBefore(request.getStartDate())) {
            throw new BadRequestException("Ngày kết thúc không được nhỏ hơn ngày bắt đầu.");
        }

        if (!allowPastEndDate && request.getEndDate().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("Ngày kết thúc không được ở trong quá khứ.");
        }

        if (request.getDiscountType() == VoucherDiscountType.PERCENT) {
            if (request.getValue() == null || request.getValue().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BadRequestException("Mức giảm phần trăm phải lớn hơn 0%");
            }

            if (request.getMaxDiscount() == null || request.getMaxDiscount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BadRequestException("Voucher theo phần trăm bắt buộc phải có mức giảm tối đa (VNĐ) để tránh lỗ.");
            }

            if (request.getValue().compareTo(MAX_PERCENT_ALLOW) > 0) {
                throw new BadRequestException("Mức giảm phần trăm không được vượt quá 50%");
            }
        } else if (request.getDiscountType() == VoucherDiscountType.FIXED) {
            if (request.getValue() == null || request.getValue().compareTo(new BigDecimal("1000")) <= 0) {
                throw new BadRequestException("Mức giảm (VNĐ) phải lớn hơn 1.000đ");
            }

            BigDecimal minOrder = request.getMinOrderValue() != null ? request.getMinOrderValue() : BigDecimal.ZERO;
            if (minOrder.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal halfMinOrder = minOrder.divide(new BigDecimal("2"), 2, java.math.RoundingMode.HALF_UP);
                if (request.getValue().compareTo(halfMinOrder) > 0) {
                    throw new BadRequestException("Mức giảm (VNĐ) không được vượt quá một nửa đơn tối thiểu");
                }
            }
        }
    }

    @Transactional
    public VoucherResponse createVoucher(VoucherRequest request) {
        String normalizedCode = requireNormalizedVoucherCode(request.getCode());
        if (voucherRepository.existsByCode(normalizedCode)) {
            throw new ConflictException("Mã voucher '" + request.getCode() + "' đã tồn tại!");
        }

        validateUsageLimit(request.getMaxUsagePerUser());
        validateBusinessRules(request, false);

        Voucher voucher = new Voucher();
        mapToEntity(voucher, request, normalizedCode);
        return convertToResponse(voucherRepository.save(voucher));
    }

    @Transactional
    public VoucherResponse updateVoucher(Long id, VoucherRequest request) {
        Voucher voucher = voucherRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy voucher với ID: " + id));

        String normalizedCode = requireNormalizedVoucherCode(request.getCode());

        if (voucherRepository.existsByCodeAndIdNot(normalizedCode, id)) {
            throw new ConflictException("Mã voucher '" + request.getCode() + "' đã tồn tại!");
        }

        boolean allowPastEndDate = voucher.getEndDate() != null
                && voucher.getEndDate().isBefore(LocalDateTime.now());

        validateUsageLimit(request.getMaxUsagePerUser());
        validateBusinessRules(request, allowPastEndDate);

        mapToEntity(voucher, request, normalizedCode);
        return convertToResponse(voucherRepository.save(voucher));
    }

    @Transactional
    public void deleteVoucher(Long id) {
        Voucher voucher = voucherRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy voucher với ID: " + id));
        voucherRepository.delete(voucher);
    }

    public List<UserVoucherResponse> getAvailableVouchersForUser(Long userId, BigDecimal orderSubtotal) {
        LocalDateTime now = LocalDateTime.now();
        List<Voucher> vouchers = voucherRepository.findByStatus(VoucherStatus.ACTIVE);
        Map<Long, UserVoucher> userVoucherMap = buildUserVoucherMap(
                userId,
                vouchers.stream().map(Voucher::getId).toList());

        return vouchers.stream()
                .filter(voucher -> evaluateVoucherForUser(
                        voucher,
                        userVoucherMap.get(voucher.getId()),
                        null,
                        now).visibleToUser())
                .map(voucher -> buildUserVoucherResponse(
                        voucher,
                        userVoucherMap.get(voucher.getId()),
                        orderSubtotal,
                        now))
                .sorted((left, right) -> {
                    if (Objects.equals(left.getSaved(), right.getSaved())) {
                        return Long.compare(
                                Objects.requireNonNullElse(right.getId(), 0L),
                                Objects.requireNonNullElse(left.getId(), 0L));
                    }
                    return Boolean.TRUE.equals(right.getSaved()) ? 1 : -1;
                })
                .toList();
    }

    public List<UserVoucherResponse> getSavedVouchersForUser(Long userId, BigDecimal orderSubtotal) {
        LocalDateTime now = LocalDateTime.now();
        return userVoucherRepository.findSavedByUserId(userId).stream()
                .map(userVoucher -> buildUserVoucherResponse(
                        userVoucher.getVoucher(),
                        userVoucher,
                        orderSubtotal,
                        now))
                .toList();
    }

    @Transactional
    public UserVoucherResponse saveVoucherForUser(Long userId, String code) {
        User user = getUserById(userId);
        String normalizedCode = requireNormalizedVoucherCode(code);
        Voucher voucher = voucherRepository.findByCode(normalizedCode)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy voucher với mã: " + normalizedCode));

        UserVoucher userVoucher = userVoucherRepository.findByUserAndVoucher(user, voucher)
                .orElse(new UserVoucher(user, voucher, 0, false));
        VoucherAvailability availability = evaluateVoucherForUser(voucher, userVoucher, null, LocalDateTime.now());
        if (!availability.visibleToUser()) {
            throw new BadRequestException(availability.reason());
        }

        userVoucher.setIsSaved(true);
        if (userVoucher.getCreatedAt() == null) {
            userVoucher.setCreatedAt(LocalDateTime.now());
        }

        UserVoucher savedUserVoucher = userVoucherRepository.save(userVoucher);
        return buildUserVoucherResponse(voucher, savedUserVoucher, null, LocalDateTime.now());
    }

    @Transactional
    public void removeSavedVoucherForUser(Long userId, String code) {
        String normalizedCode = requireNormalizedVoucherCode(code);
        UserVoucher userVoucher = userVoucherRepository.findByUserIdAndVoucherCode(userId, normalizedCode)
                .orElseThrow(() -> new NotFoundException("Voucher chưa có trong ví của bạn"));

        if (!Boolean.TRUE.equals(userVoucher.getIsSaved())) {
            throw new NotFoundException("Voucher chưa có trong ví của bạn");
        }

        userVoucher.setIsSaved(false);
        userVoucherRepository.save(userVoucher);
    }

    @Transactional
    public VoucherOrderEvaluation validateVoucherForOrder(
            User user,
            String voucherCode,
            BigDecimal orderSubtotal,
            boolean consume,
            boolean conflictOnUnavailable) {
        String normalizedVoucherCode = normalizeVoucherCode(voucherCode);
        if (normalizedVoucherCode == null) {
            return new VoucherOrderEvaluation(null, null, BigDecimal.ZERO);
        }

        Voucher voucher = (consume ? voucherRepository.findByCodeForUpdate(normalizedVoucherCode)
                : voucherRepository.findByCode(normalizedVoucherCode))
                .orElseThrow(() -> voucherValidationException(conflictOnUnavailable, "Mã voucher không tồn tại"));

        UserVoucher userVoucher = userVoucherRepository.findByUserAndVoucher(user, voucher)
                .orElse(new UserVoucher(user, voucher, 0, false));
        VoucherAvailability availability = evaluateVoucherForUser(
                voucher,
                userVoucher,
                orderSubtotal,
                LocalDateTime.now());

        if (!availability.canApply()) {
            throw voucherValidationException(conflictOnUnavailable, availability.reason());
        }

        if (consume) {
            voucher.setQuantity(Objects.requireNonNullElse(voucher.getQuantity(), 0) - 1);
            userVoucher.setUsageCount(Objects.requireNonNullElse(userVoucher.getUsageCount(), 0) + 1);
            voucherRepository.save(voucher);
            try {
                userVoucherRepository.saveAndFlush(userVoucher);
            } catch (DataIntegrityViolationException ex) {
                throw new ConflictException("Bạn đang thanh toán đồng thời với cùng voucher này. Vui lòng thử lại.");
            }
        }

        return new VoucherOrderEvaluation(voucher, userVoucher, availability.previewDiscountAmount());
    }

    @Transactional
    public void restoreVoucherForOrder(Order order) {
        if (order.getVoucher() == null) {
            return;
        }

        Voucher voucher = voucherRepository.findByIdForUpdate(order.getVoucher().getId()).orElse(null);
        if (voucher == null) {
            return;
        }

        voucher.setQuantity(Objects.requireNonNullElse(voucher.getQuantity(), 0) + 1);
        voucherRepository.save(voucher);

        userVoucherRepository.findByUserAndVoucher(order.getUser(), voucher).ifPresent(userVoucher -> {
            int currentUsage = Objects.requireNonNullElse(userVoucher.getUsageCount(), 0);
            if (currentUsage > 0) {
                userVoucher.setUsageCount(currentUsage - 1);
                userVoucherRepository.save(userVoucher);
            }
        });
    }

    public BigDecimal calculateDiscountAmount(Voucher voucher, BigDecimal orderSubtotal) {
        if (voucher == null || orderSubtotal == null || orderSubtotal.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal discountAmount;
        if (VoucherDiscountType.PERCENT.equals(voucher.getDiscountType())) {
            BigDecimal percentValue = voucher.getValue() != null ? voucher.getValue() : BigDecimal.ZERO;
            BigDecimal calculatedDiscount = orderSubtotal.multiply(percentValue).divide(BigDecimal.valueOf(100));

            discountAmount = voucher.getMaxDiscount() != null && voucher.getMaxDiscount().compareTo(BigDecimal.ZERO) > 0
                    ? calculatedDiscount.min(voucher.getMaxDiscount())
                    : calculatedDiscount;
        } else {
            discountAmount = voucher.getValue() != null ? voucher.getValue() : BigDecimal.ZERO;
        }

        return discountAmount.compareTo(orderSubtotal) > 0 ? orderSubtotal : discountAmount;
    }

    private void mapToEntity(Voucher entity, VoucherRequest request, String normalizedCode) {
        entity.setCode(normalizedCode);
        entity.setTitle(request.getTitle() != null ? request.getTitle().trim() : null);
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
        // Thêm buffer 5 phút để tránh lệch múi giờ nhỏ giữa server và client
        LocalDateTime nowWithBuffer = now.plusMinutes(5);
        LocalDateTime nowMinusBuffer = now.minusMinutes(5);

        List<Voucher> vouchers = voucherRepository.findByStatus(VoucherStatus.ACTIVE);
        log.info("Checking {} ACTIVE vouchers from database. Current Server Time: {}", vouchers.size(), now);
        
        List<VoucherResponse> result = vouchers.stream()
                .filter(v -> {
                    boolean ok = v.getQuantity() == null || v.getQuantity() > 0;
                    if (!ok) log.warn("Voucher {} filtered out: quantity = {}", v.getCode(), v.getQuantity());
                    return ok;
                })
                .filter(v -> {
                    // Cho phép bắt đầu sớm hơn 5 phút
                    boolean ok = v.getStartDate() == null || !v.getStartDate().isAfter(nowWithBuffer);
                    if (!ok) log.warn("Voucher {} filtered out: startDate {} is in the future (Now: {})", v.getCode(), v.getStartDate(), now);
                    return ok;
                })
                .filter(v -> {
                    // Cho phép kết thúc muộn hơn 5 phút
                    boolean ok = v.getEndDate() == null || !v.getEndDate().isBefore(nowMinusBuffer);
                    if (!ok) log.warn("Voucher {} filtered out: endDate {} is in the past (Now: {})", v.getCode(), v.getEndDate(), now);
                    return ok;
                })
                .map(this::convertToResponseWithDerivedStatus)
                .toList();
        
        log.info("Returning {} public vouchers to client", result.size());
        return result;
    }

    public VoucherResponse getVoucherByCode(String code) {
        String normalizedCode = requireNormalizedVoucherCode(code);
        Voucher voucher = voucherRepository.findByCode(normalizedCode)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy voucher với mã: " + code));
        return convertToResponseWithDerivedStatus(voucher);
    }

    private String requireNormalizedVoucherCode(String code) {
        String normalizedCode = normalizeVoucherCode(code);
        if (normalizedCode == null) {
            throw new BadRequestException("Mã voucher không được để trống");
        }
        return normalizedCode;
    }

    private User getUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Người dùng không tồn tại"));
    }

    private Map<Long, UserVoucher> buildUserVoucherMap(Long userId, Collection<Long> voucherIds) {
        if (voucherIds == null || voucherIds.isEmpty()) {
            return Map.of();
        }

        return userVoucherRepository.findByUserIdAndVoucherIds(userId, voucherIds).stream()
                .collect(Collectors.toMap(userVoucher -> userVoucher.getVoucher().getId(), Function.identity()));
    }

    private VoucherAvailability evaluateVoucherForUser(
            Voucher voucher,
            UserVoucher userVoucher,
            BigDecimal orderSubtotal,
            LocalDateTime now) {
        int usageCount = Objects.requireNonNullElse(userVoucher != null ? userVoucher.getUsageCount() : null, 0);
        int maxUsagePerUser = voucher.getMaxUsagePerUser() != null ? voucher.getMaxUsagePerUser() : 1;
        int remainingUsageCount = Math.max(0, maxUsagePerUser - usageCount);

        if (voucher.getStatus() != VoucherStatus.ACTIVE
                || voucher.getStartDate() == null
                || voucher.getEndDate() == null
                || now.isBefore(voucher.getStartDate())
                || now.isAfter(voucher.getEndDate())) {
            return new VoucherAvailability(
                    false,
                    false,
                    "Voucher không hợp lệ hoặc đã hết hạn",
                    BigDecimal.ZERO,
                    usageCount,
                    remainingUsageCount);
        }

        if (Objects.requireNonNullElse(voucher.getQuantity(), 0) <= 0) {
            return new VoucherAvailability(
                    false,
                    false,
                    "Voucher này đã hết lượt sử dụng trên hệ thống",
                    BigDecimal.ZERO,
                    usageCount,
                    remainingUsageCount);
        }

        if (usageCount >= maxUsagePerUser) {
            return new VoucherAvailability(
                    false,
                    false,
                    "Bạn đã sử dụng tối đa số lượt cho phép của voucher này",
                    BigDecimal.ZERO,
                    usageCount,
                    remainingUsageCount);
        }

        BigDecimal minOrderValue = voucher.getMinOrderValue() != null ? voucher.getMinOrderValue() : BigDecimal.ZERO;
        if (orderSubtotal != null && orderSubtotal.compareTo(minOrderValue) < 0) {
            return new VoucherAvailability(
                    true,
                    false,
                    "Đơn hàng chưa đạt giá trị tối thiểu để sử dụng voucher này",
                    BigDecimal.ZERO,
                    usageCount,
                    remainingUsageCount);
        }

        return new VoucherAvailability(
                true,
                true,
                null,
                orderSubtotal != null ? calculateDiscountAmount(voucher, orderSubtotal) : BigDecimal.ZERO,
                usageCount,
                remainingUsageCount);
    }

    private UserVoucherResponse buildUserVoucherResponse(
            Voucher voucher,
            UserVoucher userVoucher,
            BigDecimal orderSubtotal,
            LocalDateTime now) {
        VoucherAvailability availability = evaluateVoucherForUser(voucher, userVoucher, orderSubtotal, now);

        return UserVoucherResponse.builder()
                .id(voucher.getId())
                .code(voucher.getCode())
                .title(voucher.getTitle())
                .discountType(voucher.getDiscountType())
                .value(voucher.getValue())
                .maxDiscount(voucher.getMaxDiscount())
                .maxUsagePerUser(voucher.getMaxUsagePerUser())
                .minOrderValue(voucher.getMinOrderValue())
                .startDate(voucher.getStartDate())
                .endDate(voucher.getEndDate())
                .quantity(voucher.getQuantity())
                .status(deriveVoucherStatus(voucher))
                .saved(Boolean.TRUE.equals(userVoucher != null ? userVoucher.getIsSaved() : null))
                .usageCount(availability.usageCount())
                .remainingUsageCount(availability.remainingUsageCount())
                .canApply(availability.canApply())
                .availabilityReason(availability.reason())
                .previewDiscountAmount(availability.previewDiscountAmount())
                .build();
    }

    private RuntimeException voucherValidationException(boolean conflictOnUnavailable, String message) {
        return conflictOnUnavailable ? new ConflictException(message) : new BadRequestException(message);
    }
}
