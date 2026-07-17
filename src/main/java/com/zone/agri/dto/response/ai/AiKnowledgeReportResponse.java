package com.zone.agri.dto.response.ai;

import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiKnowledgeReportResponse {

    private long totalQuestions;
    private long matchedQuestions;
    private long unmatchedQuestions;
    private long reviewCaseCount;
    private Map<String, Long> matchedTypeCounts;
    private List<QuestionFrequency> topUnmatchedQuestions;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuestionFrequency {
        private String question;
        private long count;
    }
}
