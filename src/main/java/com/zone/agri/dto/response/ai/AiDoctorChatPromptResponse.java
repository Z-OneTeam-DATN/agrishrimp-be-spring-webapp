package com.zone.agri.dto.response.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiDoctorChatPromptResponse {

    private String id;
    private String category;
    private String label;
    private String question;
}
