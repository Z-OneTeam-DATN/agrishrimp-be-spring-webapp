package com.zone.agri.dto.response.ai;

import com.zone.agri.entity.enums.AiReviewCaseReason;
import com.zone.agri.entity.enums.AiReviewCaseStatus;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiKnowledgeReviewCaseResponse {

    private Long id;
    private Long userId;
    private String sessionId;
    private String sourceChannel;
    private String questionText;
    private String userSymptoms;
    private String imageUrl;
    private String aiSuggestedDiseaseCode;
    private String matchedKnowledgeCode;
    private Double matchScore;
    private AiReviewCaseReason reason;
    private AiReviewCaseStatus status;
    private String resolutionNotes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
