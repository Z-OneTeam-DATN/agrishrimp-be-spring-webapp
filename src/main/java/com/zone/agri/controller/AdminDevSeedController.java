package com.zone.agri.controller;

import com.zone.agri.dto.response.dashboard.DashboardDemoSeedResponse;
import com.zone.agri.security.annotation.RequirePermission;
import com.zone.agri.service.DashboardDemoDataSeederService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/dev")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.dev-tools.enabled", havingValue = "true")
@Tag(name = "Admin Dev Tools", description = "Dev-only endpoints for local dashboard verification")
public class AdminDevSeedController {

    private final DashboardDemoDataSeederService dashboardDemoDataSeederService;

    @Operation(summary = "Seed demo data for Admin Dashboard")
    @RequirePermission("DASHBOARD_VIEW")
    @PostMapping("/seed-dashboard-demo-data")
    public ResponseEntity<DashboardDemoSeedResponse> seedDashboardDemoData() {
        return ResponseEntity.ok(dashboardDemoDataSeederService.seed());
    }
}
