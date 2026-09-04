package com.zone.agri.config;

import com.zone.agri.dto.response.geo.CoordinateDto;
import com.zone.agri.entity.Branch;
import com.zone.agri.repository.BranchRepository;
import com.zone.agri.service.BranchAddressCanonicalizer;
import com.zone.agri.service.GeocodingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(
        name = "app.startup.branch-address-backfill.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class BranchAddressBackfillRunner implements ApplicationRunner {

    private final BranchRepository branchRepository;
    private final BranchAddressCanonicalizer branchAddressCanonicalizer;
    private final GeocodingService geocodingService;

    @Override
    public void run(ApplicationArguments args) {
        List<Branch> branches = branchRepository.findAll();
        int updatedCount = 0;

        for (Branch branch : branches) {
            boolean changed = false;
            boolean wasCanonical = safelyCheckCanonicalMetadata(branch);

            try {
                changed |= branchAddressCanonicalizer.canonicalize(branch);

                if ((branch.getLat() == null || branch.getLng() == null)
                        && hasText(branchAddressCanonicalizer.buildDisplayAddress(branch))) {
                    CoordinateDto coordinate = geocodeSafely(branch);
                    if (coordinate != null) {
                        if (branch.getLat() == null || !branch.getLat().equals(coordinate.getLat())) {
                            branch.setLat(coordinate.getLat());
                            changed = true;
                        }
                        if (branch.getLng() == null || !branch.getLng().equals(coordinate.getLng())) {
                            branch.setLng(coordinate.getLng());
                            changed = true;
                        }
                    }
                }

                if (changed) {
                    branchRepository.save(branch);
                    updatedCount++;
                }

                if (!wasCanonical && !safelyCheckCanonicalMetadata(branch)) {
                    log.warn(
                            "Branch address backfill could not fully canonicalize branch id={}, code={}, name={}",
                            branch.getId(),
                            branch.getBranchCode(),
                            branch.getName());
                }
            } catch (Exception ex) {
                log.warn(
                        "Branch address backfill skipped branch id={}, code={}, name={} because {}",
                        branch.getId(),
                        branch.getBranchCode(),
                        branch.getName(),
                        ex.getMessage());
            }
        }

        log.info("Branch address backfill completed. scanned={}, updated={}", branches.size(), updatedCount);
    }

    private boolean safelyCheckCanonicalMetadata(Branch branch) {
        try {
            return branchAddressCanonicalizer.hasCanonicalDeliveryMetadata(branch);
        } catch (Exception ex) {
            return false;
        }
    }

    private CoordinateDto geocodeSafely(Branch branch) {
        try {
            return geocodingService.geocode(branchAddressCanonicalizer.buildDisplayAddress(branch));
        } catch (Exception ex) {
            log.warn(
                    "Could not geocode branch during startup backfill id={}, code={}: {}",
                    branch.getId(),
                    branch.getBranchCode(),
                    ex.getMessage());
            return null;
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
