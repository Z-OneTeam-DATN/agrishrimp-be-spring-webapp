package com.zone.agri.dto.request.ai;

import com.zone.agri.entity.enums.AiKnowledgeStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiKeywordAnswerSetRequest {

    private String code;
    private String name;
    private Long categoryId;
    private String keywordsRaw;
    private String answerHtml;
    private Boolean enabled;
    private Double matchThreshold;
    private Integer priority;
    private Boolean canonical;
    private AiKnowledgeStatus status;
}
