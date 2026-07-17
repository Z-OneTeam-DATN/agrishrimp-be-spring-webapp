package com.zone.agri.controller;

import com.zone.agri.dto.request.purchase.PurchaseRequestCreateRequest;
import com.zone.agri.dto.response.purchase.PurchaseRequestResponse;
import com.zone.agri.security.annotation.RequirePermission;
import com.zone.agri.service.PurchaseRequestService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/purchase-requests")
@RequiredArgsConstructor
@Tag(name = "Purchase Requests", description = "Purchase requests from suppliers")
@SecurityRequirement(name = "bearerAuth")
public class PurchaseRequestController {

    private final PurchaseRequestService purchaseRequestService;

    @RequirePermission("PURCHASE_REQUEST_VIEW")
    @GetMapping
    public ResponseEntity<List<PurchaseRequestResponse>> getAll() {
        return ResponseEntity.ok(purchaseRequestService.getAllRequests());
    }

    @RequirePermission("PURCHASE_REQUEST_VIEW")
    @GetMapping("/{id}")
    public ResponseEntity<PurchaseRequestResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(purchaseRequestService.getById(id));
    }

    @RequirePermission("PURCHASE_REQUEST_CREATE")
    @PostMapping
    public ResponseEntity<PurchaseRequestResponse> create(
            @Valid @RequestBody PurchaseRequestCreateRequest request) {
        return ResponseEntity.ok(purchaseRequestService.createRequest(request));
    }

    @RequirePermission("PURCHASE_REQUEST_UPDATE")
    @PutMapping("/{id}")
    public ResponseEntity<PurchaseRequestResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody PurchaseRequestCreateRequest request) {
        return ResponseEntity.ok(purchaseRequestService.updateRequest(id, request));
    }

    @RequirePermission("PURCHASE_REQUEST_VIEW")
    @GetMapping("/{id}/remaining-items")
    public ResponseEntity<List<PurchaseRequestResponse.ItemResponse>> getRemainingItems(
            @PathVariable Long id) {
        return ResponseEntity.ok(purchaseRequestService.getRemainingItems(id));
    }

    @RequirePermission("PURCHASE_REQUEST_CREATE")
    @PostMapping("/{id}/submit")
    public ResponseEntity<PurchaseRequestResponse> submit(@PathVariable Long id) {
        return ResponseEntity.ok(purchaseRequestService.submit(id));
    }

    @RequirePermission("PURCHASE_REQUEST_APPROVE")
    @PostMapping("/{id}/approve")
    public ResponseEntity<PurchaseRequestResponse> approve(@PathVariable Long id) {
        return ResponseEntity.ok(purchaseRequestService.approve(id));
    }

    @RequirePermission("PURCHASE_REQUEST_APPROVE")
    @PostMapping("/{id}/reject")
    public ResponseEntity<PurchaseRequestResponse> reject(@PathVariable Long id) {
        return ResponseEntity.ok(purchaseRequestService.reject(id));
    }

    @RequirePermission("PURCHASE_REQUEST_UPDATE")
    @PostMapping("/{id}/send-to-supplier")
    public ResponseEntity<PurchaseRequestResponse> sendToSupplier(@PathVariable Long id) {
        return ResponseEntity.ok(purchaseRequestService.sendToSupplier(id));
    }

    @RequirePermission("PURCHASE_REQUEST_UPDATE")
    @PostMapping("/{id}/resend-to-supplier")
    public ResponseEntity<PurchaseRequestResponse> resendToSupplier(@PathVariable Long id) {
        return ResponseEntity.ok(purchaseRequestService.resendToSupplier(id));
    }

    @RequirePermission("PURCHASE_REQUEST_UPDATE")
    @PostMapping("/{id}/confirm-supplier")
    public ResponseEntity<PurchaseRequestResponse> confirmSupplier(@PathVariable Long id) {
        return ResponseEntity.ok(purchaseRequestService.confirmSupplier(id));
    }

    @RequirePermission("PURCHASE_REQUEST_UPDATE")
    @PostMapping("/{id}/mark-delivering")
    public ResponseEntity<PurchaseRequestResponse> markDelivering(@PathVariable Long id) {
        return ResponseEntity.ok(purchaseRequestService.markDelivering(id));
    }

    @RequirePermission("PURCHASE_REQUEST_UPDATE")
    @PostMapping("/{id}/cancel")
    public ResponseEntity<PurchaseRequestResponse> cancel(@PathVariable Long id) {
        return ResponseEntity.ok(purchaseRequestService.cancel(id));
    }

    @RequirePermission("PURCHASE_REQUEST_APPROVE")
    @PostMapping("/{id}/close")
    public ResponseEntity<PurchaseRequestResponse> close(@PathVariable Long id) {
        return ResponseEntity.ok(purchaseRequestService.close(id));
    }
}
