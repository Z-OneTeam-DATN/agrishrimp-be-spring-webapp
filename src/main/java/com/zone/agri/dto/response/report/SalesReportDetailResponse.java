package com.zone.agri.dto.response.report;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SalesReportDetailResponse {
    private String type;
    private String label;
    private String description;
    private List<Column> columns;
    private List<Map<String, Object>> rows;
    private Map<String, Object> totals;
    private int totalRows;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Column {
        private String key;
        private String label;
        private String align;
    }
}
