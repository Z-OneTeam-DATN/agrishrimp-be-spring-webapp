package com.zone.agri.controller;

import com.zone.agri.dto.inventory.InventoryReceiptRequest;
import com.zone.agri.dto.inventory.InventoryReceiptResponse;
import com.zone.agri.dto.request.inventory.ExportNoteRequest;
import com.zone.agri.dto.response.InventoryNoteResponse;
import com.zone.agri.security.annotation.RequirePermission;
import com.zone.agri.service.InventoryService;
import com.zone.agri.service.InventoryNoteService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
@Tag(name = "Inventory Management", description = "Quản lý phiếu nhập kho và xuất kho")
public class InventoryController {

    private final InventoryService inventoryService;
    private final InventoryNoteService inventoryNoteService;

    /**
     * Kiểm tra user hiện tại có authority cụ thể không.
     * Đọc trực tiếp từ SecurityContext (JWT đã load sẵn) — không query DB.
     */
    private boolean hasAuthority(String authority) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(authority));
    }

    // ==============================
    // PHIẾU NHẬP KHO (RECEIPTS)
    // ==============================

    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("IMPORT_CREATE")
    @PostMapping("/receipts")
    public ResponseEntity<InventoryReceiptResponse> createReceipt(@RequestBody InventoryReceiptRequest request) {
        // Có IMPORT_APPROVE → tự động duyệt luôn (IMPORTED)
        // Không có              → chờ duyệt (PO)
        if (hasAuthority("IMPORT_APPROVE")) {
            request.setImportStatus("IMPORTED");
        } else {
            request.setImportStatus("PO");
        }
        return ResponseEntity.ok(inventoryService.createReceipt(request));
    }

    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("IMPORT_UPDATE")
    @PutMapping("/receipts/{id}")
    public ResponseEntity<InventoryReceiptResponse> updateReceipt(
            @PathVariable Long id,
            @RequestBody InventoryReceiptRequest request) {
        if (!hasAuthority("IMPORT_APPROVE")) {
            request.setImportStatus("PO");
        }
        return ResponseEntity.ok(inventoryService.updateReceipt(id, request));
    }

    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("IMPORT_DELETE")
    @DeleteMapping("/receipts/{id}")
    public ResponseEntity<String> deleteReceipt(@PathVariable Long id) {
        inventoryService.deleteReceipt(id);
        return ResponseEntity.ok("Xóa phiếu nhập kho thành công.");
    }

    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("IMPORT_VIEW")
    @GetMapping("/receipts")
    public ResponseEntity<List<InventoryReceiptResponse>> getAllReceipts() {
        return ResponseEntity.ok(inventoryService.getAllReceipts());
    }

    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("IMPORT_VIEW")
    @GetMapping("/receipts/{id}")
    public ResponseEntity<InventoryReceiptResponse> getReceiptDetail(@PathVariable Long id) {
        return ResponseEntity.ok(inventoryService.getReceiptById(id));
    }

    // ==============================
    // LỆNH XUẤT KHO (EXPORT)
    // ==============================

    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("EXPORT_VIEW")
    @GetMapping("/export-commands")
    public ResponseEntity<?> getAllExportCommands() {
        return ResponseEntity.ok(inventoryNoteService.getAllExportCommands());
    }

    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("EXPORT_VIEW")
    @GetMapping("/export-receipts")
    public ResponseEntity<?> getAllExportReceipts() {
        return ResponseEntity.ok(inventoryNoteService.getAllExportReceipts());
    }

    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("EXPORT_CREATE")
    @PostMapping("/export-commands")
    public ResponseEntity<?> createExportCommand(@RequestBody ExportNoteRequest request) {
        InventoryNoteResponse response = inventoryNoteService.createExportCommand(request);
        // Có EXPORT_APPROVE → tự động chốt phiếu & trừ kho
        if (hasAuthority("EXPORT_APPROVE")) {
            response = inventoryNoteService.completeExportCommand(response.getId());
        }
        return ResponseEntity.ok(response);
    }

    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("EXPORT_DELETE")
    @DeleteMapping("/export-commands/{id}")
    public ResponseEntity<?> deleteExportCommand(@PathVariable Long id) {
        inventoryNoteService.deleteExportCommand(id);
        return ResponseEntity.ok("Xóa lệnh xuất thành công");
    }

    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("EXPORT_APPROVE")
    @PostMapping("/export-commands/{id}/complete")
    public ResponseEntity<?> completeExportCommand(@PathVariable Long id) {
        return ResponseEntity.ok(inventoryNoteService.completeExportCommand(id));
    }

    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("EXPORT_VIEW")
    @GetMapping("/export-commands/{id}")
    public ResponseEntity<InventoryNoteResponse> getExportCommandDetail(@PathVariable Long id) {
        return ResponseEntity.ok(inventoryNoteService.getExportCommandById(id));
    }

    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("EXPORT_UPDATE")
    @PutMapping("/export-commands/{id}")
    public ResponseEntity<?> updateExportCommand(
            @PathVariable Long id,
            @RequestBody ExportNoteRequest request) {
        return ResponseEntity.ok(inventoryNoteService.updateExportCommand(id, request));
    }
}
