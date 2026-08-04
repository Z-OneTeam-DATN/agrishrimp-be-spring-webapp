package com.zone.agri.dto.response.ai;

import com.zone.agri.entity.enums.AiKnowledgeStatus;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiDiseaseKnowledgeResponse {

    private Long id;
    private String code;
    private String nameVi;
    private String nameEn;
    private AiKnowledgeCategoryResponse category;
    private String aliasesRaw;
    private String symptomKeywordsRaw;
    private String signsSummary;
    private List<String> causes;
    private List<AiKnowledgeTreatmentStageResponse> treatmentStages;
    private List<String> imageUrls;
    private String engineerName;
    private String engineerPhone;
    private Double confidenceThreshold;
    private Double matchThreshold;
    private Boolean enabled;
    private Integer priority;
    private Boolean canonical;
    private AiKnowledgeStatus status;
    private String reviewNote;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
