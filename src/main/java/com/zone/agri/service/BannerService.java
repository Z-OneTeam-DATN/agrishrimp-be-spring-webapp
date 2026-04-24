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
    public BannerResponse create(BannerRequest req, MultipartFile file) {
        Banner banner = Banner.builder()
                .title(req.getTitle())
                .linkUrl(req.getLinkUrl())
                .displayOrder(req.getDisplayOrder() != null ? req.getDisplayOrder() : 0)
                .isActive(req.getIsActive() != null ? req.getIsActive() : true)
                .startDate(req.getStartDate())
                .endDate(req.getEndDate())
                .build();

        if (file != null && !file.isEmpty()) {
            CloudinaryService.UploadResult result = cloudinaryService.upload(file, "banners");
            banner.setImageUrl(result.secureUrl());
            banner.setPublicId(result.publicId());
        } else if (req.getImageUrl() != null) {
            banner.setImageUrl(req.getImageUrl());
            banner.setPublicId(req.getPublicId());
        }

        return toResponse(bannerRepository.save(banner));
    }

    @Transactional
    public BannerResponse update(Long id, BannerRequest req, MultipartFile file) {
        Banner banner = bannerRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Banner không tồn tại: " + id));

        if (file != null && !file.isEmpty()) {
            if (banner.getPublicId() != null) cloudinaryService.delete(banner.getPublicId());
            CloudinaryService.UploadResult result = cloudinaryService.upload(file, "banners");
            banner.setImageUrl(result.secureUrl());
            banner.setPublicId(result.publicId());
        } else if (req.getImageUrl() != null && !req.getImageUrl().equals(banner.getImageUrl())) {
            banner.setImageUrl(req.getImageUrl());
            banner.setPublicId(req.getPublicId());
        }

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
        bannerRepository.delete(banner);
    }

    private BannerResponse toResponse(Banner b) {
        return BannerResponse.builder()
                .id(b.getId())
                .title(b.getTitle())
                .imageUrl(b.getImageUrl())
                .publicId(b.getPublicId())
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
