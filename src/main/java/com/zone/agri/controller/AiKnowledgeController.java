package com.zone.agri.controller;

import com.zone.agri.dto.ai.AiChatResponse;
import com.zone.agri.dto.request.ai.AiDiseaseKnowledgeRejectRequest;
import com.zone.agri.dto.request.ai.AiDiseaseKnowledgeRequest;
import com.zone.agri.dto.request.ai.AiDiseaseKnowledgeVisibilityRequest;
import com.zone.agri.dto.request.ai.AiDoctorChatRequest;
import com.zone.agri.dto.request.ai.AiKeywordAnswerSetRequest;
import com.zone.agri.dto.request.ai.AiKnowledgeCategoryRequest;
import com.zone.agri.dto.request.ai.AiKnowledgeChatConfigRequest;
import com.zone.agri.dto.request.ai.AiKnowledgeImportApplyRequest;
import com.zone.agri.dto.request.ai.AiReviewCaseUpdateRequest;
import com.zone.agri.dto.response.ai.AiDiseaseKnowledgeResponse;
import com.zone.agri.dto.response.ai.AiKnowledgeCategoryResponse;
import com.zone.agri.dto.response.ai.AiKnowledgeChatConfigResponse;
import com.zone.agri.dto.response.ai.AiKnowledgeImportPreviewResponse;
import com.zone.agri.dto.response.ai.AiKnowledgeReviewCaseResponse;
import com.zone.agri.dto.response.ai.AiKeywordAnswerSetResponse;
import com.zone.agri.entity.enums.AiReviewCaseStatus;
import com.zone.agri.security.annotation.RequirePermission;
import com.zone.agri.service.ai.AiKnowledgeService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/ai-knowledge")
@RequiredArgsConstructor
public class AiKnowledgeController {

    private final AiKnowledgeService aiKnowledgeService;

    @GetMapping("/categories")
    @RequirePermission("AI_KNOWLEDGE_VIEW")
    public ResponseEntity<List<AiKnowledgeCategoryResponse>> getCategories() {
        return ResponseEntity.ok(aiKnowledgeService.getCategories());
    }

    @PostMapping("/categories")
    @RequirePermission("AI_KNOWLEDGE_CREATE")
    public ResponseEntity<AiKnowledgeCategoryResponse> createCategory(@RequestBody AiKnowledgeCategoryRequest request) {
        return ResponseEntity.ok(aiKnowledgeService.createCategory(request));
    }

    @PutMapping("/categories/{id}")
    @RequirePermission("AI_KNOWLEDGE_UPDATE")
    public ResponseEntity<AiKnowledgeCategoryResponse> updateCategory(
            @PathVariable Long id,
            @RequestBody AiKnowledgeCategoryRequest request) {
        return ResponseEntity.ok(aiKnowledgeService.updateCategory(id, request));
    }

    @DeleteMapping("/categories/{id}")
    @RequirePermission("AI_KNOWLEDGE_UPDATE")
    public ResponseEntity<Void> deleteCategory(@PathVariable Long id) {
        aiKnowledgeService.deleteCategory(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/keyword-sets")
    @RequirePermission("AI_KNOWLEDGE_VIEW")
    public ResponseEntity<List<AiKeywordAnswerSetResponse>> getKeywordSets() {
        return ResponseEntity.ok(aiKnowledgeService.getKeywordAnswerSets());
    }

    @PostMapping("/keyword-sets")
    @RequirePermission("AI_KNOWLEDGE_CREATE")
    public ResponseEntity<AiKeywordAnswerSetResponse> createKeywordSet(@RequestBody AiKeywordAnswerSetRequest request) {
        return ResponseEntity.ok(aiKnowledgeService.createKeywordAnswerSet(request));
    }

    @PutMapping("/keyword-sets/{id}")
    @RequirePermission("AI_KNOWLEDGE_UPDATE")
    public ResponseEntity<AiKeywordAnswerSetResponse> updateKeywordSet(
            @PathVariable Long id,
            @RequestBody AiKeywordAnswerSetRequest request) {
        return ResponseEntity.ok(aiKnowledgeService.updateKeywordAnswerSet(id, request));
    }

    @DeleteMapping("/keyword-sets/{id}")
    @RequirePermission("AI_KNOWLEDGE_UPDATE")
    public ResponseEntity<Void> deleteKeywordSet(@PathVariable Long id) {
        aiKnowledgeService.deleteKeywordAnswerSet(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/diseases")
    @RequirePermission("AI_KNOWLEDGE_VIEW")
    public ResponseEntity<List<AiDiseaseKnowledgeResponse>> getDiseases() {
        return ResponseEntity.ok(aiKnowledgeService.getDiseaseKnowledgeEntries());
    }

    @PostMapping("/diseases")
    @RequirePermission("AI_KNOWLEDGE_CREATE")
    public ResponseEntity<AiDiseaseKnowledgeResponse> createDisease(@RequestBody AiDiseaseKnowledgeRequest request) {
        return ResponseEntity.ok(aiKnowledgeService.createDiseaseKnowledge(request));
    }

    @PutMapping("/diseases/{id}")
    @RequirePermission("AI_KNOWLEDGE_UPDATE")
    public ResponseEntity<AiDiseaseKnowledgeResponse> updateDisease(
            @PathVariable Long id,
            @RequestBody AiDiseaseKnowledgeRequest request) {
        return ResponseEntity.ok(aiKnowledgeService.updateDiseaseKnowledge(id, request));
    }

    @DeleteMapping("/diseases/{id}")
    @RequirePermission("AI_KNOWLEDGE_UPDATE")
    public ResponseEntity<Void> deleteDisease(@PathVariable Long id) {
        aiKnowledgeService.deleteDiseaseKnowledge(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/diseases/{id}/approve")
    @RequirePermission("AI_KNOWLEDGE_APPROVE")
    public ResponseEntity<AiDiseaseKnowledgeResponse> approveDisease(@PathVariable Long id) {
        return ResponseEntity.ok(aiKnowledgeService.approveDiseaseKnowledge(id));
    }

    @PatchMapping("/diseases/{id}/reject")
    @RequirePermission("AI_KNOWLEDGE_APPROVE")
    public ResponseEntity<AiDiseaseKnowledgeResponse> rejectDisease(
            @PathVariable Long id,
            @RequestBody(required = false) AiDiseaseKnowledgeRejectRequest request) {
        String reason = request != null ? request.getReason() : null;
        return ResponseEntity.ok(aiKnowledgeService.rejectDiseaseKnowledge(id, reason));
    }

    @PatchMapping("/diseases/{id}/visibility")
    @RequirePermission("AI_KNOWLEDGE_APPROVE")
    public ResponseEntity<AiDiseaseKnowledgeResponse> setDiseaseVisibility(
            @PathVariable Long id,
            @RequestBody AiDiseaseKnowledgeVisibilityRequest request) {
        return ResponseEntity.ok(
                aiKnowledgeService.setDiseaseKnowledgeVisibility(id, Boolean.TRUE.equals(request.getEnabled())));
    }

    @GetMapping("/config")
    @RequirePermission({"AI_KNOWLEDGE_VIEW", "AI_KNOWLEDGE_APPROVE"})
    public ResponseEntity<AiKnowledgeChatConfigResponse> getConfig() {
        return ResponseEntity.ok(aiKnowledgeService.getChatConfig());
    }

    @PutMapping("/config")
    @RequirePermission({"AI_KNOWLEDGE_UPDATE", "AI_KNOWLEDGE_APPROVE"})
    public ResponseEntity<AiKnowledgeChatConfigResponse> updateConfig(@RequestBody AiKnowledgeChatConfigRequest request) {
        return ResponseEntity.ok(aiKnowledgeService.updateChatConfig(request));
    }

    @GetMapping("/review-cases")
    @RequirePermission({"AI_CASE_REVIEW", "AI_KNOWLEDGE_VIEW"})
    public ResponseEntity<List<AiKnowledgeReviewCaseResponse>> getReviewCases(
            @RequestParam(required = false) AiReviewCaseStatus status) {
        return ResponseEntity.ok(aiKnowledgeService.getReviewCases(status));
    }

    @PutMapping("/review-cases/{id}")
    @RequirePermission("AI_CASE_REVIEW")
    public ResponseEntity<AiKnowledgeReviewCaseResponse> updateReviewCase(
            @PathVariable Long id,
            @RequestBody AiReviewCaseUpdateRequest request) {
        return ResponseEntity.ok(aiKnowledgeService.updateReviewCase(id, request));
    }

    @PostMapping(value = "/import/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequirePermission("AI_IMPORT_KNOWLEDGE")
    public ResponseEntity<AiKnowledgeImportPreviewResponse> previewImport(
            @RequestPart("file") MultipartFile file,
            @RequestPart(value = "mode", required = false) String mode) {
        return ResponseEntity.ok(aiKnowledgeService.previewImport(file, mode));
    }

    @PostMapping("/import/apply")
    @RequirePermission("AI_IMPORT_KNOWLEDGE")
    public ResponseEntity<AiKnowledgeImportPreviewResponse> applyImport(@RequestBody AiKnowledgeImportApplyRequest request) {
        return ResponseEntity.ok(aiKnowledgeService.applyImport(request));
    }

    @GetMapping("/import/template")
    @RequirePermission({"AI_IMPORT_KNOWLEDGE", "AI_KNOWLEDGE_VIEW"})
    public ResponseEntity<byte[]> downloadTemplate() {
        byte[] fileBytes = aiKnowledgeService.buildImportTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("ai-knowledge-template.xlsx")
                .build());
        return ResponseEntity.ok()
                .headers(headers)
                .body(fileBytes);
    }

    @PostMapping("/test-chat")
    @RequirePermission({"AI_KNOWLEDGE_VIEW", "AI_CASE_REVIEW"})
    public ResponseEntity<AiChatResponse> testChat(@RequestBody AiDoctorChatRequest request) {
        return ResponseEntity.ok(aiKnowledgeService.answerChat(request, null, "AI_KNOWLEDGE_TESTER", false));
    }
}
