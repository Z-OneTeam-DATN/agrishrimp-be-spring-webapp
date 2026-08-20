package com.zone.agri.service;

import com.zone.agri.dto.response.geo.CoordinateDto;
import com.zone.agri.dto.response.geo.RoutingResult;
import com.zone.agri.entity.Branch;
import com.zone.agri.entity.enums.BranchStatus;
import com.zone.agri.exception.BadRequestException;
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
    public List<BranchWithRealDistance> findNearestBranches(double userLat, double userLng, Integer radiusKm) {
        validateCoordinates(userLat, userLng);
        List<Branch> allActive = branchRepository.findByStatus(BranchStatus.ACTIVE);

        if (allActive.isEmpty()) {
            log.error("Hệ thống không có chi nhánh nào đang hoạt động!");
            return List.of();
        }

        // Haversine sort toàn bộ (không giới hạn)
        List<BranchWithDistance> sorted = sortByHaversine(userLat, userLng, allActive);
        int effectiveRadiusKm = radiusKm != null && radiusKm > 0 ? radiusKm : 15;
        sorted = sorted.stream()
                .filter(candidate -> candidate.distanceKm() <= effectiveRadiusKm)
                .toList();
        if (sorted.isEmpty()) {
            return List.of();
        }

        // ORS enrich top N, Haversine fallback cho phần còn lại
        return enrichWithRealDistance(userLat, userLng, sorted);
    }

    /**
     * Chọn chi nhánh giao hàng theo khoảng cách/thời gian thực tế nếu có tọa độ.
     * Nếu branch thiếu tọa độ thì dùng fallback hành chính theo ward/district/province
     * để giữ thứ tự ổn định.
     */
    public List<BranchWithRealDistance> findBranchesForDelivery(
            Integer provinceId,
            Integer districtId,
            String wardCode,
            Double userLat,
            Double userLng) {
        List<Branch> allActive = branchRepository.findByStatus(BranchStatus.ACTIVE);

        if (allActive.isEmpty()) {
            log.error("Hệ thống không có chi nhánh nào đang hoạt động!");
            return List.of();
        }

        String normalizedWardCode = normalizeWardCode(wardCode);

        if (userLat != null && userLng != null) {
            List<BranchWithDistance> rankedByDistance = sortByHaversine(userLat, userLng, allActive);

            return applyAdministrativeFallbackOrdering(
                    enrichWithRealDistance(userLat, userLng, rankedByDistance),
                    provinceId,
                    districtId,
                    normalizedWardCode);
        }

        List<Branch> rankedWithoutCoordinates = allActive.stream()
                .sorted(Comparator
                        .comparingInt((Branch branch) -> administrativeMatchScore(
                                branch, provinceId, districtId, normalizedWardCode))
                        .thenComparing(Branch::getId, Comparator.nullsLast(Long::compareTo)))
                .toList();

        return buildAdministrativeRanking(rankedWithoutCoordinates, provinceId, districtId, normalizedWardCode);
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
        List<BranchWithRealDistance> result = new ArrayList<>();
        List<BranchWithDistance> withCoords = new ArrayList<>();
        List<BranchWithDistance> withoutCoords = new ArrayList<>();

        for (BranchWithDistance bwd : all) {
            if (bwd.branch().getLat() != null && bwd.branch().getLng() != null) {
                withCoords.add(bwd);
            } else {
                withoutCoords.add(bwd);
            }
        }

        if (!withCoords.isEmpty()) {
            int limit = Math.min(withCoords.size(), orsTopN);
            List<BranchWithDistance> orsCandidates = withCoords.subList(0, limit);
            List<BranchWithDistance> fallbackCandidates = withCoords.subList(limit, withCoords.size());

            List<Double> durations = null;
            try {
                CoordinateDto origin = new CoordinateDto(userLat, userLng);
                List<CoordinateDto> destinations = orsCandidates.stream()
                        .map(bwd -> new CoordinateDto(bwd.branch().getLat(), bwd.branch().getLng()))
                        .toList();
                durations = routingProvider.getDistanceMatrix(origin, destinations).getDurations();
            } catch (Exception e) {
                log.warn("ORS distance matrix thất bại, dùng Haversine fallback: {}", e.getMessage());
            }

            // Process ORS candidates
            for (int i = 0; i < orsCandidates.size(); i++) {
                BranchWithDistance bwd = orsCandidates.get(i);
                double durationSec = (durations != null && i < durations.size() && durations.get(i) >= 0)
                        ? durations.get(i)
                        : estimateDuration(bwd.distanceKm());
                result.add(createRealDistance(bwd, durationSec));
            }

            // Process fallback candidates
            for (BranchWithDistance bwd : fallbackCandidates) {
                result.add(createRealDistance(bwd, estimateDuration(bwd.distanceKm())));
            }
        }

        // Branches chưa geocode — sort về cuối
        for (BranchWithDistance bwd : withoutCoords) {
            log.debug("Branch '{}' (id={}) chưa geocode, sort về cuối", bwd.branch().getName(), bwd.branch().getId());
            result.add(createRealDistance(bwd, 999_999));
        }

        result.sort(Comparator.comparingDouble(BranchWithRealDistance::durationSeconds)
                .thenComparingDouble(BranchWithRealDistance::distanceKm)
                .thenComparing(candidate -> candidate.branch().getId(), Comparator.nullsLast(Long::compareTo)));
        return result;
    }

    private void validateCoordinates(double userLat, double userLng) {
        if (Double.isNaN(userLat) || Double.isNaN(userLng)
                || Double.isInfinite(userLat) || Double.isInfinite(userLng)) {
            throw new BadRequestException("Tọa độ không hợp lệ");
        }
        if (userLat < -90 || userLat > 90 || userLng < -180 || userLng > 180) {
            throw new BadRequestException("Tọa độ không hợp lệ");
        }
    }

    private double estimateDuration(double distanceKm) {
        return distanceKm * 120; // 120s/km estimate
    }

    private BranchWithRealDistance createRealDistance(BranchWithDistance bwd, double durationSec) {
        return new BranchWithRealDistance(bwd.branch(), bwd.distanceKm(), durationSec, durationSec / 60.0);
    }

    private List<BranchWithRealDistance> buildAdministrativeRanking(
            List<Branch> branches,
            Integer provinceId,
            Integer districtId,
            String wardCode) {
        List<BranchWithRealDistance> ranked = new ArrayList<>();

        for (int index = 0; index < branches.size(); index++) {
            Branch branch = branches.get(index);
            int score = administrativeMatchScore(branch, provinceId, districtId, wardCode);
            double distanceKm = administrativeDistanceForScore(score) + (index * 0.1);
            double durationSec = estimateDuration(distanceKm);
            ranked.add(new BranchWithRealDistance(branch, distanceKm, durationSec, durationSec / 60.0));
        }

        return ranked;
    }

    private List<BranchWithRealDistance> applyAdministrativeFallbackOrdering(
            List<BranchWithRealDistance> branches,
            Integer provinceId,
            Integer districtId,
            String wardCode) {
        return branches.stream()
                .map(candidate -> withAdministrativeFallbackDistance(candidate, provinceId, districtId, wardCode))
                .sorted(Comparator
                        .comparingDouble(BranchWithRealDistance::durationSeconds)
                        .thenComparingDouble(BranchWithRealDistance::distanceKm)
                        .thenComparingInt((BranchWithRealDistance candidate) ->
                                administrativeMatchScore(candidate.branch(), provinceId, districtId, wardCode))
                        .thenComparing(candidate -> candidate.branch().getId(), Comparator.nullsLast(Long::compareTo)))
                .toList();
    }

    private BranchWithRealDistance withAdministrativeFallbackDistance(
            BranchWithRealDistance candidate,
            Integer provinceId,
            Integer districtId,
            String wardCode) {
        if (candidate == null || candidate.branch() == null || hasCoordinates(candidate.branch())) {
            return candidate;
        }

        int score = administrativeMatchScore(candidate.branch(), provinceId, districtId, wardCode);
        double distanceKm = administrativeDistanceForScore(score);
        double durationSec = estimateDuration(distanceKm);
        return new BranchWithRealDistance(candidate.branch(), distanceKm, durationSec, durationSec / 60.0);
    }

    private boolean hasCoordinates(Branch branch) {
        return branch != null && branch.getLat() != null && branch.getLng() != null;
    }

    private double administrativeDistanceForScore(int score) {
        return switch (score) {
            case 0 -> 1.0;
            case 1 -> 4.0;
            case 2 -> 12.0;
            default -> 30.0;
        };
    }

    private int administrativeMatchScore(Branch branch, Integer provinceId, Integer districtId, String wardCode) {
        if (branch == null) {
            return Integer.MAX_VALUE;
        }

        String branchWardCode = normalizeWardCode(branch.getWardCode());
        if (wardCode != null && wardCode.equals(branchWardCode)) {
            return 0;
        }
        if (districtId != null && districtId.equals(branch.getDistrictId())) {
            return 1;
        }
        if (provinceId != null && provinceId.equals(branch.getProvinceId())) {
            return 2;
        }
        return 3;
    }

    private String normalizeWardCode(String wardCode) {
        if (wardCode == null) {
            return null;
        }

        String normalized = wardCode.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
