package com.zone.agri.dto.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class AiChatResponse {

    @JsonProperty("success")
    private boolean success;

    @JsonProperty("conversation_id")
    private String conversationId;

    @JsonProperty("reply")
    private String reply;

    @JsonProperty("suggested_actions")
    private List<String> suggestedActions;
}
