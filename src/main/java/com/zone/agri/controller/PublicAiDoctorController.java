package com.zone.agri.controller;

import com.zone.agri.dto.ai.AiChatResponse;
import com.zone.agri.dto.request.ai.AiDoctorChatRequest;
import com.zone.agri.dto.response.ai.AiDoctorDiagnosisResponse;
import com.zone.agri.dto.response.ai.AiDoctorChatPromptResponse;
import com.zone.agri.service.ai.AiKnowledgeService;
import com.zone.agri.service.aidoctor.AiDoctorDiagnosisService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/public/ai-doctor")
@RequiredArgsConstructor
public class PublicAiDoctorController {

    private final AiKnowledgeService aiKnowledgeService;
    private final AiDoctorDiagnosisService diagnosisService;

    @GetMapping("/prompts")
    public ResponseEntity<List<AiDoctorChatPromptResponse>> getPrompts() {
        return ResponseEntity.ok(aiKnowledgeService.getChatPrompts());
    }

    @PostMapping("/chat")
    public ResponseEntity<AiChatResponse> chat(@RequestBody AiDoctorChatRequest request) {
        return ResponseEntity.ok(aiKnowledgeService.answerChat(request, null, "AI_DOCTOR_PUBLIC", true));
    }

    @PostMapping(value = "/diagnosis", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AiDoctorDiagnosisResponse> diagnose(
            @RequestPart("image") MultipartFile image,
            @RequestPart(value = "userSymptoms", required = false) String userSymptoms,
            @RequestPart(value = "sessionId", required = false) String sessionId) {
        return ResponseEntity.ok(diagnosisService.diagnose(image, userSymptoms, null, sessionId));
    }
}
