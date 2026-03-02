package com.zone.agri.service;

import com.zone.agri.dto.geo.CoordinateDto;
import com.zone.agri.dto.geo.RoutingResult;
import com.zone.agri.entity.Branch;
import com.zone.agri.entity.enums.BranchStatus;
import com.zone.agri.repository.BranchRepository;
import com.zone.agri.utils.HaversineUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Tìm chi nhánh theo thứ tự gần nhất:
 * <ol>
 *   <li>Lấy TẤT CẢ chi nhánh ACTIVE</li>
 *   <li>Haversine — sort theo khoảng cách tăng dần (không giới hạn)</li>
 *   <li>Distance Matrix API — enrich top N gần nhất bằng thời gian thực;
 *       các chi nhánh còn lại dùng Haversine estimate</li>
 * </ol>
 * Allocation sẽ thử lần lượt từng chi nhánh — gần trước, xa sau.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BranchSearchService {

    private final BranchRepository branchRepository;
    private final OpenRouteServiceProvider routingProvider;

    /** Số chi nhánh gần nhất được gọi ORS để lấy thời gian thực (tiết kiệm API quota). */
    @Value("${location.max-candidates:5}")
    private int orsTopN;

    // ──────────────────────────────────────────────────────────────
    // Internal records
    // ──────────────────────────────────────────────────────────────

    public record BranchWithDistance(Branch branch, double distanceKm) {}

    public record BranchWithRealDistance(
            Branch branch,
            double distanceKm,
            double durationSeconds,
            double durationMinutes
    ) {}

    // ──────────────────────────────────────────────────────────────
    // Public API
    // ──────────────────────────────────────────────────────────────

    /**
     * Trả về TẤT CẢ chi nhánh ACTIVE, sorted theo khoảng cách tăng dần.
     * ORS chỉ được gọi cho top {@code orsTopN} gần nhất; các chi nhánh còn lại
     * dùng Haversine estimate để tránh tốn API quota.
     */
    public List<BranchWithRealDistance> findNearestBranches(double userLat, double userLng) {
        List<Branch> allActive = branchRepository.findByStatus(BranchStatus.ACTIVE);

        if (allActive.isEmpty()) {
            log.error("Hệ thống không có chi nhánh nào đang hoạt động!");
            return List.of();
        }

        // Haversine sort toàn bộ (không giới hạn)
        List<BranchWithDistance> sorted = sortByHaversine(userLat, userLng, allActive);

        // ORS enrich top N, Haversine fallback cho phần còn lại
        return enrichWithRealDistance(userLat, userLng, sorted);
    }

    // ──────────────────────────────────────────────────────────────
    // Haversine sort — toàn bộ, không giới hạn
    // ──────────────────────────────────────────────────────────────

    List<BranchWithDistance> sortByHaversine(double userLat, double userLng, List<Branch> candidates) {
        return candidates.stream()
                .map(b -> {
                    double dist = (b.getLat() != null && b.getLng() != null)
                            ? HaversineUtils.distanceKm(userLat, userLng, b.getLat(), b.getLng())
                            : Double.MAX_VALUE;
                    return new BranchWithDistance(b, dist);
                })
                .sorted(Comparator.comparingDouble(BranchWithDistance::distanceKm))
                .toList();
    }

    // ──────────────────────────────────────────────────────────────
    // ORS enrich — top N dùng Distance Matrix API, phần còn lại Haversine
    // ──────────────────────────────────────────────────────────────

    List<BranchWithRealDistance> enrichWithRealDistance(double userLat, double userLng,
                                                        List<BranchWithDistance> all) {
        List<BranchWithDistance> withCoords = all.stream()
                .filter(bwd -> bwd.branch().getLat() != null && bwd.branch().getLng() != null)
                .toList();
        List<BranchWithDistance> withoutCoords = all.stream()
                .filter(bwd -> bwd.branch().getLat() == null || bwd.branch().getLng() == null)
                .toList();

        List<BranchWithRealDistance> result = new ArrayList<>();

        if (!withCoords.isEmpty()) {
            // Chỉ gọi ORS cho orsTopN gần nhất để tiết kiệm API quota
            List<BranchWithDistance> orsGroup      = withCoords.stream().limit(orsTopN).toList();
            List<BranchWithDistance> haversineGroup = withCoords.stream().skip(orsTopN).toList();

            List<Double> durations = null;
            try {
                CoordinateDto origin = new CoordinateDto(userLat, userLng);
                List<CoordinateDto> destinations = orsGroup.stream()
                        .map(bwd -> new CoordinateDto(bwd.branch().getLat(), bwd.branch().getLng()))
                        .toList();
                durations = routingProvider.getDistanceMatrix(origin, destinations).getDurations();
            } catch (Exception e) {
                log.warn("ORS distance matrix thất bại, dùng Haversine fallback: {}", e.getMessage());
            }

            for (int i = 0; i < orsGroup.size(); i++) {
                BranchWithDistance bwd = orsGroup.get(i);
                double durationSec = (durations != null && i < durations.size() && durations.get(i) >= 0)
                        ? durations.get(i)
                        : bwd.distanceKm() * 120;
                result.add(new BranchWithRealDistance(bwd.branch(), bwd.distanceKm(), durationSec, durationSec / 60.0));
            }

            // Chi nhánh xa hơn orsTopN — Haversine estimate (120s/km)
            for (BranchWithDistance bwd : haversineGroup) {
                double durationSec = bwd.distanceKm() * 120;
                result.add(new BranchWithRealDistance(bwd.branch(), bwd.distanceKm(), durationSec, durationSec / 60.0));
            }
        }

        // Branches chưa geocode — sort về cuối
        for (BranchWithDistance bwd : withoutCoords) {
            log.debug("Branch '{}' (id={}) chưa geocode, sort về cuối", bwd.branch().getName(), bwd.branch().getId());
            result.add(new BranchWithRealDistance(bwd.branch(), bwd.distanceKm(), 999_999, 999_999 / 60.0));
        }

        result.sort(Comparator.comparingDouble(BranchWithRealDistance::durationSeconds));
        return result;
    }
}
