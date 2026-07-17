package com.zone.agri.dto.request.ai;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiKnowledgeImportPreviewRowRequest {

    private int rowNumber;
    private String type;
    private String categoryName;
    private String code;
    private String name;
    private String aliasesRaw;
    private String symptomKeywordsRaw;
    private String answerHtml;
    private String signsSummary;
    private List<String> causes;
    private List<AiKnowledgeTreatmentStageRequest> treatmentStages;
    private Double matchThreshold;
    private Double confidenceThreshold;
    private Boolean enabled;
}
