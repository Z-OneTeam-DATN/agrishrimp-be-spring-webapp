package com.zone.agri.controller;

import com.zone.agri.dto.request.inventory.ExportNoteRequest;
import com.zone.agri.service.InventoryNoteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:3000")
public class InventoryController {

    private final InventoryNoteService inventoryNoteService;

    @GetMapping("/export-commands")
    public ResponseEntity<?> getAllExportCommands() {
        try {
            return ResponseEntity.ok(inventoryNoteService.getAllExportCommands());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Lỗi server: " + e.getMessage());
        }
    }

    // 2. TẠO MỚI API: Lấy danh sách PHIẾU ĐÃ XUẤT KHO (COMPLETED)
    @GetMapping("/export-receipts")
    public ResponseEntity<?> getAllExportReceipts() {
        try {
            return ResponseEntity.ok(inventoryNoteService.getAllExportReceipts());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Lỗi server: " + e.getMessage());
        }
    }

    // 3. API Tạo lệnh xuất kho mới
    @PostMapping("/export-commands")
    public ResponseEntity<?> createExportCommand(@RequestBody ExportNoteRequest request) {
        try {
            return ResponseEntity.ok(inventoryNoteService.createExportCommand(request));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Lỗi tạo lệnh xuất: " + e.getMessage());
        }
    }

    @DeleteMapping("/export-commands/{id}")
    public ResponseEntity<?> deleteExportCommand(@PathVariable Long id) {
        try {
            inventoryNoteService.deleteExportCommand(id);
            return ResponseEntity.ok("Xóa lệnh xuất thành công");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Lỗi khi xóa lệnh: " + e.getMessage());
        }
    }
}