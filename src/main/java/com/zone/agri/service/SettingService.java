package com.zone.agri.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.HashMap;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;

import org.springframework.stereotype.Service;

import com.zone.agri.entity.SystemSetting;
import com.zone.agri.exception.BadRequestException;
import com.zone.agri.repository.SystemSettingRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SettingService {
    private final SystemSettingRepository settingRepo;

    // In-memory cache to prevent N+1 DB query roundtrips over remote tunnel
    private volatile Boolean cacheMultiTierEnabled = null;
    private volatile BigDecimal cacheMinMarginFloor = null;
    private volatile Map<Long, BigDecimal> cacheCategoryMarginOffsets = null;
    private volatile Map<Integer, BigDecimal> cacheExpiryDiscountTiers = null;
    private volatile String cacheProfitMarginRaw = null;
    private volatile BigDecimal cacheProfitMultiplier = null;
    private volatile String cacheProfitRoundingRuleRaw = null;

    private synchronized void clearCache() {
        cacheMultiTierEnabled = null;
        cacheMinMarginFloor = null;
        cacheCategoryMarginOffsets = null;
        cacheExpiryDiscountTiers = null;
        cacheProfitMarginRaw = null;
        cacheProfitMultiplier = null;
        cacheProfitRoundingRuleRaw = null;
    }

    private static final String PROFIT_MARGIN_KEY = "PROFIT_MARGIN";
    private static final String PRICE_ROUNDING_RULE_KEY = "PRICE_ROUNDING_RULE";
    private static final String DEFAULT_PROFIT_MARGIN = "30";
    private static final String DEFAULT_ROUNDING_RULE = "NONE";

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final String MULTI_TIER_PRICING_ENABLED_KEY = "MULTI_TIER_PRICING_ENABLED";
    private static final String MIN_MARGIN_FLOOR_KEY = "MIN_MARGIN_FLOOR";
    private static final String CATEGORY_MARGIN_OFFSETS_KEY = "CATEGORY_MARGIN_OFFSETS";
    private static final String EXPIRY_DISCOUNT_TIERS_KEY = "EXPIRY_DISCOUNT_TIERS";

    private static final String DEFAULT_MIN_MARGIN_FLOOR = "3.0";
    private static final String DEFAULT_JSON_EMPTY = "{}";

    // Hàm VIP: Tự động quy đổi % thành hệ số nhân (VD: 30% -> trả về 1.3)
    public BigDecimal getProfitMultiplier() {
        BigDecimal cached = cacheProfitMultiplier;
        if (cached == null) {
            String val = settingRepo.findById(PROFIT_MARGIN_KEY)
                    .map(SystemSetting::getSettingValue)
                    .orElse(DEFAULT_PROFIT_MARGIN);
            BigDecimal margin = parseAndValidateMargin(val, false);
            cached = BigDecimal.ONE.add(margin.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));
            cacheProfitMultiplier = cached;
        }
        return cached;
    }

    // Lấy con số nguyên gốc (VD: "30") để Frontend hiển thị vào ô Input
    public String getProfitMarginRaw() {
        String cached = cacheProfitMarginRaw;
        if (cached == null) {
            cached = settingRepo.findById(PROFIT_MARGIN_KEY)
                    .map(SystemSetting::getSettingValue)
                    .orElse(DEFAULT_PROFIT_MARGIN);
            cacheProfitMarginRaw = cached;
        }
        return cached;
    }

    public String getProfitRoundingRuleRaw() {
        String cached = cacheProfitRoundingRuleRaw;
        if (cached == null) {
            cached = settingRepo.findById(PRICE_ROUNDING_RULE_KEY)
                    .map(SystemSetting::getSettingValue)
                    .map(this::sanitizeRoundingRule)
                    .orElse(DEFAULT_ROUNDING_RULE);
            cacheProfitRoundingRuleRaw = cached;
        }
        return cached;
    }

    public void updateProfitMargin(String newMargin) {
        String validatedMargin = parseAndValidateMarginValue(newMargin);
        SystemSetting setting = settingRepo.findById(PROFIT_MARGIN_KEY)
                .orElse(SystemSetting.builder()
                        .settingKey(PROFIT_MARGIN_KEY)
                        .description("Phần trăm lợi nhuận cộng thêm vào giá vốn")
                        .build());
        setting.setSettingValue(validatedMargin);
        settingRepo.save(setting);
        clearCache();
    }

    public void updateProfitConfig(String newMargin, String roundingRule) {
        updateProfitMargin(newMargin);

        SystemSetting roundingSetting = settingRepo.findById(PRICE_ROUNDING_RULE_KEY)
                .orElse(SystemSetting.builder()
                        .settingKey(PRICE_ROUNDING_RULE_KEY)
                        .description("Quy tắc làm tròn giá bán")
                        .build());
        roundingSetting.setSettingValue(sanitizeRoundingRule(roundingRule));
        settingRepo.save(roundingSetting);
        clearCache();
    }

    public boolean isMultiTierPricingEnabled() {
        Boolean cached = cacheMultiTierEnabled;
        if (cached == null) {
            cached = settingRepo.findById(MULTI_TIER_PRICING_ENABLED_KEY)
                    .map(SystemSetting::getSettingValue)
                    .map(String::trim)
                    .map(Boolean::parseBoolean)
                    .orElse(false);
            cacheMultiTierEnabled = cached;
        }
        return cached;
    }

    public BigDecimal getMinMarginFloor() {
        BigDecimal cached = cacheMinMarginFloor;
        if (cached == null) {
            String val = settingRepo.findById(MIN_MARGIN_FLOOR_KEY)
                    .map(SystemSetting::getSettingValue)
                    .orElse(DEFAULT_MIN_MARGIN_FLOOR);
            try {
                cached = new BigDecimal(val.trim());
            } catch (Exception e) {
                cached = new BigDecimal(DEFAULT_MIN_MARGIN_FLOOR);
            }
            cacheMinMarginFloor = cached;
        }
        return cached;
    }

    public Map<Long, BigDecimal> getCategoryMarginOffsets() {
        Map<Long, BigDecimal> cached = cacheCategoryMarginOffsets;
        if (cached == null) {
            String val = settingRepo.findById(CATEGORY_MARGIN_OFFSETS_KEY)
                    .map(SystemSetting::getSettingValue)
                    .orElse(DEFAULT_JSON_EMPTY);
            try {
                cached = objectMapper.readValue(val, new TypeReference<Map<Long, BigDecimal>>() {});
            } catch (Exception e) {
                cached = new HashMap<>();
            }
            cacheCategoryMarginOffsets = cached;
        }
        return cached;
    }

    public Map<Integer, BigDecimal> getExpiryDiscountTiers() {
        Map<Integer, BigDecimal> cached = cacheExpiryDiscountTiers;
        if (cached == null) {
            String val = settingRepo.findById(EXPIRY_DISCOUNT_TIERS_KEY)
                    .map(SystemSetting::getSettingValue)
                    .orElse(DEFAULT_JSON_EMPTY);
            try {
                cached = objectMapper.readValue(val, new TypeReference<Map<Integer, BigDecimal>>() {});
            } catch (Exception e) {
                cached = new HashMap<>();
            }
            cacheExpiryDiscountTiers = cached;
        }
        return cached;
    }

    public BigDecimal calculateSellingPrice(BigDecimal importPrice) {
        return calculateSellingPrice(importPrice, (Long) null, (java.time.LocalDateTime) null);
    }

    public BigDecimal calculateSellingPrice(BigDecimal importPrice, Long categoryId, java.time.LocalDateTime expiryDate) {
        BigDecimal safeImportPrice = importPrice != null ? importPrice : BigDecimal.ZERO;
        BigDecimal baseMultiplier = getProfitMultiplier();
        String roundingRule = getProfitRoundingRuleRaw();

        // Fallback: Nếu tắt tính năng đa tầng, hoặc không truyền tham số, dùng công thức global cũ
        if (!isMultiTierPricingEnabled() || (categoryId == null && expiryDate == null)) {
            BigDecimal basePrice = safeImportPrice.multiply(baseMultiplier);
            return applyRounding(basePrice, sanitizeRoundingRule(roundingRule));
        }

        BigDecimal baseMarginValue = parseAndValidateMargin(getProfitMarginRaw(), false);
        BigDecimal totalMargin = baseMarginValue;

        // Cộng trừ theo Danh mục (Category Offset)
        if (categoryId != null) {
            Map<Long, BigDecimal> offsets = getCategoryMarginOffsets();
            BigDecimal offset = offsets.get(categoryId);
            if (offset != null) {
                totalMargin = totalMargin.add(offset);
            }
        }

        // Khấu trừ cận date (Expiry Discount)
        if (expiryDate != null) {
            java.time.Duration duration = java.time.Duration.between(java.time.LocalDateTime.now(), expiryDate);
            long daysRemaining = duration.toDays();
            if (daysRemaining > 0) {
                Map<Integer, BigDecimal> tiers = getExpiryDiscountTiers();
                BigDecimal matchedDiscount = BigDecimal.ZERO;
                int bestThreshold = Integer.MAX_VALUE;
                for (Map.Entry<Integer, BigDecimal> entry : tiers.entrySet()) {
                    int threshold = entry.getKey();
                    if (daysRemaining <= threshold && threshold < bestThreshold) {
                        bestThreshold = threshold;
                        matchedDiscount = entry.getValue();
                    }
                }
                totalMargin = totalMargin.subtract(matchedDiscount);
            } else {
                Map<Integer, BigDecimal> tiers = getExpiryDiscountTiers();
                BigDecimal maxDiscount = BigDecimal.ZERO;
                for (BigDecimal discount : tiers.values()) {
                    if (discount.compareTo(maxDiscount) > 0) {
                        maxDiscount = discount;
                    }
                }
                totalMargin = totalMargin.subtract(maxDiscount);
            }
        }

        // Chặn sàn tối thiểu (ghim theo cấu hình DB, fallback 3.0%)
        BigDecimal floorMargin = getMinMarginFloor();
        if (totalMargin.compareTo(floorMargin) < 0) {
            totalMargin = floorMargin;
        }

        // Tính toán hệ số nhân margin thực tế cuối cùng
        BigDecimal multiplier = BigDecimal.ONE.add(totalMargin.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));
        BigDecimal finalBasePrice = safeImportPrice.multiply(multiplier);

        // Làm tròn ở bước cuối cùng
        return applyRounding(finalBasePrice, sanitizeRoundingRule(roundingRule));
    }

    public BigDecimal calculateSellingPrice(BigDecimal importPrice, BigDecimal profitMultiplier, String roundingRule) {
        BigDecimal safeImportPrice = importPrice != null ? importPrice : BigDecimal.ZERO;
        BigDecimal safeMultiplier = profitMultiplier != null ? profitMultiplier : getProfitMultiplier();
        BigDecimal basePrice = safeImportPrice.multiply(safeMultiplier);
        return applyRounding(basePrice, sanitizeRoundingRule(roundingRule));
    }

    private BigDecimal applyRounding(BigDecimal amount, String roundingRule) {
        long roundedAmount = amount.max(BigDecimal.ZERO).setScale(0, RoundingMode.HALF_UP).longValue();

        return switch (roundingRule) {
            case "STEP_500" -> BigDecimal.valueOf(((roundedAmount + 499) / 500) * 500);
            case "STEP_1000" -> BigDecimal.valueOf(((roundedAmount + 999) / 1000) * 1000);
            case "TAIL_99000" -> {
                if (roundedAmount <= 99000) {
                    yield BigDecimal.valueOf(99000);
                }
                long band = Math.round((roundedAmount - 99000) / 100000.0);
                yield BigDecimal.valueOf(99000L + (band * 100000L));
            }
            default -> BigDecimal.valueOf(roundedAmount);
        };
    }

    public boolean isMarginCapped(Long categoryId, java.time.LocalDateTime expiryDate) {
        if (!isMultiTierPricingEnabled() || (categoryId == null && expiryDate == null)) {
            return false;
        }

        BigDecimal baseMarginValue = parseAndValidateMargin(getProfitMarginRaw(), false);
        BigDecimal totalMargin = baseMarginValue;

        if (categoryId != null) {
            Map<Long, BigDecimal> offsets = getCategoryMarginOffsets();
            BigDecimal offset = offsets.get(categoryId);
            if (offset != null) {
                totalMargin = totalMargin.add(offset);
            }
        }

        if (expiryDate != null) {
            java.time.Duration duration = java.time.Duration.between(java.time.LocalDateTime.now(), expiryDate);
            long daysRemaining = duration.toDays();
            if (daysRemaining > 0) {
                Map<Integer, BigDecimal> tiers = getExpiryDiscountTiers();
                BigDecimal matchedDiscount = BigDecimal.ZERO;
                int bestThreshold = Integer.MAX_VALUE;
                for (Map.Entry<Integer, BigDecimal> entry : tiers.entrySet()) {
                    int threshold = entry.getKey();
                    if (daysRemaining <= threshold && threshold < bestThreshold) {
                        bestThreshold = threshold;
                        matchedDiscount = entry.getValue();
                    }
                }
                totalMargin = totalMargin.subtract(matchedDiscount);
            } else {
                Map<Integer, BigDecimal> tiers = getExpiryDiscountTiers();
                BigDecimal maxDiscount = BigDecimal.ZERO;
                for (BigDecimal discount : tiers.values()) {
                    if (discount.compareTo(maxDiscount) > 0) {
                        maxDiscount = discount;
                    }
                }
                totalMargin = totalMargin.subtract(maxDiscount);
            }
        }

        BigDecimal floorMargin = getMinMarginFloor();
        return totalMargin.compareTo(floorMargin) < 0;
    }

    private String sanitizeRoundingRule(String roundingRule) {
        if (roundingRule == null)
            return DEFAULT_ROUNDING_RULE;
        String normalized = roundingRule.trim().toUpperCase();
        return switch (normalized) {
            case "STEP_500", "STEP_1000", "TAIL_99000", "NONE" -> normalized;
            default -> DEFAULT_ROUNDING_RULE;
        };
    }

    public void updateMultiTierPricingEnabled(boolean enabled) {
        SystemSetting setting = settingRepo.findById(MULTI_TIER_PRICING_ENABLED_KEY)
                .orElse(SystemSetting.builder()
                        .settingKey(MULTI_TIER_PRICING_ENABLED_KEY)
                        .description("Bật/Tắt định giá đa tầng (danh mục & cận date)")
                        .build());
        setting.setSettingValue(String.valueOf(enabled));
        settingRepo.save(setting);
        clearCache();
    }

    public void updateMinMarginFloor(String floor) {
        SystemSetting setting = settingRepo.findById(MIN_MARGIN_FLOOR_KEY)
                .orElse(SystemSetting.builder()
                        .settingKey(MIN_MARGIN_FLOOR_KEY)
                        .description("Biên độ lợi nhuận sàn tối thiểu")
                        .build());
        try {
            new BigDecimal(floor.trim());
            setting.setSettingValue(floor.trim());
            settingRepo.save(setting);
            clearCache();
        } catch (Exception e) {
            throw new BadRequestException("Biên sàn không hợp lệ: " + floor);
        }
    }

    public void updateCategoryMarginOffsets(Map<Long, BigDecimal> offsets) {
        SystemSetting setting = settingRepo.findById(CATEGORY_MARGIN_OFFSETS_KEY)
                .orElse(SystemSetting.builder()
                        .settingKey(CATEGORY_MARGIN_OFFSETS_KEY)
                        .description("Biên lợi nhuận offset theo danh mục sản phẩm (JSON)")
                        .build());
        try {
            String jsonVal = objectMapper.writeValueAsString(offsets != null ? offsets : new HashMap<>());
            setting.setSettingValue(jsonVal);
            settingRepo.save(setting);
            clearCache();
        } catch (Exception e) {
            throw new BadRequestException("Cấu hình offset danh mục lỗi: " + e.getMessage());
        }
    }

    public void updateExpiryDiscountTiers(Map<Integer, BigDecimal> tiers) {
        SystemSetting setting = settingRepo.findById(EXPIRY_DISCOUNT_TIERS_KEY)
                .orElse(SystemSetting.builder()
                        .settingKey(EXPIRY_DISCOUNT_TIERS_KEY)
                        .description("Giảm giá cận date theo số ngày còn lại (JSON)")
                        .build());
        try {
            String jsonVal = objectMapper.writeValueAsString(tiers != null ? tiers : new HashMap<>());
            setting.setSettingValue(jsonVal);
            settingRepo.save(setting);
            clearCache();
        } catch (Exception e) {
            throw new BadRequestException("Cấu hình discount cận date lỗi: " + e.getMessage());
        }
    }

    private String parseAndValidateMarginValue(String rawMargin) {
        return parseAndValidateMargin(rawMargin, true).stripTrailingZeros().toPlainString();
    }

    private BigDecimal parseAndValidateMargin(String rawMargin, boolean throwOnError) {
        String normalized = rawMargin == null ? "" : rawMargin.trim().replace(',', '.');
        try {
            BigDecimal margin = new BigDecimal(normalized);
            if (margin.compareTo(BigDecimal.ZERO) < 0 || margin.compareTo(BigDecimal.valueOf(100)) > 0) {
                throw new BadRequestException("Biên lợi nhuận phải nằm trong khoảng 0% đến 100%.");
            }
            return margin;
        } catch (NumberFormatException | ArithmeticException ex) {
            if (throwOnError) {
                throw new BadRequestException("Biên lợi nhuận không hợp lệ.");
            }
            return new BigDecimal(DEFAULT_PROFIT_MARGIN);
        }
    }

    public int getCashflowRiskWindowDays() {
        return settingRepo.findById("CASHFLOW_RISK_WINDOW_DAYS")
                .map(SystemSetting::getSettingValue)
                .map(String::trim)
                .map(Integer::parseInt)
                .orElse(14);
    }

    public BigDecimal getCashflowCriticalThresholdPercent() {
        return settingRepo.findById("CASHFLOW_CRITICAL_THRESHOLD_PERCENT")
                .map(SystemSetting::getSettingValue)
                .map(String::trim)
                .map(BigDecimal::new)
                .orElse(BigDecimal.valueOf(20.0));
    }

    public BigDecimal getCashflowWeightTime() {
        return settingRepo.findById("CASHFLOW_WEIGHT_TIME")
                .map(SystemSetting::getSettingValue)
                .map(String::trim)
                .map(BigDecimal::new)
                .orElse(BigDecimal.valueOf(0.5));
    }

    public BigDecimal getCashflowWeightFrequency() {
        return settingRepo.findById("CASHFLOW_WEIGHT_FREQUENCY")
                .map(SystemSetting::getSettingValue)
                .map(String::trim)
                .map(BigDecimal::new)
                .orElse(BigDecimal.valueOf(0.3));
    }

    public BigDecimal getCashflowWeightValue() {
        return settingRepo.findById("CASHFLOW_WEIGHT_VALUE")
                .map(SystemSetting::getSettingValue)
                .map(String::trim)
                .map(BigDecimal::new)
                .orElse(BigDecimal.valueOf(0.2));
    }

    public int getSupplierDebtDefaultTermDays() {
        return settingRepo.findById("SUPPLIER_DEBT_DEFAULT_TERM_DAYS")
                .map(SystemSetting::getSettingValue)
                .map(String::trim)
                .map(Integer::parseInt)
                .orElse(30);
    }

    public int getDebtAgeWarningDays() {
        return settingRepo.findById("DEBT_AGE_WARNING_DAYS")
                .map(SystemSetting::getSettingValue)
                .map(String::trim)
                .map(Integer::parseInt)
                .orElse(45);
    }

    public int getDebtAgeCriticalDays() {
        return settingRepo.findById("DEBT_AGE_CRITICAL_DAYS")
                .map(SystemSetting::getSettingValue)
                .map(String::trim)
                .map(Integer::parseInt)
                .orElse(90);
    }

    public BigDecimal getDebtWeightAge() {
        return settingRepo.findById("DEBT_WEIGHT_AGE")
                .map(SystemSetting::getSettingValue)
                .map(String::trim)
                .map(BigDecimal::new)
                .orElse(BigDecimal.valueOf(0.5));
    }

    public BigDecimal getDebtWeightValue() {
        return settingRepo.findById("DEBT_WEIGHT_VALUE")
                .map(SystemSetting::getSettingValue)
                .map(String::trim)
                .map(BigDecimal::new)
                .orElse(BigDecimal.valueOf(0.5));
    }

    public BigDecimal getPLCOGSWarningThreshold() {
        return settingRepo.findById("PL_COGS_RATIO_WARNING_THRESHOLD")
                .map(SystemSetting::getSettingValue)
                .map(String::trim)
                .map(BigDecimal::new)
                .orElse(BigDecimal.valueOf(75.0));
    }

    public BigDecimal getPLReturnWarningThreshold() {
        return settingRepo.findById("PL_RETURN_RATIO_WARNING_THRESHOLD")
                .map(SystemSetting::getSettingValue)
                .map(String::trim)
                .map(BigDecimal::new)
                .orElse(BigDecimal.valueOf(10.0));
    }
}