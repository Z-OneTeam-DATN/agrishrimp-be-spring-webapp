package com.zone.agri.dto.response.ai;

import com.zone.agri.entity.enums.AiKnowledgeStatus;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiKeywordAnswerSetResponse {

    private Long id;
    private String code;
    private String name;
    private AiKnowledgeCategoryResponse category;
    private String keywordsRaw;
    private String answerHtml;
    private Boolean enabled;
    private Double matchThreshold;
    private Integer priority;
    private Boolean canonical;
    private AiKnowledgeStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
