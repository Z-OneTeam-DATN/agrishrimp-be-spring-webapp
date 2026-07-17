package com.zone.agri.dto.response.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiKnowledgeChatConfigResponse {

    private Long id;
    private String greetingMessage;
    private String fallbackMessage;
}
