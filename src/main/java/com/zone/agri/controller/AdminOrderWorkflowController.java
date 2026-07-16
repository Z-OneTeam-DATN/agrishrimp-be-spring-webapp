package com.zone.agri.controller;

import com.zone.agri.entity.User;
import com.zone.agri.exception.Forbidden;
import com.zone.agri.exception.SignInRequiredException;
import com.zone.agri.repository.UserRepository;
import com.zone.agri.security.annotation.RequirePermission;
import com.zone.agri.service.AdminOrderWorkflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AdminOrderWorkflowController {

    private final AdminOrderWorkflowService adminOrderWorkflowService;
    private final UserRepository userRepository;

    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new SignInRequiredException("Vui long dang nhap de tiep tuc");
        }

        return userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new SignInRequiredException("Tai khoan khong ton tai"));
    }

    private void verifyAdminAccess() {
        User user = getCurrentUser();
        if (user.getBranch() != null) {
            throw new Forbidden("Tai khoan nay khong duoc phep thao tac don hang toan he thong.");
        }
    }

    @RequirePermission("ORDER_UPDATE")
    @PutMapping("/admin/{id}/approve-packed-and-ship")
    public ResponseEntity<?> approvePackedAndShip(@PathVariable Long id) {
        verifyAdminAccess();
        adminOrderWorkflowService.approvePackingAndShip(id);
        return ResponseEntity.ok(Map.of(
                "message", "Da duyet dong goi va chuyen don sang dang giao"));
    }
}
