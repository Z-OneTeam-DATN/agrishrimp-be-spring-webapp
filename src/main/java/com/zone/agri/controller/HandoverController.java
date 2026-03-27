package com.zone.agri.controller;

import com.zone.agri.dto.request.order.HandoverCreateRequest;
import com.zone.agri.dto.response.order.HandoverDetailResponse;
import com.zone.agri.dto.response.order.HandoverResponse;
import com.zone.agri.entity.Handover;
import com.zone.agri.entity.User;
import com.zone.agri.repository.UserRepository;
import com.zone.agri.service.HandoverService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/branch/handovers")
@RequiredArgsConstructor
public class HandoverController {

    private final HandoverService handoverService;
    private final UserRepository userRepository;

    @PostMapping
    public ResponseEntity<HandoverResponse> createHandover(
            @RequestBody HandoverCreateRequest request,
            Principal principal) {
        String email = principal.getName();
        User currentUser = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy thông tin tài khoản đang đăng nhập"));

        Long userId = currentUser.getId();
        Long branchId = currentUser.getBranch() != null ? currentUser.getBranch().getId() : null;

        if (branchId == null) {
            throw new RuntimeException("Tài khoản của bạn không được phân công quản lý chi nhánh nào!");
        }
        Handover newHandover = handoverService.createHandover(userId, branchId, request);

        return ResponseEntity.ok(HandoverResponse.fromEntity(newHandover));
    }

    @GetMapping
    public ResponseEntity<List<HandoverResponse>> getHandoverList(Principal principal) {
        String email = principal.getName();
        User currentUser = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Long branchId = currentUser.getBranch().getId();
        return ResponseEntity.ok(handoverService.getHandoverList(branchId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<HandoverDetailResponse> getHandoverDetail(@PathVariable Long id) {
        return ResponseEntity.ok(handoverService.getHandoverDetail(id));
    }
}