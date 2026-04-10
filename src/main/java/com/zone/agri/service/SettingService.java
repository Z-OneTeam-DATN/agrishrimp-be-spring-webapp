package com.zone.agri.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.stereotype.Service;

import com.zone.agri.entity.SystemSetting;
import com.zone.agri.exception.BadRequestException;
import com.zone.agri.repository.SystemSettingRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SettingService {
    private final SystemSettingRepository settingRepo;
    private static final String PROFIT_MARGIN_KEY = "PROFIT_MARGIN";
    private static final String PRICE_ROUNDING_RULE_KEY = "PRICE_ROUNDING_RULE";
    private static final String DEFAULT_PROFIT_MARGIN = "30";
    private static final String DEFAULT_ROUNDING_RULE = "NONE";

    // Hàm VIP: Tự động quy đổi % thành hệ số nhân (VD: 30% -> trả về 1.3)
    public BigDecimal getProfitMultiplier() {
        String val = settingRepo.findById(PROFIT_MARGIN_KEY)
                .map(SystemSetting::getSettingValue)
                .orElse(DEFAULT_PROFIT_MARGIN); // Mặc định 30% nếu Admin chưa cài đặt
        BigDecimal margin = parseAndValidateMargin(val, false);
        return BigDecimal.ONE.add(margin.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));
    }

    // Lấy con số nguyên gốc (VD: "30") để Frontend hiển thị vào ô Input
    public String getProfitMarginRaw() {
        return settingRepo.findById(PROFIT_MARGIN_KEY)
                .map(SystemSetting::getSettingValue)
                .orElse(DEFAULT_PROFIT_MARGIN);
    }

    public String getProfitRoundingRuleRaw() {
        return settingRepo.findById(PRICE_ROUNDING_RULE_KEY)
                .map(SystemSetting::getSettingValue)
                .map(this::sanitizeRoundingRule)
                .orElse(DEFAULT_ROUNDING_RULE);
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
    }

    public BigDecimal calculateSellingPrice(BigDecimal importPrice) {
        return calculateSellingPrice(importPrice, getProfitMultiplier(), getProfitRoundingRuleRaw());
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

    private String sanitizeRoundingRule(String roundingRule) {
        if (roundingRule == null)
            return DEFAULT_ROUNDING_RULE;
        String normalized = roundingRule.trim().toUpperCase();

        return switch (normalized) {
            case "STEP_500", "STEP_1000", "TAIL_99000", "NONE" -> normalized;
            default -> DEFAULT_ROUNDING_RULE;
        };
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
}