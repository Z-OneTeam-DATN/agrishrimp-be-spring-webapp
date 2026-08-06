package com.zone.agri.service;

import com.zone.agri.common.CloudinaryService;
import com.zone.agri.dto.request.banner.BannerRequest;
import com.zone.agri.dto.response.banner.BannerResponse;
import com.zone.agri.entity.Banner;
import com.zone.agri.repository.BannerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BannerService {

    private final BannerRepository bannerRepository;
    private final CloudinaryService cloudinaryService;
    private final BannerAssetCleanupService bannerAssetCleanupService;

    @Transactional
    public List<BannerResponse> getAll() {
        syncExpiredBanners(LocalDateTime.now());
        return normalizeDisplayOrders()
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional
    public List<BannerResponse> getPublicBanners() {
        LocalDateTime now = LocalDateTime.now();
        syncExpiredBanners(now);
        return normalizeDisplayOrders()
                .stream()
                .filter(b -> Boolean.TRUE.equals(b.getIsActive()))
                .filter(b -> b.getStartDate() == null || !b.getStartDate().isAfter(now))
                .filter(b -> b.getEndDate() == null || !b.getEndDate().isBefore(now))
                .collect(Collectors.collectingAndThen(
                        Collectors.toList(),
                        list -> list.stream().map(this::toResponse).collect(Collectors.toList())
                ));
    }

    @Transactional
    public BannerResponse create(BannerRequest req, MultipartFile file, MultipartFile mobileFile) {
        Banner banner = Banner.builder()
                .title(req.getTitle())
                .linkUrl(req.getLinkUrl())
                .displayOrder(req.getDisplayOrder())
                .isActive(req.getIsActive() != null ? req.getIsActive() : true)
                .startDate(req.getStartDate())
                .endDate(req.getEndDate())
                .build();

        applyDesktopImage(banner, req, file);
        applyMobileImage(banner, req, mobileFile);
        if (isExpired(banner)) {
            banner.setIsActive(false);
        }

        List<Banner> orderedBanners = normalizeDisplayOrders();
        int targetIndex = clampDisplayOrder(req.getDisplayOrder(), orderedBanners.size());

        List<Banner> reorderedBanners = new ArrayList<>(orderedBanners);
        reorderedBanners.add(targetIndex, banner);
        assignSequentialDisplayOrders(reorderedBanners);

        return toResponse(bannerRepository.saveAll(reorderedBanners).stream()
                .filter(item -> item.getId() != null && item.getId().equals(banner.getId()))
                .findFirst()
                .orElse(banner));
    }

    @Transactional
    public BannerResponse update(Long id, BannerRequest req, MultipartFile file, MultipartFile mobileFile) {
        Banner banner = bannerRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Banner không tồn tại: " + id));

        applyDesktopImage(banner, req, file);
        applyMobileImage(banner, req, mobileFile);

        if (req.getTitle() != null) banner.setTitle(req.getTitle());
        if (req.getLinkUrl() != null) banner.setLinkUrl(req.getLinkUrl());
        if (req.getIsActive() != null) banner.setIsActive(req.getIsActive());
        banner.setStartDate(req.getStartDate());
        banner.setEndDate(req.getEndDate());
        if (isExpired(banner)) {
            banner.setIsActive(false);
        }

        List<Banner> orderedBanners = normalizeDisplayOrders();
        List<Banner> reorderedBanners = orderedBanners.stream()
                .filter(item -> !item.getId().equals(banner.getId()))
                .collect(Collectors.toCollection(ArrayList::new));

        int currentIndex = orderedBanners.stream()
                .map(Banner::getId)
                .toList()
                .indexOf(banner.getId());
        int targetIndex = clampDisplayOrder(
                req.getDisplayOrder() != null ? req.getDisplayOrder() : currentIndex,
                reorderedBanners.size()
        );

        reorderedBanners.add(targetIndex, banner);
        assignSequentialDisplayOrders(reorderedBanners);

        return toResponse(bannerRepository.saveAll(reorderedBanners).stream()
                .filter(item -> item.getId().equals(banner.getId()))
                .findFirst()
                .orElse(banner));
    }

    @Transactional
    public void toggleActive(Long id) {
        Banner banner = bannerRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Banner không tồn tại: " + id));

        boolean nextActive = !Boolean.TRUE.equals(banner.getIsActive());
        if (nextActive && isExpired(banner)) {
            banner.setIsActive(false);
            bannerRepository.save(banner);
            throw new RuntimeException("Banner đã hết hạn và được chuyển sang trạng thái tạm ẩn.");
        }

        banner.setIsActive(nextActive);
        bannerRepository.save(banner);
    }

    @Transactional
    public void delete(Long id) {
        Banner banner = bannerRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Banner không tồn tại: " + id));
        String publicId = banner.getPublicId();
        String mobilePublicId = banner.getMobilePublicId();
        bannerRepository.delete(banner);

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    bannerAssetCleanupService.deleteImages(publicId, mobilePublicId);
                }
            });
            return;
        }

        bannerAssetCleanupService.deleteImages(publicId, mobilePublicId);
    }

    @Scheduled(fixedDelay = 300000)
    @Transactional
    public void syncExpiredBannerStatuses() {
        syncExpiredBanners(LocalDateTime.now());
    }

    private void applyDesktopImage(Banner banner, BannerRequest req, MultipartFile file) {
        if (file != null && !file.isEmpty()) {
            if (banner.getPublicId() != null) cloudinaryService.delete(banner.getPublicId());
            CloudinaryService.UploadResult result = cloudinaryService.upload(file, "banners");
            banner.setImageUrl(result.secureUrl());
            banner.setPublicId(result.publicId());
            return;
        }

        if (req.getImageUrl() == null) {
            return;
        }

        String nextUrl = normalizeText(req.getImageUrl());
        String nextPublicId = normalizeText(req.getPublicId());

        if (nextUrl == null) {
            if (banner.getPublicId() != null) {
                cloudinaryService.delete(banner.getPublicId());
            }
            banner.setImageUrl(null);
            banner.setPublicId(null);
            return;
        }

        if (!nextUrl.equals(banner.getImageUrl()) || !Objects.equals(nextPublicId, banner.getPublicId())) {
            banner.setImageUrl(nextUrl);
            banner.setPublicId(nextPublicId);
        }
    }

    private void applyMobileImage(Banner banner, BannerRequest req, MultipartFile mobileFile) {
        if (mobileFile != null && !mobileFile.isEmpty()) {
            if (banner.getMobilePublicId() != null) cloudinaryService.delete(banner.getMobilePublicId());
            CloudinaryService.UploadResult result = cloudinaryService.upload(mobileFile, "banners/mobile");
            banner.setMobileImageUrl(result.secureUrl());
            banner.setMobilePublicId(result.publicId());
            return;
        }

        if (req.getMobileImageUrl() == null) {
            return;
        }

        String nextUrl = normalizeText(req.getMobileImageUrl());
        String nextPublicId = normalizeText(req.getMobilePublicId());

        if (nextUrl == null) {
            if (banner.getMobilePublicId() != null) {
                cloudinaryService.delete(banner.getMobilePublicId());
            }
            banner.setMobileImageUrl(null);
            banner.setMobilePublicId(null);
            return;
        }

        if (!nextUrl.equals(banner.getMobileImageUrl()) || !Objects.equals(nextPublicId, banner.getMobilePublicId())) {
            banner.setMobileImageUrl(nextUrl);
            banner.setMobilePublicId(nextPublicId);
        }
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private void syncExpiredBanners(LocalDateTime now) {
        bannerRepository.deactivateExpiredBanners(now);
    }

    private List<Banner> normalizeDisplayOrders() {
        List<Banner> orderedBanners = new ArrayList<>(bannerRepository.findAllByOrderByDisplayOrderAscCreatedAtDesc());
        boolean changed = assignSequentialDisplayOrders(orderedBanners);
        if (changed) {
            bannerRepository.saveAll(orderedBanners);
        }
        return orderedBanners;
    }

    private boolean assignSequentialDisplayOrders(List<Banner> banners) {
        boolean changed = false;
        for (int index = 0; index < banners.size(); index++) {
            Banner item = banners.get(index);
            if (!Objects.equals(item.getDisplayOrder(), index)) {
                item.setDisplayOrder(index);
                changed = true;
            }
        }
        return changed;
    }

    private int clampDisplayOrder(Integer requestedOrder, int maxIndex) {
        if (requestedOrder == null) {
            return maxIndex;
        }
        return Math.max(0, Math.min(requestedOrder, maxIndex));
    }

    private boolean isExpired(Banner banner) {
        return banner.getEndDate() != null && banner.getEndDate().isBefore(LocalDateTime.now());
    }

    private BannerResponse toResponse(Banner b) {
        return BannerResponse.builder()
                .id(b.getId())
                .title(b.getTitle())
                .imageUrl(b.getImageUrl())
                .publicId(b.getPublicId())
                .mobileImageUrl(b.getMobileImageUrl())
                .mobilePublicId(b.getMobilePublicId())
                .linkUrl(b.getLinkUrl())
                .displayOrder(b.getDisplayOrder())
                .isActive(b.getIsActive())
                .startDate(b.getStartDate())
                .endDate(b.getEndDate())
                .createdAt(b.getCreatedAt())
                .updatedAt(b.getUpdatedAt())
                .build();
    }
}
