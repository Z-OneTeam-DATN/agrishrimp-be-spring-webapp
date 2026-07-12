package com.zone.agri.controller;

import com.zone.agri.service.SettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/settings")
@RequiredArgsConstructor
public class SettingController {

    private final SettingService settingService;

    @GetMapping("/profit-margin")
    public ResponseEntity<?> getProfitMargin() {
        return ResponseEntity.ok(Map.of(
                "margin", settingService.getProfitMarginRaw(),
                "roundingRule", settingService.getProfitRoundingRuleRaw(),
                "multiTierEnabled", settingService.isMultiTierPricingEnabled(),
                "minMarginFloor", settingService.getMinMarginFloor().stripTrailingZeros().toPlainString(),
                "categoryOffsets", settingService.getCategoryMarginOffsets(),
                "expiryTiers", settingService.getExpiryDiscountTiers()
        ));
    }

    @PutMapping("/profit-margin")
    public ResponseEntity<?> updateProfitMargin(@RequestBody Map<String, Object> request) {
        String margin = String.valueOf(request.getOrDefault("margin", settingService.getProfitMarginRaw()));
        String roundingRule = String.valueOf(request.getOrDefault("roundingRule", settingService.getProfitRoundingRuleRaw()));

        settingService.updateProfitConfig(margin, roundingRule);

        if (request.containsKey("multiTierEnabled")) {
            boolean enabled = Boolean.parseBoolean(String.valueOf(request.get("multiTierEnabled")));
            settingService.updateMultiTierPricingEnabled(enabled);
        }

        if (request.containsKey("minMarginFloor")) {
            String floor = String.valueOf(request.get("minMarginFloor"));
            settingService.updateMinMarginFloor(floor);
        }

        if (request.containsKey("categoryOffsets")) {
            try {
                Map<?, ?> rawOffsets = (Map<?, ?>) request.get("categoryOffsets");
                Map<Long, java.math.BigDecimal> offsets = new java.util.HashMap<>();
                if (rawOffsets != null) {
                    for (Map.Entry<?, ?> entry : rawOffsets.entrySet()) {
                        Long catId = Long.valueOf(String.valueOf(entry.getKey()));
                        java.math.BigDecimal offsetVal = new java.math.BigDecimal(String.valueOf(entry.getValue()));
                        offsets.put(catId, offsetVal);
                    }
                }
                settingService.updateCategoryMarginOffsets(offsets);
            } catch (Exception e) {
                // Log warning and ignore
            }
        }

        if (request.containsKey("expiryTiers")) {
            try {
                Map<?, ?> rawTiers = (Map<?, ?>) request.get("expiryTiers");
                Map<Integer, java.math.BigDecimal> tiers = new java.util.HashMap<>();
                if (rawTiers != null) {
                    for (Map.Entry<?, ?> entry : rawTiers.entrySet()) {
                        Integer days = Integer.valueOf(String.valueOf(entry.getKey()));
                        java.math.BigDecimal discountVal = new java.math.BigDecimal(String.valueOf(entry.getValue()));
                        tiers.put(days, discountVal);
                    }
                }
                settingService.updateExpiryDiscountTiers(tiers);
            } catch (Exception e) {
                // Log warning and ignore
            }
        }

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Đã cập nhật cấu hình giá bán thành công.",
                "roundingRule", settingService.getProfitRoundingRuleRaw()));
    }
}
