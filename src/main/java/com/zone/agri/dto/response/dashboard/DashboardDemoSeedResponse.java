package com.zone.agri.dto.response.dashboard;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardDemoSeedResponse {
    private String message;
    private String mode;
    private String prefix;
    private LocalDateTime generatedAt;
    private Map<String, Object> deletedRecords;
    private Map<String, Object> createdRecords;
    private Map<String, Object> demoAccounts;
    private Map<String, Object> expectedDashboardNumbers;
    private List<Map<String, Object>> branches;
    private List<Map<String, Object>> salesPerformance7Days;
    private List<Map<String, Object>> categoryDistribution;
    private List<Map<String, Object>> topProducts;
    private List<Map<String, Object>> orders;
    private List<String> notes;
}
