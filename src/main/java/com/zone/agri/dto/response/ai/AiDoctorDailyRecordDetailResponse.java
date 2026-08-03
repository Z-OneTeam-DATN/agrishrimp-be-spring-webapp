package com.zone.agri.dto.response.ai;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import lombok.Builder;
import lombok.Data;

/**
 * Chi tiết "sổ khám" của 1 ngày — tổng hợp on-demand từ AiKnowledgeChatLog (chat chữ) và
 * AiDoctorDiagnosisHistory (chẩn đoán qua ảnh), không lưu ở entity riêng nào.
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AiDoctorDailyRecordDetailResponse {

    /** "yyyy-MM-dd" */
    private String date;

    /** Các câu triệu chứng farmer đã gõ trong ngày, theo thứ tự thời gian. */
    private List<String> symptomsDescribed;

    /** Bệnh đã khớp/thảo luận qua chat chữ trong ngày (distinct theo mã bệnh). */
    private List<DiseaseResponse> diseasesDiscussed;

    /** Các lần chẩn đoán qua ảnh trong ngày, nếu có. */
    private List<AiDoctorHistoryItemResponse> diagnoses;
}
