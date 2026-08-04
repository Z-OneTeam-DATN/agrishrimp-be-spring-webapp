package com.zone.agri.dto.response.ai;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

/**
 * Một lượt trong hội thoại "hôm nay" — dùng để FE phát lại (replay) đúng thứ tự các bong bóng chat
 * đã hiện trước đó, sau khi tải lại trang. Gộp 2 nguồn: chat chữ (AiKnowledgeChatLog) và chẩn đoán
 * qua ảnh (AiDoctorDiagnosisHistory), sort chung theo createdAt.
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AiDoctorConversationTurnResponse {

    /** "CHAT" | "DIAGNOSIS" */
    private String type;

    private LocalDateTime createdAt;

    // --- CHAT ---
    private String questionText;
    /** answerText đã lưu sẵn — chính là HTML đã hiển thị lúc đó. */
    private String answerHtml;

    // --- DIAGNOSIS (chỉ ảnh đã lưu History — status HEALTHY/UNRECOGNIZED không bao giờ được
    // lưu History nên không bao giờ xuất hiện ở đây, khỏi phải xử lý 2 case đó). ---
    private String diagnosisId;
    private String userSymptoms;
    private String imageUrl;
    private DiseaseResponse disease;
    private String signsSummary;
    private Boolean needsClarification;
}
