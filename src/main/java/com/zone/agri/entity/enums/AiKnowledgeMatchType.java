package com.zone.agri.entity.enums;

public enum AiKnowledgeMatchType {
    KEYWORD_SET,
    DISEASE_KNOWLEDGE,
    /**
     * Nhiều bệnh cùng vượt ngưỡng khớp (hoặc gõ chữ gần đạt ngưỡng 1 bệnh) — không tự chọn đại,
     * AI mở phiên hỏi-đáp Gemini (AiChatClarifySession) để hỏi thêm dấu hiệu phân biệt.
     */
    AMBIGUOUS
}
