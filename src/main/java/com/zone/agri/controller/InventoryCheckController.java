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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/inventory-checks")
@RequiredArgsConstructor
@Tag(name = "Inventory Check Management", description = "API Kiểm kê kho theo yêu cầu Frontend")
public class InventoryCheckController {

    private final InventoryNoteService inventoryNoteService;
    private final InventoryService inventoryService;

    private boolean hasAuthority(String authority) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(authority));
    }

    /**
     * A. API Tạo/Cập nhật phiếu (POST /api/inventory-checks)
     * Nếu có ID trong request thì cập nhật, ngược lại tạo mới.
     */
    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("CHECK_CREATE")
    @PostMapping
    public ResponseEntity<InventoryNoteResponse> saveOrUpdate(@Valid @RequestBody CheckNoteRequest request) {
        InventoryNoteResponse response;
        if (request.getId() != null) {
            response = inventoryNoteService.updateCheckCommand(request.getId(), request);
        } else {
            response = inventoryNoteService.createCheckCommand(request);
        }
        
        if (hasAuthority("CHECK_APPROVE")) {
            response = inventoryNoteService.completeCheckCommand(response.getId());
        }
        return ResponseEntity.ok(response);
    }

    /**
     * Cập nhật phiếu (PUT /inventory-checks/{id})
     */
    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("CHECK_UPDATE")
    @PutMapping("/{id}")
    public ResponseEntity<InventoryNoteResponse> update(@PathVariable Long id, @Valid @RequestBody CheckNoteRequest request) {
        return ResponseEntity.ok(inventoryNoteService.updateCheckCommand(id, request));
    }

    /**
     * C. API Danh sách (GET /inventory-checks)
     */
    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("CHECK_VIEW")
    @GetMapping
    public ResponseEntity<List<InventoryNoteResponse>> getAll() {
        return ResponseEntity.ok(inventoryNoteService.getAllCheckNotes());
    }

    /**
     * B. API Lấy chi tiết (GET /inventory-checks/{code})
     * Hỗ trợ tìm theo Code (PKK-XXXX) hoặc ID (số)
     */
    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("CHECK_VIEW")
    @GetMapping("/{codeOrId}")
    public ResponseEntity<InventoryNoteResponse> getByCodeOrId(@PathVariable String codeOrId) {
        if (codeOrId.matches("^\\d+$")) {
            return ResponseEntity.ok(inventoryNoteService.getCheckCommandById(Long.parseLong(codeOrId)));
        }
        return ResponseEntity.ok(inventoryNoteService.getCheckCommandByCode(codeOrId));
    }

    /**
     * Chốt phiếu kiểm kê
     */
    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("CHECK_APPROVE")
    @PostMapping("/{id}/complete")
    public ResponseEntity<InventoryNoteResponse> complete(@PathVariable Long id) {
        return ResponseEntity.ok(inventoryNoteService.completeCheckCommand(id));
    }

    /**
     * Xóa phiếu kiểm kê (chỉ khi trạng thái là PENDING)
     */
    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("CHECK_DELETE")
    @DeleteMapping("/{id}")
    public ResponseEntity<String> delete(@PathVariable Long id) {
        inventoryNoteService.deleteCheckNote(id);
        return ResponseEntity.ok("Xóa phiếu kiểm kê thành công");
    }

    /**
     * Tìm kiếm sản phẩm để kiểm kho
     */
    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("CHECK_CREATE")
    @GetMapping("/search-products")
    public ResponseEntity<List<InventorySearchResponse>> searchProducts(@RequestParam(required = false) String keyword) {
        return ResponseEntity.ok(inventoryService.searchInventoryForCheck(keyword));
    }
}
