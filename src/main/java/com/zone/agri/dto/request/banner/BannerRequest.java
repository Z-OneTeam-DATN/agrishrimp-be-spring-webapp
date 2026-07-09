package com.zone.agri.dto.request.banner;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class BannerRequest {
    private String title;
    private String imageUrl;
    private String publicId;
    private String mobileImageUrl;
    private String mobilePublicId;
    private String linkUrl;
    private Integer displayOrder;
    private Boolean isActive;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
}
