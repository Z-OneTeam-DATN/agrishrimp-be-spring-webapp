package com.zone.agri.dto.response.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BusinessTrendResponse {

    private String granularity;

    private String rangeLabel;

    private List<Point> points;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Point {

        private String period;

        private String label;

        private BigDecimal revenue;
        private BigDecimal cost;
        private BigDecimal profit;
        private long orders;
    }
}

