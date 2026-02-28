package com.zone.agri.service;

import com.zone.agri.entity.SystemSetting;
import com.zone.agri.repository.SystemSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class SettingService {
    private final SystemSettingRepository settingRepo;

    // Hàm VIP: Tự động quy đổi % thành hệ số nhân (VD: 30% -> trả về 1.3)
    public BigDecimal getProfitMultiplier() {
        String val = settingRepo.findById("PROFIT_MARGIN")
                .map(SystemSetting::getSettingValue)
                .orElse("30"); // Mặc định 30% nếu Admin chưa cài đặt
        double margin = Double.parseDouble(val);
        return BigDecimal.valueOf(1 + (margin / 100.0));
    }

    // Lấy con số nguyên gốc (VD: "30") để Frontend hiển thị vào ô Input
    public String getProfitMarginRaw() {
        return settingRepo.findById("PROFIT_MARGIN")
                .map(SystemSetting::getSettingValue)
                .orElse("30");
    }

    public void updateProfitMargin(String newMargin) {
        SystemSetting setting = settingRepo.findById("PROFIT_MARGIN")
                .orElse(SystemSetting.builder()
                        .settingKey("PROFIT_MARGIN")
                        .description("Phần trăm lợi nhuận cộng thêm vào giá vốn")
                        .build());
        setting.setSettingValue(newMargin);
        settingRepo.save(setting);
    }
}