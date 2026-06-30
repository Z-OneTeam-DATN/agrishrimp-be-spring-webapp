package com.zone.agri.service;

import com.zone.agri.common.CloudinaryService;
import com.zone.agri.dto.request.banner.BannerRequest;
import com.zone.agri.dto.response.banner.BannerResponse;
import com.zone.agri.entity.Banner;
import com.zone.agri.repository.BannerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BannerService {

    private final BannerRepository bannerRepository;
    private final CloudinaryService cloudinaryService;

    public List<BannerResponse> getAll() {
        return bannerRepository.findAllByOrderByDisplayOrderAscCreatedAtDesc()
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    public List<BannerResponse> getPublicBanners() {
        LocalDateTime now = LocalDateTime.now();
        return bannerRepository.findAllByOrderByDisplayOrderAscCreatedAtDesc()
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
                .displayOrder(req.getDisplayOrder() != null ? req.getDisplayOrder() : 0)
                .isActive(req.getIsActive() != null ? req.getIsActive() : true)
                .startDate(req.getStartDate())
                .endDate(req.getEndDate())
                .build();

        applyDesktopImage(banner, req, file);
        applyMobileImage(banner, req, mobileFile);

        return toResponse(bannerRepository.save(banner));
    }

    @Transactional
    public BannerResponse update(Long id, BannerRequest req, MultipartFile file, MultipartFile mobileFile) {
        Banner banner = bannerRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Banner không tồn tại: " + id));

        applyDesktopImage(banner, req, file);
        applyMobileImage(banner, req, mobileFile);

        if (req.getTitle() != null) banner.setTitle(req.getTitle());
        if (req.getLinkUrl() != null) banner.setLinkUrl(req.getLinkUrl());
        if (req.getDisplayOrder() != null) banner.setDisplayOrder(req.getDisplayOrder());
        if (req.getIsActive() != null) banner.setIsActive(req.getIsActive());
        banner.setStartDate(req.getStartDate());
        banner.setEndDate(req.getEndDate());

        return toResponse(bannerRepository.save(banner));
    }

    @Transactional
    public void toggleActive(Long id) {
        Banner banner = bannerRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Banner không tồn tại: " + id));
        banner.setIsActive(!Boolean.TRUE.equals(banner.getIsActive()));
        bannerRepository.save(banner);
    }

    @Transactional
    public void delete(Long id) {
        Banner banner = bannerRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Banner không tồn tại: " + id));
        if (banner.getPublicId() != null) cloudinaryService.delete(banner.getPublicId());
        if (banner.getMobilePublicId() != null) cloudinaryService.delete(banner.getMobilePublicId());
        bannerRepository.delete(banner);
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
