package com.zone.agri.controller;

import com.zone.agri.dto.user.MePermissionsResponse;
import com.zone.agri.security.CustomUserDetail;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/me")
@Tag(name = "Me")
public class MeController {

    @Operation(summary = "Lấy danh sách permissions của user hiện tại")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/permissions")
    public ResponseEntity<MePermissionsResponse> getMyPermissions() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        CustomUserDetail principal = (CustomUserDetail) auth.getPrincipal();

        List<String> permissions = auth.getAuthorities().stream()
                .map(a -> a.getAuthority())
                .filter(code -> !code.startsWith("ROLE_"))
                .sorted()
                .toList();

        return ResponseEntity.ok(MePermissionsResponse.builder()
                .userId(principal.getUserDetail().getId())
                .role(principal.getUserDetail().getRole() != null
                        ? principal.getUserDetail().getRole().getSlug()
                        : null)
                .permissions(permissions)
                .build());
    }
}
