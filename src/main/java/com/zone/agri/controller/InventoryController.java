package com.zone.agri.controller;

import com.zone.agri.dto.inventory.InventoryReceiptRequest;
import com.zone.agri.dto.inventory.InventoryReceiptResponse;
import com.zone.agri.dto.request.inventory.ExportNoteRequest;
import com.zone.agri.dto.response.InventoryNoteResponse;
import com.zone.agri.entity.User;
import com.zone.agri.repository.UserRepository;
import com.zone.agri.service.InventoryService;
import com.zone.agri.service.InventoryNoteService;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:3000")
public class InventoryController {

    private final InventoryService inventoryService;
    private final InventoryNoteService inventoryNoteService;
    private final UserRepository userRepository;

    private boolean isCurrentUserAdmin() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();

        System.out.println("\n========== DEBUG QUYỀN USER ==========");
        System.out.println("1. Email đang đăng nhập: " + email);

        return userRepository.findByEmail(email)
                .map(user -> {
                    if (user.getRole() != null) {
                        System.out.println("2. Role ID trong DB: " + user.getRole().getId());
                        System.out.println("3. Role Slug trong DB: " + user.getRole().getSlug());
                        // System.out.println("4. Tên Role: " + user.getRole().getName()); // Mở cmt nếu entity Role của bạn có trường name/description
                    } else {
                        System.out.println("-> User này KHÔNG CÓ ROLE (Role is null)");
                    }

                    // Logic check cũ của bạn
                    boolean isAdmin = user.getRole() != null &&
                            (user.getRole().getId() == 1L || "ADMIN".equalsIgnoreCase(user.getRole().getSlug()));

                    System.out.println("=> KẾT QUẢ CHECK (Có phải Admin ko?): " + isAdmin);
                    System.out.println("======================================\n");

                    return isAdmin;
                })
                .orElseGet(() -> {
                    System.out.println("-> LỖI: Không tìm thấy user trong DB với email: " + email);
                    System.out.println("======================================\n");
                    return false;
                });
    }

    // ==============================
    // PHIẾU NHẬP KHO (RECEIPTS)
    // ==============================
    @PostMapping("/receipts")
    public ResponseEntity<InventoryReceiptResponse> createReceipt(@RequestBody InventoryReceiptRequest request) {

        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User currentUser = userRepository.findByEmail(email).orElse(null);

        boolean isAdmin = false;
        if (currentUser != null && currentUser.getRole() != null) {
            isAdmin = (currentUser.getRole().getId() == 1L || "ADMIN".equalsIgnoreCase(currentUser.getRole().getSlug()));
        }

        // 2. PHÂN XỬ:
        if (isAdmin) {
            // Admin có thể duyệt tự động
            request.setImportStatus("IMPORTED");
        } else {
            // NẾU LÀ QUẢN LÝ KHO -> Ép trạng thái về PO (Chờ duyệt), không ném lỗi
            request.setImportStatus("PO");
        }

        return ResponseEntity.ok(inventoryService.createReceipt(request));
    }

    @PutMapping("/receipts/{id}")
    public ResponseEntity<InventoryReceiptResponse> updateReceipt(
            @PathVariable Long id,
            @RequestBody InventoryReceiptRequest request) {

        if (!isCurrentUserAdmin()) {
            request.setImportStatus("PO");
        }
        return ResponseEntity.ok(inventoryService.updateReceipt(id, request));
    }

    @DeleteMapping("/receipts/{id}")
    public ResponseEntity<String> deleteReceipt(@PathVariable Long id) {
        inventoryService.deleteReceipt(id);
        return ResponseEntity.ok("Xóa phiếu nhập kho thành công.");
    }

    @GetMapping("/receipts")
    public ResponseEntity<List<InventoryReceiptResponse>> getAllReceipts() {
        return ResponseEntity.ok(inventoryService.getAllReceipts());
    }

    @GetMapping("/receipts/{id}")
    public ResponseEntity<InventoryReceiptResponse> getReceiptDetail(
            @PathVariable Long id) {
        return ResponseEntity.ok(inventoryService.getReceiptById(id));
    }

    // ==============================
    // LỆNH XUẤT KHO (EXPORT)
    // ==============================

    @GetMapping("/export-commands")
    public ResponseEntity<?> getAllExportCommands() {
        return ResponseEntity.ok(inventoryNoteService.getAllExportCommands());
    }

    @GetMapping("/export-receipts")
    public ResponseEntity<?> getAllExportReceipts() {
        return ResponseEntity.ok(inventoryNoteService.getAllExportReceipts());
    }

    @PostMapping("/export-commands")
    public ResponseEntity<?> createExportCommand(@RequestBody ExportNoteRequest request) {
        // 1. Tạo lệnh PENDING
        InventoryNoteResponse response = inventoryNoteService.createExportCommand(request);

        // 2. ADMIN -> Gọi tiếp hàm chốt phiếu để trừ kho luôn
        if (isCurrentUserAdmin()) {
            response = inventoryNoteService.completeExportCommand(response.getId());
        }
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/export-commands/{id}")
    public ResponseEntity<?> deleteExportCommand(@PathVariable Long id) {
        inventoryNoteService.deleteExportCommand(id);
        return ResponseEntity.ok("Xóa lệnh xuất thành công");
    }
    @PostMapping("/export-commands/{id}/complete")
    public ResponseEntity<?> completeExportCommand(@PathVariable Long id) {
        if (!isCurrentUserAdmin()) {
            throw new RuntimeException("CẢNH BÁO: BẠN KHÔNG PHẢI ADMIN, KHÔNG ĐƯỢC PHÉP DUYỆT XUẤT KHO!");
        }
        return ResponseEntity.ok(inventoryNoteService.completeExportCommand(id));
    }
    @GetMapping("/export-commands/{id}")
    public ResponseEntity<InventoryNoteResponse> getExportCommandDetail(@PathVariable Long id) {
        return ResponseEntity.ok(inventoryNoteService.getExportCommandById(id));
    }

    @PutMapping("/export-commands/{id}")
    public ResponseEntity<?> updateExportCommand(
            @PathVariable Long id,
            @RequestBody ExportNoteRequest request) {
        return ResponseEntity.ok(inventoryNoteService.updateExportCommand(id, request));
    }
}