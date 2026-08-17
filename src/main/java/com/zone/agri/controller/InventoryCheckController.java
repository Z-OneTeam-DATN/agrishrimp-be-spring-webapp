package com.zone.agri.controller;

import com.zone.agri.dto.request.inventory.CheckNoteRequest;
import com.zone.agri.dto.response.inventory.InventoryNoteResponse;
import com.zone.agri.dto.response.inventory.InventorySearchResponse;
import com.zone.agri.security.annotation.RequirePermission;
import com.zone.agri.service.InventoryNoteService;
import com.zone.agri.service.InventoryService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.List;

@RestController
@RequestMapping("/api/inventory-checks")
@RequiredArgsConstructor
@Tag(name = "Inventory Check Management", description = "API Kiểm kê kho theo yêu cầu Frontend")
public class InventoryCheckController {

    private final InventoryNoteService inventoryNoteService;
    private final InventoryService inventoryService;

    /**
     * A. API Tạo/Cập nhật phiếu (POST /api/inventory-checks)
     * Nếu có ID trong request thì cập nhật, ngược lại tạo mới.
     */
    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission({"INVENTORY_CHECK_CREATE", "INVENTORY_CHECK_UPDATE"})
    @PostMapping
    public ResponseEntity<InventoryNoteResponse> saveOrUpdate(@Valid @RequestBody CheckNoteRequest request) {
        if (request.getId() != null) {
            return ResponseEntity.ok(inventoryNoteService.updateCheckCommand(request.getId(), request));
        }
        return ResponseEntity.ok(inventoryNoteService.createCheckCommand(request));
    }

    /**
     * Cập nhật phiếu (PUT /inventory-checks/{id})
     */
    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("INVENTORY_CHECK_UPDATE")
    @PutMapping("/{id}")
    public ResponseEntity<InventoryNoteResponse> update(@PathVariable Long id, @Valid @RequestBody CheckNoteRequest request) {
        return ResponseEntity.ok(inventoryNoteService.updateCheckCommand(id, request));
    }

    /**
     * C. API Danh sách (GET /inventory-checks)
     */
    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission({
            "INVENTORY_CHECK_VIEW",
            "INVENTORY_CHECK_CREATE",
            "INVENTORY_CHECK_UPDATE",
            "INVENTORY_CHECK_APPROVE",
            "INVENTORY_CHECK_CANCEL",
            "INVENTORY_CHECK_DELETE"
    })
    @GetMapping
    public ResponseEntity<List<InventoryNoteResponse>> getAll(
            @RequestParam(required = false) Long branchId) {
        Long finalBranchId = com.zone.agri.common.AuthUtils.resolveRequestedOrUserBranch(branchId, "INVENTORY_CHECK_VIEW");
        return ResponseEntity.ok(inventoryNoteService.getAllCheckNotes(finalBranchId));
    }

    /**
     * B. API Lấy chi tiết (GET /inventory-checks/{code})
     * Hỗ trợ tìm theo Code (PKK-XXXX) hoặc ID (số)
     */
    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission({
            "INVENTORY_CHECK_VIEW",
            "INVENTORY_CHECK_CREATE",
            "INVENTORY_CHECK_UPDATE",
            "INVENTORY_CHECK_APPROVE",
            "INVENTORY_CHECK_CANCEL",
            "INVENTORY_CHECK_DELETE"
    })
    @GetMapping("/{codeOrId}")
    public ResponseEntity<InventoryNoteResponse> getByCodeOrId(@PathVariable String codeOrId) {
        if (codeOrId.matches("^\\d+$")) {
            return ResponseEntity.ok(inventoryNoteService.getCheckCommandById(Long.parseLong(codeOrId)));
        }
        return ResponseEntity.ok(inventoryNoteService.getCheckCommandByCode(codeOrId));
    }

    /**
     * Gửi phiếu kiểm kê sang bước chờ duyệt cân bằng
     */
    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("INVENTORY_CHECK_UPDATE")
    @PostMapping("/{id}/start")
    public ResponseEntity<InventoryNoteResponse> startCheck(@PathVariable Long id) {
        return ResponseEntity.ok(inventoryNoteService.startCheckCommand(id));
    }

    /**
     * Gửi phiếu kiểm kê sang bước chờ duyệt cân bằng
     */
    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("INVENTORY_CHECK_UPDATE")
    @PostMapping("/{id}/submit-for-approval")
    public ResponseEntity<InventoryNoteResponse> submitForApproval(@PathVariable Long id) {
        return ResponseEntity.ok(inventoryNoteService.submitCheckForApproval(id));
    }

    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("INVENTORY_CHECK_APPROVE")
    @PostMapping("/{id}/request-recount")
    public ResponseEntity<InventoryNoteResponse> requestRecount(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> payload
    ) {
        return ResponseEntity.ok(inventoryNoteService.requestCheckRecount(id, payload != null ? payload.get("reason") : null));
    }

    /**
     * Admin duyệt cân bằng tồn kho theo số thực tế
     */
    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("INVENTORY_CHECK_APPROVE")
    @PostMapping("/{id}/approve-adjustment")
    public ResponseEntity<InventoryNoteResponse> approveAdjustment(@PathVariable Long id) {
        return ResponseEntity.ok(inventoryNoteService.approveCheckAdjustment(id));
    }

    /**
     * Xóa phiếu kiểm kê (chỉ khi trạng thái là PENDING)
     */
    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("INVENTORY_CHECK_CANCEL")
    @PostMapping("/{id}/cancel")
    public ResponseEntity<InventoryNoteResponse> cancelCheck(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> payload
    ) {
        return ResponseEntity.ok(inventoryNoteService.cancelCheck(id, payload != null ? payload.get("reason") : null));
    }

    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("INVENTORY_CHECK_DELETE")
    @DeleteMapping("/{id}")
    public ResponseEntity<String> delete(@PathVariable Long id) {
        inventoryNoteService.deleteCheckNote(id);
        return ResponseEntity.ok("Xóa phiếu kiểm kê thành công");
    }

    /**
     * Tìm kiếm sản phẩm để kiểm kho
     */
    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission({"INVENTORY_CHECK_CREATE", "INVENTORY_CHECK_UPDATE"})
    @GetMapping("/search-products")
    public ResponseEntity<List<InventorySearchResponse>> searchProducts(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long branchId) {
        return ResponseEntity.ok(inventoryService.searchInventoryForCheck(keyword, branchId));
    }
}
