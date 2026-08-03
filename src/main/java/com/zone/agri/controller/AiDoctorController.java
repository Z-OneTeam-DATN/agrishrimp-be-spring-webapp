package com.zone.agri.controller;

import com.zone.agri.common.AuthUtils;
import com.zone.agri.dto.ai.AiChatResponse;
import com.zone.agri.dto.request.ai.AiDoctorChatRequest;
import com.zone.agri.dto.request.ai.AiDoctorClarifyRequest;
import com.zone.agri.dto.response.ai.AiDoctorClarifyResponse;
import com.zone.agri.dto.response.ai.AiDoctorDailyRecordDetailResponse;
import com.zone.agri.dto.response.ai.AiDoctorDailyRecordListResponse;
import com.zone.agri.dto.response.ai.AiDoctorDiagnosisResponse;
import com.zone.agri.dto.response.ai.AiDoctorHistoryListResponse;
import com.zone.agri.dto.response.user.UserDetail;
import com.zone.agri.exception.CustomAuthenticationException;
import com.zone.agri.service.ai.AiKnowledgeService;
import com.zone.agri.service.aidoctor.AiDoctorClarifyService;
import com.zone.agri.service.aidoctor.AiDoctorDailyRecordService;
import com.zone.agri.service.aidoctor.AiDoctorDiagnosisHistoryService;
import com.zone.agri.service.aidoctor.AiDoctorDiagnosisService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/ai-doctor")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class AiDoctorController {

    private final AiDoctorDiagnosisService diagnosisService;
    private final AiDoctorDiagnosisHistoryService historyService;
    private final AiKnowledgeService aiKnowledgeService;
    private final AiDoctorClarifyService clarifyService;
    private final AiDoctorDailyRecordService dailyRecordService;

    @PostMapping(value = "/diagnosis", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AiDoctorDiagnosisResponse> diagnose(
            @RequestPart("image") MultipartFile image,
            @RequestPart(value = "userSymptoms", required = false) String userSymptoms,
            @RequestPart(value = "sessionId", required = false) String sessionId) {
        UserDetail user = requireUser();
        return ResponseEntity.ok(diagnosisService.diagnose(image, userSymptoms, user.getId(), sessionId));
    }

    @GetMapping("/history")
    public ResponseEntity<AiDoctorHistoryListResponse> getHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UserDetail user = requireUser();
        return ResponseEntity.ok(historyService.getMyDiagnosisHistory(user.getId(), PageRequest.of(page, Math.min(size, 50))));
    }

    @GetMapping("/diagnosis/{id}")
    public ResponseEntity<AiDoctorDiagnosisResponse> getDiagnosisDetail(@PathVariable Long id) {
        UserDetail user = requireUser();
        return ResponseEntity.ok(historyService.getDiagnosisDetail(id, user.getId()));
    }

    @PostMapping("/diagnosis/{id}/prescription")
    public ResponseEntity<AiDoctorDiagnosisResponse> generatePrescription(@PathVariable Long id) {
        UserDetail user = requireUser();
        return ResponseEntity.ok(diagnosisService.generatePrescription(id, user.getId()));
    }

    @PostMapping("/chat")
    public ResponseEntity<AiChatResponse> chat(@RequestBody AiDoctorChatRequest request) {
        UserDetail user = requireUser();
        return ResponseEntity.ok(aiKnowledgeService.answerChat(request, user.getId(), "AI_DOCTOR_PRIVATE", true));
    }

    @PostMapping("/diagnosis/{id}/clarify")
    public ResponseEntity<AiDoctorClarifyResponse> clarify(
            @PathVariable String id,
            @RequestBody(required = false) AiDoctorClarifyRequest request) {
        UserDetail user = requireUser();
        return ResponseEntity.ok(clarifyService.continueClarify(id, request, user.getId(), "AI_DOCTOR_PRIVATE"));
    }

    @GetMapping("/daily-records")
    public ResponseEntity<AiDoctorDailyRecordListResponse> getDailyRecordDates() {
        UserDetail user = requireUser();
        return ResponseEntity.ok(dailyRecordService.getDailyRecordDates(user.getId()));
    }

    @GetMapping("/daily-records/{date}")
    public ResponseEntity<AiDoctorDailyRecordDetailResponse> getDailyRecordDetail(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        UserDetail user = requireUser();
        return ResponseEntity.ok(dailyRecordService.getDailyRecordDetail(user.getId(), date));
    }

    private UserDetail requireUser() {
        UserDetail user = AuthUtils.getUserDetail();
        if (user == null) {
            throw new CustomAuthenticationException("Bạn cần đăng nhập để sử dụng AI Doctor");
        }
        return user;
    }
}
