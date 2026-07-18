package com.zone.agri.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Một lượt trong hội thoại hỏi-đáp làm rõ bệnh — lưu trong AiDoctorClarifySession.conversationJson
 * và cũng là đơn vị lịch sử gửi lại cho Gemini ở mỗi lượt gọi.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiClarifyTurn {

    public static final String ROLE_ASSISTANT = "ASSISTANT";
    public static final String ROLE_FARMER = "FARMER";

    private String role;
    private String text;
}
