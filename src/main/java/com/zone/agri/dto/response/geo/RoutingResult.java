package com.zone.agri.dto.response.geo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Kết quả gọi Distance Matrix API (ORS).
 * durations[i] = thời gian từ origin tới destination i (giây)
 * distances[i] = khoảng cách từ origin tới destination i (mét)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RoutingResult {
    private List<Double> durations; // giây
    private List<Double> distances; // mét
}
