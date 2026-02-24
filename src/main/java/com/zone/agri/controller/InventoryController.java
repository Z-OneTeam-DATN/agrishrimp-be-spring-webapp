package com.zone.agri.controller;

import com.zone.agri.dto.inventory.InventoryReceiptRequest;
import com.zone.agri.dto.inventory.InventoryReceiptResponse;
import com.zone.agri.dto.request.inventory.ExportNoteRequest;
import com.zone.agri.service.InventoryService;
import com.zone.agri.service.InventoryNoteService;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:3000")
public class InventoryController {

    private final InventoryService inventoryService;
    private final InventoryNoteService inventoryNoteService;

    // ==============================
    // PHIẾU NHẬP KHO (RECEIPTS)
    // ==============================

    @PostMapping("/receipts")
    @com.zone.agri.security.annotation.RequirePermission("IMPORT_MANAGE")
    public ResponseEntity<InventoryReceiptResponse> createReceipt(
            @RequestBody InventoryReceiptRequest request) {
        return ResponseEntity.ok(inventoryService.createReceipt(request));
    }

    @PutMapping("/receipts/{id}")
    @com.zone.agri.security.annotation.RequirePermission("IMPORT_MANAGE")
    public ResponseEntity<InventoryReceiptResponse> updateReceipt(
            @PathVariable Long id,
            @RequestBody InventoryReceiptRequest request) {
        return ResponseEntity.ok(inventoryService.updateReceipt(id, request));
    }

    @DeleteMapping("/receipts/{id}")
    @com.zone.agri.security.annotation.RequirePermission("IMPORT_MANAGE")
    public ResponseEntity<String> deleteReceipt(@PathVariable Long id) {
        inventoryService.deleteReceipt(id);
        return ResponseEntity.ok("Xóa phiếu nhập kho thành công.");
    }

    @GetMapping("/receipts")
    @com.zone.agri.security.annotation.RequirePermission({"IMPORT_MANAGE", "INVENTORY_VIEW"})
    public ResponseEntity<List<InventoryReceiptResponse>> getAllReceipts() {
        return ResponseEntity.ok(inventoryService.getAllReceipts());
    }

    @GetMapping("/receipts/{id}")
    @com.zone.agri.security.annotation.RequirePermission({"IMPORT_MANAGE", "INVENTORY_VIEW"})
    public ResponseEntity<InventoryReceiptResponse> getReceiptDetail(
            @PathVariable Long id) {
        return ResponseEntity.ok(inventoryService.getReceiptById(id));
    }

    // ==============================
    // LỆNH XUẤT KHO (EXPORT)
    // ==============================

    @GetMapping("/export-commands")
    @com.zone.agri.security.annotation.RequirePermission({"EXPORT_MANAGE", "INVENTORY_VIEW"})
    public ResponseEntity<?> getAllExportCommands() {
        return ResponseEntity.ok(inventoryNoteService.getAllExportCommands());
    }

    @GetMapping("/export-receipts")
    @com.zone.agri.security.annotation.RequirePermission({"EXPORT_MANAGE", "INVENTORY_VIEW"})
    public ResponseEntity<?> getAllExportReceipts() {
        return ResponseEntity.ok(inventoryNoteService.getAllExportReceipts());
    }

    @PostMapping("/export-commands")
    @com.zone.agri.security.annotation.RequirePermission("EXPORT_MANAGE")
    public ResponseEntity<?> createExportCommand(
            @RequestBody ExportNoteRequest request) {
        return ResponseEntity.ok(inventoryNoteService.createExportCommand(request));
    }

    @DeleteMapping("/export-commands/{id}")
    @com.zone.agri.security.annotation.RequirePermission("EXPORT_MANAGE")
    public ResponseEntity<?> deleteExportCommand(@PathVariable Long id) {
        inventoryNoteService.deleteExportCommand(id);
        return ResponseEntity.ok("Xóa lệnh xuất thành công");
    }
}