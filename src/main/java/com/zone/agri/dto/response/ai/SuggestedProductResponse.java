package com.zone.agri.dto.response.ai;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SuggestedProductResponse {

    private Long id;
    private String name;
    private String image;
    private Long price;      // Phase BE-3: importPrice × 1.3 từ lô tồn; 0 nếu chưa có lô nhập
    private String webUrl;
}
