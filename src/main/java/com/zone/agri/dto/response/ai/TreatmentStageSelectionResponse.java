package com.zone.agri.dto.response.ai;

import java.util.List;
import java.util.stream.IntStream;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TreatmentStageSelectionResponse {

    private Boolean required;
    private String message;
    private List<TreatmentStageOptionResponse> options;

    public static TreatmentStageSelectionResponse fromStages(List<TreatmentStageResponse> stages) {
        List<TreatmentStageResponse> safeStages = stages != null ? stages : List.of();
        return TreatmentStageSelectionResponse.builder()
                .required(true)
                .message("Bà con chọn đúng giai đoạn bệnh hiện tại để bác sĩ đưa phác đồ điều trị phù hợp.")
                .options(IntStream.range(0, safeStages.size())
                        .mapToObj(index -> TreatmentStageOptionResponse.builder()
                                .stageIndex(index)
                                .stageNumber(index + 1)
                                .stageTitle(resolveStageTitle(safeStages.get(index), index))
                                .build())
                        .toList())
                .build();
    }

    private static String resolveStageTitle(TreatmentStageResponse stage, int index) {
        String title = stage != null ? stage.getStageTitle() : null;
        if (title == null || title.isBlank()) {
            return "Giai đoạn " + (index + 1);
        }
        return title.trim();
    }
}
