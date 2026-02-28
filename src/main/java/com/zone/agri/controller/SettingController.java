package com.zone.agri.controller;

import com.zone.agri.service.SettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@CrossOrigin(origins = "http://localhost:3000")
@RestController
@RequestMapping("/api/admin/settings")
@RequiredArgsConstructor
public class SettingController {

    private final SettingService settingService;

    @GetMapping("/profit-margin")
    public ResponseEntity<?> getProfitMargin() {
        return ResponseEntity.ok(Map.of("margin", settingService.getProfitMarginRaw()));
    }

    @PutMapping("/profit-margin")
    public ResponseEntity<?> updateProfitMargin(@RequestBody Map<String, String> request) {
        String margin = request.get("margin");
        settingService.updateProfitMargin(margin);
        return ResponseEntity.ok(Map.of("success", true, "message", "Đã cập nhật tỷ lệ lợi nhuận thành " + margin + "%"));
    }
}