package com.zone.agri.dto.request.ai;

import com.zone.agri.entity.enums.AiKnowledgeStatus;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiDiseaseKnowledgeRequest {

    private String code;
    private String nameVi;
    private String nameEn;
    private Long categoryId;
    private String aliasesRaw;
    private String symptomKeywordsRaw;
    private String signsSummary;
    private List<String> causes;
    private List<AiKnowledgeTreatmentStageRequest> treatmentStages;
    private Double confidenceThreshold;
    private Double matchThreshold;
    private Boolean enabled;
    private Integer priority;
    private Boolean canonical;
    private AiKnowledgeStatus status;
}
