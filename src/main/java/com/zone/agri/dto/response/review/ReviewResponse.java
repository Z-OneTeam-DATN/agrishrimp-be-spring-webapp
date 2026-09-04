package com.zone.agri.dto.response.review;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class ReviewResponse {
    private Long id;
    private Long productId;
    private String productName;
    private Integer rating;
    private String comment;
    private List<String> imageUrls;
    private LocalDateTime createdAt;
    private String userName;
    private String userAvatar;
}
