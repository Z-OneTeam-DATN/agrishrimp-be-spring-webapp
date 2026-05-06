package com.zone.agri.controller;

import com.zone.agri.entity.CannedResponse;
import com.zone.agri.repository.CannedResponseRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/chat/canned-responses")
@RequiredArgsConstructor
@Tag(name = "CannedResponse", description = "Quản lý tin nhắn mẫu")
@SecurityRequirement(name = "bearerAuth")
public class CannedResponseController {

    private final CannedResponseRepository cannedResponseRepository;

    @Operation(summary = "Lấy tất cả tin nhắn mẫu")
    @GetMapping
    public ResponseEntity<List<CannedResponse>> getAll(
            @RequestParam(required = false) String keyword) {
        if (keyword != null && !keyword.isBlank()) {
            return ResponseEntity.ok(cannedResponseRepository.findByShortcutContainingIgnoreCase(keyword));
        }
        return ResponseEntity.ok(cannedResponseRepository.findAllByOrderByShortcutAsc());
    }

    @Operation(summary = "Tạo tin nhắn mẫu")
    @PostMapping
    public ResponseEntity<CannedResponse> create(@RequestBody Map<String, String> body) {
        CannedResponse cr = CannedResponse.builder()
                .shortcut(body.get("shortcut"))
                .content(body.get("content"))
                .build();
        return ResponseEntity.ok(cannedResponseRepository.save(cr));
    }

    @Operation(summary = "Cập nhật tin nhắn mẫu")
    @PutMapping("/{id}")
    public ResponseEntity<CannedResponse> update(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        CannedResponse cr = cannedResponseRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Not found"));
        cr.setShortcut(body.get("shortcut"));
        cr.setContent(body.get("content"));
        return ResponseEntity.ok(cannedResponseRepository.save(cr));
    }

    @Operation(summary = "Xóa tin nhắn mẫu")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        cannedResponseRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
