package com.zone.agri.controller;

import com.zone.agri.dto.stock.StockRequestApproveDto;
import com.zone.agri.dto.stock.StockRequestCreateDto;
import com.zone.agri.dto.stock.StockRequestResponse;
import com.zone.agri.security.annotation.RequirePermission;
import com.zone.agri.service.StockRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/stock-requests")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:3000")
public class StockRequestController {

    private final StockRequestService stockRequestService;

    @PostMapping
    @RequirePermission("STOCK_REQUEST_CREATE")
    public ResponseEntity<StockRequestResponse> createRequest(@RequestBody StockRequestCreateDto dto) {
        return ResponseEntity.ok(stockRequestService.createRequest(dto));
    }

    @GetMapping
    @RequirePermission({"STOCK_REQUEST_CREATE", "STOCK_REQUEST_APPROVE"})
    public ResponseEntity<List<StockRequestResponse>> getAll() {
        return ResponseEntity.ok(stockRequestService.getAll());
    }

    @GetMapping("/{id}")
    @RequirePermission({"STOCK_REQUEST_CREATE", "STOCK_REQUEST_APPROVE"})
    public ResponseEntity<StockRequestResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(stockRequestService.getById(id));
    }

    @PutMapping("/{id}/approve")
    @RequirePermission("STOCK_REQUEST_APPROVE")
    public ResponseEntity<StockRequestResponse> approve(@PathVariable Long id, @RequestBody StockRequestApproveDto dto) {
        return ResponseEntity.ok(stockRequestService.approveRequest(id, dto));
    }

    @PutMapping("/{id}/reject")
    @RequirePermission("STOCK_REQUEST_APPROVE")
    public ResponseEntity<StockRequestResponse> reject(@PathVariable Long id, @RequestBody String reason) {
        return ResponseEntity.ok(stockRequestService.rejectRequest(id, reason));
    }

    @DeleteMapping("/{id}")
    @RequirePermission("STOCK_REQUEST_CREATE")
    public ResponseEntity<StockRequestResponse> cancel(@PathVariable Long id) {
        return ResponseEntity.ok(stockRequestService.cancelRequest(id));
    }
}
