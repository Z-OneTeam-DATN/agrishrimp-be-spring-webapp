package com.zone.agri.dto.response.ai;

import java.util.List;
import java.util.stream.IntStream;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TreatmentStageSelectionResponse {

    public static final String TYPE_STAGE = "STAGE";
    public static final String TYPE_SUB_STAGE = "SUB_STAGE";

    private Boolean required;
    private String selectionType;
    private Integer selectedStageIndex;
    private Integer selectedStageNumber;
    private String selectedStageTitle;
    private String message;
    private List<TreatmentStageOptionResponse> options;

    public static TreatmentStageSelectionResponse fromStages(List<TreatmentStageResponse> stages) {
        List<TreatmentStageResponse> safeStages = stages != null ? stages : List.of();
        return TreatmentStageSelectionResponse.builder()
                .required(true)
                .selectionType(TYPE_STAGE)
                .message("Bà con chọn giai đoạn bệnh hiện tại để bác sĩ đưa bước xử lý phù hợp.")
                .options(IntStream.range(0, safeStages.size())
                        .mapToObj(index -> TreatmentStageOptionResponse.builder()
                                .stageIndex(index)
                                .stageNumber(index + 1)
                                .stageTitle(resolveStageTitle(safeStages.get(index), index))
                                .build())
                        .toList())
                .build();
    }

    public static TreatmentStageSelectionResponse fromSubStages(List<TreatmentStageResponse> stages, int stageIndex) {
        List<TreatmentStageResponse> safeStages = stages != null ? stages : List.of();
        if (stageIndex < 0 || stageIndex >= safeStages.size()) {
            return TreatmentStageSelectionResponse.builder()
                    .required(true)
                    .selectionType(TYPE_SUB_STAGE)
                    .message("Giai đoạn bệnh không hợp lệ.")
                    .options(List.of())
                    .build();
        }

        TreatmentStageResponse selectedStage = safeStages.get(stageIndex);
        List<TreatmentStageResponse> subStages = selectedStage.getSubStages() != null
                ? selectedStage.getSubStages()
                : List.of();
        return TreatmentStageSelectionResponse.builder()
                .required(true)
                .selectionType(TYPE_SUB_STAGE)
                .selectedStageIndex(stageIndex)
                .selectedStageNumber(stageIndex + 1)
                .selectedStageTitle(resolveStageTitle(selectedStage, stageIndex))
                .message("Bà con chọn giai đoạn con đang gặp để xem đúng phác đồ và sản phẩm.")
                .options(IntStream.range(0, subStages.size())
                        .mapToObj(index -> TreatmentStageOptionResponse.builder()
                                .stageIndex(stageIndex)
                                .stageNumber(stageIndex + 1)
                                .stageTitle(resolveSubStageTitle(subStages.get(index), stageIndex, index))
                                .subStageIndex(index)
                                .subStageNumber((stageIndex + 1) + "." + (index + 1))
                                .subStageTitle(resolveSubStageTitle(subStages.get(index), stageIndex, index))
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

    private static String resolveSubStageTitle(TreatmentStageResponse stage, int stageIndex, int subStageIndex) {
        String title = stage != null ? stage.getStageTitle() : null;
        if (title == null || title.isBlank()) {
            return "Giai đoạn " + (stageIndex + 1) + "." + (subStageIndex + 1);
        }
        return title.trim();
    }
}
