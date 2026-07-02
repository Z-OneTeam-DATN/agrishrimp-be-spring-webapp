package com.zone.agri.service;

import com.zone.agri.common.CloudinaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BannerAssetCleanupService {

    private final CloudinaryService cloudinaryService;

    @Async
    public void deleteImages(String publicId, String mobilePublicId) {
        cloudinaryService.delete(publicId);

        if (mobilePublicId != null && !mobilePublicId.equals(publicId)) {
            cloudinaryService.delete(mobilePublicId);
        }
    }
}
