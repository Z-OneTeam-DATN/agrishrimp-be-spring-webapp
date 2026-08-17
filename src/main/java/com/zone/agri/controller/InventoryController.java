package com.zone.agri.controller;

import com.zone.agri.dto.request.inventory.CheckNoteRequest;
import com.zone.agri.dto.request.inventory.InventoryQCRequest;
import com.zone.agri.dto.request.inventory.InventoryReceiptRequest;
import com.zone.agri.dto.request.inventory.ReceiptPaymentRequest;
import com.zone.agri.dto.response.inventory.InventoryReceiptResponse;
import com.zone.agri.dto.request.inventory.ExportNoteRequest;
import com.zone.agri.dto.response.inventory.InventoryNoteResponse;
import com.zone.agri.dto.response.inventory.ReceiptPaymentResponse;
import com.zone.agri.dto.response.inventory.InventorySearchResponse;
import com.zone.agri.security.annotation.RequirePermission;
import com.zone.agri.service.InventoryReceiptPaymentService;
import com.zone.agri.service.InventoryService;
import com.zone.agri.service.InventoryNoteService;
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
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
@Tag(name = "Inventory Management", description = "Quản lý phiếu nhập kho, xuất kho và kiểm kho")
public class InventoryController {

    private final InventoryService inventoryService;
    private final InventoryNoteService inventoryNoteService;
    private final InventoryReceiptPaymentService inventoryReceiptPaymentService;

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
    public ResponseEntity<InventoryReceiptResponse> createReceipt(@Valid @RequestBody InventoryReceiptRequest request) {
        if (hasAuthority("IMPORT_APPROVE")) {
            request.setImportStatus("IMPORTED");
        } else {
            request.setImportStatus("PO");
        }
        return ResponseEntity.ok(inventoryService.createReceipt(request));
    }

    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("IMPORT_APPROVE")
    @PostMapping("/receipts/{id}/approve")
    public ResponseEntity<InventoryReceiptResponse> approveReceipt(@PathVariable Long id) {
        return ResponseEntity.ok(inventoryService.approveReceipt(id));
    }

    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("IMPORT_APPROVE")
    @PostMapping("/receipts/{id}/reject")
    public ResponseEntity<InventoryReceiptResponse> rejectReceipt(@PathVariable Long id) {
        return ResponseEntity.ok(inventoryService.rejectReceipt(id));
    }

    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("IMPORT_APPROVE")
    @PostMapping("/receipts/{id}/complete")
    public ResponseEntity<InventoryReceiptResponse> completeReceipt(
            @PathVariable Long id,
            @Valid @RequestBody InventoryQCRequest qcRequest) {
        return ResponseEntity.ok(inventoryService.completeReceipt(id, qcRequest));
    }

    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("IMPORT_UPDATE")
    @PutMapping("/receipts/{id}")
    public ResponseEntity<InventoryReceiptResponse> updateReceipt(
            @PathVariable Long id,
            @Valid @RequestBody InventoryReceiptRequest request) {
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
    public ResponseEntity<List<InventoryReceiptResponse>> getAllReceipts(
            @RequestParam(required = false) Long branchId) {
        return ResponseEntity.ok(inventoryService.getAllReceipts(branchId));
    }

    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("IMPORT_VIEW")
    @GetMapping("/receipts/{id}")
    public ResponseEntity<InventoryReceiptResponse> getReceiptDetail(@PathVariable Long id) {
        return ResponseEntity.ok(inventoryService.getReceiptById(id));
    }

    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("IMPORT_VIEW")
    @GetMapping("/receipts/{id}/payments")
    public ResponseEntity<List<ReceiptPaymentResponse>> getReceiptPayments(@PathVariable Long id) {
        return ResponseEntity.ok(inventoryReceiptPaymentService.getReceiptPayments(id));
    }

    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("IMPORT_APPROVE")
    @PostMapping("/receipts/{id}/payments")
    public ResponseEntity<ReceiptPaymentResponse> createReceiptPayment(
            @PathVariable Long id,
            @Valid @RequestBody ReceiptPaymentRequest request) {
        return ResponseEntity.ok(inventoryReceiptPaymentService.createReceiptPayment(id, request));
    }

    /**
     * Quy trình 3 – Hướng 1 (Nhanh):
     * Tạo Phiếu xuất trả NCC trực tiếp từ Phiếu nhập kho đã duyệt (COMPLETED).
     * Hệ thống tự điền NCC + danh sách hàng lỗi, khóa cứng đơn giá = đơn giá nhập.
     */
    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("EXPORT_CREATE")
    @PostMapping("/receipts/{id}/create-return")
    public ResponseEntity<InventoryNoteResponse> createReturnFromGR(@PathVariable Long id) {
        return ResponseEntity.ok(inventoryNoteService.createReturnFromGR(id));
    }

    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("EXPORT_CREATE")
    @GetMapping("/defective-items")
    public ResponseEntity<List<com.zone.agri.dto.response.inventory.InventorySearchResponse>> getDefectiveItemsBySupplier(
            @RequestParam Long supplierId,
            @RequestParam(required = false) Long branchId) {
        return ResponseEntity.ok(inventoryService.getDefectiveItemsBySupplier(supplierId, branchId));
    }

    // ==============================
    // LỆNH XUẤT KHO (EXPORT)
    // ==============================

    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("EXPORT_VIEW")
    @GetMapping("/export-commands")
    public ResponseEntity<?> getAllExportCommands(
            @RequestParam(required = false) Long branchId) {
        return ResponseEntity.ok(inventoryNoteService.getAllExportCommands(branchId));
    }

    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("EXPORT_VIEW")
    @GetMapping("/export-receipts")
    public ResponseEntity<?> getAllExportReceipts(
            @RequestParam(required = false) Long branchId) {
        return ResponseEntity.ok(inventoryNoteService.getAllExportReceipts(branchId));
    }

    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("EXPORT_CREATE")
    @PostMapping("/export-commands")
    public ResponseEntity<?> createExportCommand(@Valid @RequestBody ExportNoteRequest request) {
        InventoryNoteResponse response = inventoryNoteService.createExportCommand(request);
        return ResponseEntity.ok(response);
    }

    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("EXPORT_APPROVE")
    @PostMapping("/export-commands/{id}/approve")
    public ResponseEntity<?> approveExportCommand(@PathVariable Long id) {
        return ResponseEntity.ok(inventoryNoteService.approveExportCommand(id));
    }

    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("EXPORT_APPROVE")
    @PostMapping("/export-commands/{id}/complete")
    public ResponseEntity<?> completeExportCommand(@PathVariable Long id) {
        return ResponseEntity.ok(inventoryNoteService.completeExportCommand(id));
    }

    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("EXPORT_DELETE")
    @DeleteMapping("/export-commands/{id}")
    public ResponseEntity<?> deleteExportCommand(@PathVariable Long id) {
        inventoryNoteService.deleteExportCommand(id);
        return ResponseEntity.ok("Xóa lệnh xuất thành công");
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
            @Valid @RequestBody ExportNoteRequest request) {
        return ResponseEntity.ok(inventoryNoteService.updateExportCommand(id, request));
    }
}
