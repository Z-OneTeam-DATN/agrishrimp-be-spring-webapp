package com.zone.agri.controller;

import com.zone.agri.common.RoleUtils;
import com.zone.agri.dto.request.returns.*;
import com.zone.agri.dto.response.returns.ReturnOrderDraftResponse;
import com.zone.agri.dto.response.returns.ReturnRequestResponse;
import com.zone.agri.entity.User;
import com.zone.agri.exception.BadRequestException;
import com.zone.agri.exception.SignInRequiredException;
import com.zone.agri.repository.UserRepository;
import com.zone.agri.security.annotation.RequirePermission;
import com.zone.agri.service.ReturnRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Return Request Management", description = "Luồng trả hàng thủ công cho khách, admin và chi nhánh")
@SecurityRequirement(name = "bearerAuth")
public class ReturnRequestController {

    private final ReturnRequestService returnRequestService;
    private final UserRepository userRepository;

    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new SignInRequiredException("Vui lòng đăng nhập để tiếp tục");
        }
        return userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new SignInRequiredException("Tài khoản không tồn tại"));
    }

    private Long getCurrentUserId() {
        return getCurrentUser().getId();
    }

    private Long getCurrentBranchId() {
        User user = getCurrentUser();
        if (user.getBranch() == null) {
            throw new BadRequestException("Tài khoản chưa được gán vào chi nhánh nào");
        }
        return user.getBranch().getId();
    }

    private void verifyAdminAccess() {
        User user = getCurrentUser();
        String roleSlug = user.getRole() != null ? user.getRole().getSlug() : "";
        if (!RoleUtils.isAdminLikeRole(roleSlug)) {
            throw new com.zone.agri.exception.Forbidden("Tài khoản chi nhánh không được phép xem toàn bộ yêu cầu trả hàng. Vui lòng dùng màn chi nhánh.");
        }
    }

    @Operation(summary = "Lấy draft tạo yêu cầu trả hàng từ đơn đã giao")
    @GetMapping("/v1/returns/orders/{orderId}/draft")
    public ResponseEntity<ReturnOrderDraftResponse> getReturnDraft(@PathVariable Long orderId) {
        return ResponseEntity.ok(returnRequestService.getReturnDraft(getCurrentUserId(), orderId));
    }

    @Operation(summary = "Tạo yêu cầu trả hàng thủ công")
    @PostMapping("/v1/returns")
    public ResponseEntity<ReturnRequestResponse> createReturnRequest(
            @Valid @RequestBody CreateReturnRequest request
    ) {
        return ResponseEntity.ok(returnRequestService.createReturnRequest(getCurrentUserId(), request));
    }

    @Operation(summary = "Danh sách yêu cầu trả hàng của tôi")
    @GetMapping("/v1/returns/my")
    public ResponseEntity<List<ReturnRequestResponse>> getMyReturnRequests() {
        return ResponseEntity.ok(returnRequestService.getMyReturnRequests(getCurrentUserId()));
    }

    @Operation(summary = "Chi tiết yêu cầu trả hàng của tôi")
    @GetMapping("/v1/returns/my/{requestId}")
    public ResponseEntity<ReturnRequestResponse> getMyReturnRequestDetail(@PathVariable Long requestId) {
        return ResponseEntity.ok(returnRequestService.getMyReturnRequestDetail(getCurrentUserId(), requestId));
    }

    @Operation(summary = "Danh sách yêu cầu trả hàng toàn hệ thống")
    @RequirePermission("ORDER_VIEW_ALL_BRANCHES")
    @GetMapping("/admin/returns")
    public ResponseEntity<List<ReturnRequestResponse>> getAdminReturnRequests(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search
    ) {
        return ResponseEntity.ok(returnRequestService.getAdminReturnRequests(status, search));
    }

    @Operation(summary = "Chi tiết yêu cầu trả hàng toàn hệ thống")
    @RequirePermission("ORDER_VIEW_ALL_BRANCHES")
    @GetMapping("/admin/returns/{requestId}")
    public ResponseEntity<ReturnRequestResponse> getAdminReturnRequestDetail(@PathVariable Long requestId) {
        return ResponseEntity.ok(returnRequestService.getAdminReturnRequestDetail(requestId));
    }

    @Operation(summary = "Duyệt yêu cầu trả hàng")
    @RequirePermission("ORDER_UPDATE")
    @PutMapping("/admin/returns/{requestId}/approve")
    public ResponseEntity<ReturnRequestResponse> approveReturnRequestForAdmin(
            @PathVariable Long requestId,
            @RequestBody(required = false) ReturnRequestApproveRequest request
    ) {
        verifyAdminAccess();
        return ResponseEntity.ok(returnRequestService.approveForAdmin(requestId, request));
    }

    @Operation(summary = "Từ chối yêu cầu trả hàng")
    @RequirePermission("ORDER_UPDATE")
    @PutMapping("/admin/returns/{requestId}/reject")
    public ResponseEntity<ReturnRequestResponse> rejectReturnRequestForAdmin(
            @PathVariable Long requestId,
            @Valid @RequestBody ReturnRequestRejectRequest request
    ) {
        verifyAdminAccess();
        return ResponseEntity.ok(returnRequestService.rejectForAdmin(requestId, request));
    }

    @Operation(summary = "Xác nhận đã nhận lại hàng")
    @RequirePermission("ORDER_UPDATE")
    @PutMapping("/admin/returns/{requestId}/receive")
    public ResponseEntity<ReturnRequestResponse> receiveReturnRequestForAdmin(
            @PathVariable Long requestId,
            @RequestBody(required = false) ReturnRequestReceiveRequest request
    ) {
        verifyAdminAccess();
        return ResponseEntity.ok(returnRequestService.receiveForAdmin(requestId, request));
    }

    @Operation(summary = "Xác nhận hoàn tiền")
    @RequirePermission("ORDER_UPDATE")
    @PutMapping("/admin/returns/{requestId}/refund")
    public ResponseEntity<ReturnRequestResponse> refundReturnRequestForAdmin(
            @PathVariable Long requestId,
            @Valid @RequestBody ReturnRequestRefundRequest request
    ) {
        verifyAdminAccess();
        return ResponseEntity.ok(returnRequestService.refundForAdmin(requestId, request));
    }

    @Operation(summary = "Danh sách yêu cầu trả hàng của chi nhánh")
    @RequirePermission("ORDER_VIEW")
    @GetMapping("/branch/returns")
    public ResponseEntity<List<ReturnRequestResponse>> getBranchReturnRequests(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search
    ) {
        return ResponseEntity.ok(returnRequestService.getBranchReturnRequests(getCurrentBranchId(), status, search));
    }

    @Operation(summary = "Chi tiết yêu cầu trả hàng của chi nhánh")
    @RequirePermission("ORDER_VIEW")
    @GetMapping("/branch/returns/{requestId}")
    public ResponseEntity<ReturnRequestResponse> getBranchReturnRequestDetail(@PathVariable Long requestId) {
        return ResponseEntity.ok(returnRequestService.getBranchReturnRequestDetail(getCurrentBranchId(), requestId));
    }

    @Operation(summary = "Chi nhánh duyệt yêu cầu trả hàng")
    @RequirePermission("ORDER_UPDATE")
    @PutMapping("/branch/returns/{requestId}/approve")
    public ResponseEntity<ReturnRequestResponse> approveReturnRequestForBranch(
            @PathVariable Long requestId,
            @RequestBody(required = false) ReturnRequestApproveRequest request
    ) {
        return ResponseEntity.ok(returnRequestService.approveForBranch(getCurrentBranchId(), requestId, request));
    }

    @Operation(summary = "Chi nhánh từ chối yêu cầu trả hàng")
    @RequirePermission("ORDER_UPDATE")
    @PutMapping("/branch/returns/{requestId}/reject")
    public ResponseEntity<ReturnRequestResponse> rejectReturnRequestForBranch(
            @PathVariable Long requestId,
            @Valid @RequestBody ReturnRequestRejectRequest request
    ) {
        return ResponseEntity.ok(returnRequestService.rejectForBranch(getCurrentBranchId(), requestId, request));
    }

    @Operation(summary = "Chi nhánh xác nhận đã nhận lại hàng")
    @RequirePermission("ORDER_UPDATE")
    @PutMapping("/branch/returns/{requestId}/receive")
    public ResponseEntity<ReturnRequestResponse> receiveReturnRequestForBranch(
            @PathVariable Long requestId,
            @RequestBody(required = false) ReturnRequestReceiveRequest request
    ) {
        return ResponseEntity.ok(returnRequestService.receiveForBranch(getCurrentBranchId(), requestId, request));
    }

    @Operation(summary = "Chi nhánh xác nhận hoàn tiền")
    @RequirePermission("ORDER_UPDATE")
    @PutMapping("/branch/returns/{requestId}/refund")
    public ResponseEntity<ReturnRequestResponse> refundReturnRequestForBranch(
            @PathVariable Long requestId,
            @Valid @RequestBody ReturnRequestRefundRequest request
    ) {
        return ResponseEntity.ok(returnRequestService.refundForBranch(getCurrentBranchId(), requestId, request));
    }
}
