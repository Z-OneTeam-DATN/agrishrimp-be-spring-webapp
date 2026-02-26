package com.zone.agri.service;

import com.zone.agri.dto.geo.CoordinateDto;
import com.zone.agri.dto.geo.RoutingResult;
import com.zone.agri.entity.Branch;
import com.zone.agri.entity.enums.BranchStatus;
import com.zone.agri.repository.BranchRepository;
import com.zone.agri.utils.BoundingBoxUtils;
import com.zone.agri.utils.HaversineUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Tìm chi nhánh gần nhất theo 3 tầng lọc:
 * <ol>
 *   <li>Bounding Box — filter nhanh theo lat/lng range (dùng DB index)</li>
 *   <li>Haversine — sort chính xác hơn, lấy top N</li>
 *   <li>Distance Matrix API — lấy thời gian thực, sort theo duration</li>
 * </ol>
 * Luôn đảm bảo trả về ít nhất 1 chi nhánh (fallback toàn bộ ACTIVE nếu cần).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BranchSearchService {

    private final BranchRepository branchRepository;
    private final OpenRouteServiceProvider routingProvider;

    @Value("${location.default-radius-km:15}")
    private double defaultRadiusKm;

    @Value("${location.max-candidates:5}")
    private int maxCandidates;

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
     * Tìm danh sách chi nhánh gần nhất với người dùng.
     * Luôn trả về ít nhất 1 chi nhánh — fallback toàn bộ ACTIVE nếu bounding box thất bại
     * (ví dụ: chi nhánh chưa được geocode chưa có lat/lng).
     *
     * @param userLat vĩ độ người dùng
     * @param userLng kinh độ người dùng
     * @return danh sách chi nhánh sorted theo duration/distance tăng dần
     */
    public List<BranchWithRealDistance> findNearestBranches(double userLat, double userLng) {
        double radius = defaultRadiusKm;

        // Tầng 1 — Bounding Box (chỉ branches đã geocode)
        List<Branch> candidates = findCandidates(userLat, userLng, radius);

        // Mở rộng radius x2 nếu không thấy
        if (candidates.isEmpty()) {
            radius = radius * 2;
            candidates = findCandidates(userLat, userLng, radius);
            if (!candidates.isEmpty()) {
                log.info("Bounding box mở rộng {}km tìm được {} chi nhánh", radius, candidates.size());
            }
        }

        // Final fallback: TẤT CẢ chi nhánh ACTIVE (kể cả chưa geocode)
        if (candidates.isEmpty()) {
            log.warn("Không có chi nhánh trong bounding box {}km, fallback toàn bộ ACTIVE branches", radius);
            candidates = branchRepository.findByStatus(BranchStatus.ACTIVE);
        }

        if (candidates.isEmpty()) {
            log.error("Hệ thống không có chi nhánh nào đang hoạt động!");
            return List.of();
        }

        // Tầng 2 — Haversine sort (handle null lat/lng → sort về sau cùng)
        List<BranchWithDistance> top = sortByHaversine(userLat, userLng, candidates, maxCandidates);

        // Tầng 3 — Distance Matrix API (chỉ cho branches có tọa độ hợp lệ)
        return enrichWithRealDistance(userLat, userLng, top);
    }

    // ──────────────────────────────────────────────────────────────
    // Tầng 1 — Bounding Box
    // ──────────────────────────────────────────────────────────────

    List<Branch> findCandidates(double userLat, double userLng, double radiusKm) {
        BoundingBoxUtils.BoundingBox box = BoundingBoxUtils.calculate(userLat, userLng, radiusKm);
        return branchRepository.findBranchesInBoundingBox(
                BranchStatus.ACTIVE,
                box.minLat(), box.maxLat(),
                box.minLng(), box.maxLng()
        );
    }

    // ──────────────────────────────────────────────────────────────
    // Tầng 2 — Haversine sort
    // ──────────────────────────────────────────────────────────────

    List<BranchWithDistance> sortByHaversine(double userLat, double userLng,
                                             List<Branch> candidates, int limit) {
        return candidates.stream()
                .map(b -> {
                    // Branches chưa geocode (null lat/lng) → đặt về cuối danh sách
                    double dist = (b.getLat() != null && b.getLng() != null)
                            ? HaversineUtils.distanceKm(userLat, userLng, b.getLat(), b.getLng())
                            : Double.MAX_VALUE;
                    return new BranchWithDistance(b, dist);
                })
                .sorted(Comparator.comparingDouble(BranchWithDistance::distanceKm))
                .limit(limit)
                .toList();
    }

    // ──────────────────────────────────────────────────────────────
    // Tầng 3 — Distance Matrix API (1 request cho tất cả)
    // ──────────────────────────────────────────────────────────────

    List<BranchWithRealDistance> enrichWithRealDistance(double userLat, double userLng,
                                                        List<BranchWithDistance> top) {
        // Tách branches có tọa độ và không có tọa độ
        List<BranchWithDistance> withCoords = top.stream()
                .filter(bwd -> bwd.branch().getLat() != null && bwd.branch().getLng() != null)
                .toList();
        List<BranchWithDistance> withoutCoords = top.stream()
                .filter(bwd -> bwd.branch().getLat() == null || bwd.branch().getLng() == null)
                .toList();

        List<BranchWithRealDistance> result = new ArrayList<>();

        // Gọi ORS chỉ cho branches có tọa độ
        if (!withCoords.isEmpty()) {
            CoordinateDto origin = new CoordinateDto(userLat, userLng);
            List<CoordinateDto> destinations = withCoords.stream()
                    .map(bwd -> new CoordinateDto(bwd.branch().getLat(), bwd.branch().getLng()))
                    .toList();

            List<Double> durations = null;
            try {
                RoutingResult routingResult = routingProvider.getDistanceMatrix(origin, destinations);
                durations = routingResult.getDurations();
            } catch (Exception e) {
                log.warn("ORS distance matrix thất bại, dùng Haversine fallback: {}", e.getMessage());
            }

            for (int i = 0; i < withCoords.size(); i++) {
                BranchWithDistance bwd = withCoords.get(i);
                double durationSec = (durations != null && i < durations.size() && durations.get(i) >= 0)
                        ? durations.get(i)
                        : bwd.distanceKm() * 120; // Fallback: ~120s/km
                result.add(new BranchWithRealDistance(
                        bwd.branch(), bwd.distanceKm(), durationSec, durationSec / 60.0));
            }
        }

        // Branches không có tọa độ — gán duration lớn (sort về cuối)
        for (BranchWithDistance bwd : withoutCoords) {
            double fallbackDuration = 999_999;
            log.debug("Branch '{}' (id={}) chưa geocode, gán fallback duration {}s",
                    bwd.branch().getName(), bwd.branch().getId(), fallbackDuration);
            result.add(new BranchWithRealDistance(
                    bwd.branch(), bwd.distanceKm(), fallbackDuration, fallbackDuration / 60.0));
        }

        // Sort theo duration tăng dần (có tọa độ trước, không có tọa độ sau)
        result.sort(Comparator.comparingDouble(BranchWithRealDistance::durationSeconds));
        return result;
    }
}
