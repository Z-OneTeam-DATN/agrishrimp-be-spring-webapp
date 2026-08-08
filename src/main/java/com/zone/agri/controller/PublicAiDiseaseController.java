package com.zone.agri.controller;

import com.zone.agri.dto.response.ai.PublicAiDiseaseResponse;
import com.zone.agri.service.ai.AiKnowledgeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/ai-diseases")
@RequiredArgsConstructor
@Tag(name = "Public AI Disease APIs", description = "API công khai cho kho tri thức bệnh tôm đã được duyệt")
public class PublicAiDiseaseController {

    private final AiKnowledgeService aiKnowledgeService;

    @Operation(summary = "Lấy danh sách bệnh tôm đã duyệt", description = "Chỉ trả tri thức APPROVED và enabled; không yêu cầu xác thực.")
    @GetMapping
    public ResponseEntity<List<PublicAiDiseaseResponse>> getPublicDiseases() {
        return ResponseEntity.ok(aiKnowledgeService.getPublicDiseaseKnowledgeEntries());
    }

    @Operation(summary = "Lấy chi tiết bệnh tôm đã duyệt theo slug", description = "Chỉ trả dữ liệu public đã được duyệt; không expose threshold hoặc review note.")
    @GetMapping("/{slug}")
    public ResponseEntity<PublicAiDiseaseResponse> getPublicDiseaseBySlug(@PathVariable String slug) {
        return ResponseEntity.ok(aiKnowledgeService.getPublicDiseaseKnowledgeBySlug(slug));
    }
}
