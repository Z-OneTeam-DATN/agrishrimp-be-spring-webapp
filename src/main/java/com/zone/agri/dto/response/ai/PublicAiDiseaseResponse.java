package com.zone.agri.dto.response.ai;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PublicAiDiseaseResponse {
    private String slug;
    private String code;
    private String nameVi;
    private String nameEn;
    private String categoryName;
    private String categorySlug;
    private String signsSummary;
    private List<String> causes;
    private List<PublicAiDiseaseTreatmentStageResponse> treatmentStages;
    private List<String> imageUrls;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
