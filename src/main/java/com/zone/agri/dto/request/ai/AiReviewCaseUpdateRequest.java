package com.zone.agri.dto.request.ai;

import com.zone.agri.entity.enums.AiReviewCaseStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiReviewCaseUpdateRequest {

    private AiReviewCaseStatus status;
    private String matchedKnowledgeCode;
    private String resolutionNotes;
}
