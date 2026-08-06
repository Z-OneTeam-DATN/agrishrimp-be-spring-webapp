package com.zone.agri.dto.response.ai;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiKnowledgeImportPreviewRowResponse {

    private int rowNumber;
    private String type;
    private String categoryName;
    private String code;
    private String name;
    private String status;
    private boolean valid;
    private List<String> warnings;
    private List<String> errors;
    private String aliasesRaw;
    private String symptomKeywordsRaw;
    private String answerHtml;
    private String signsSummary;
    private List<String> causes;
    private List<AiKnowledgeTreatmentStageResponse> treatmentStages;
    private Double matchThreshold;
    private Double confidenceThreshold;
    private Boolean enabled;
}
