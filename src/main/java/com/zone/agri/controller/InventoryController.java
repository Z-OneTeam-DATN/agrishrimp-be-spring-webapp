package com.zone.agri.controller;

import com.zone.agri.dto.inventory.InventoryReceiptRequest;
import com.zone.agri.dto.inventory.InventoryReceiptResponse;
import com.zone.agri.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    // TẠO PHIẾU
    @PostMapping("/receipts")
    public ResponseEntity<InventoryReceiptResponse> createReceipt(@RequestBody InventoryReceiptRequest request) {
        return ResponseEntity.ok(inventoryService.createReceipt(request));
    }

    // SỬA PHIẾU (Chỉ khi trạng thái PENDING)
    @PutMapping("/receipts/{id}")
    public ResponseEntity<InventoryReceiptResponse> updateReceipt(
            @PathVariable Long id,
            @RequestBody InventoryReceiptRequest request) {
        return ResponseEntity.ok(inventoryService.updateReceipt(id, request));
    }

    // XÓA PHIẾU (Xóa bản ghi và hoàn tồn kho nếu cần)
    @DeleteMapping("/receipts/{id}")
    public ResponseEntity<String> deleteReceipt(@PathVariable Long id) {
        inventoryService.deleteReceipt(id);
        return ResponseEntity.ok("Xóa phiếu nhập kho thành công.");
    }

    // LẤY DANH SÁCH PHIẾU NHẬP
    @GetMapping("/receipts")
    public ResponseEntity<List<InventoryReceiptResponse>> getAllReceipts() {
        return ResponseEntity.ok(inventoryService.getAllReceipts());
    }

    // XEM CHI TIẾT PHIẾU NHẬP
    @GetMapping("/receipts/{id}")
    public ResponseEntity<InventoryReceiptResponse> getReceiptDetail(@PathVariable Long id) {
        return ResponseEntity.ok(inventoryService.getReceiptById(id));
    }
}