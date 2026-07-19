package com.zone.agri.entity.enums;

public enum AiKnowledgeMatchType {
    KEYWORD_SET,
    DISEASE_KNOWLEDGE,
    /** Nhiều bệnh cùng vượt ngưỡng khớp — AI liệt kê các bệnh nghi ngờ thay vì chọn đại 1 bệnh. */
    AMBIGUOUS
}
