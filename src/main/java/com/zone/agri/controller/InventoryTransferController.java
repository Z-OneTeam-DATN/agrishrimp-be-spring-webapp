package com.zone.agri.controller;

import com.zone.agri.dto.request.transfer.TransferQCRequest;
import com.zone.agri.dto.request.transfer.TransferRequest;
import com.zone.agri.dto.request.transfer.TransferSettlementRequest;
import com.zone.agri.dto.response.transfer.TransferDetailResponse;
import com.zone.agri.dto.response.transfer.TransferResponse;
import com.zone.agri.security.annotation.RequirePermission;
import com.zone.agri.service.InventoryTransferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transfers")
@RequiredArgsConstructor
@Tag(name = "Inventory Transfer Management", description = "Quan ly dieu chuyen kho, xuat/nhan hang giua cac chi nhanh")
public class InventoryTransferController {

    private final InventoryTransferService transferService;

    @Operation(summary = "Lay danh sach phieu dieu chuyen")
    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("TRANSFER_VIEW")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lay danh sach thanh cong")
    })
    @GetMapping
    public ResponseEntity<Page<TransferResponse>> getAll(
            @Parameter(description = "Tu khoa tim kiem")
            @RequestParam(required = false) String keyword,
            @Parameter(description = "Trang thai phieu")
            @RequestParam(required = false, defaultValue = "all") String status,
            @Parameter(description = "Chi nhanh")
            @RequestParam(required = false) Long branchId,
            @Parameter(description = "So trang")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "So luong ban ghi tren mot trang")
            @RequestParam(defaultValue = "10") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(transferService.getTransfers(keyword, status, branchId, pageable));
    }

    @Operation(summary = "Lap phieu dieu chuyen moi")
    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("TRANSFER_CREATE")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tao phieu thanh cong", content = @Content(schema = @Schema(implementation = TransferDetailResponse.class))),
            @ApiResponse(responseCode = "400", description = "Du lieu khong hop le")
    })
    @PostMapping
    public ResponseEntity<TransferDetailResponse> createTransfer(@Valid @RequestBody TransferRequest request) {
        Long transferId = transferService.createTransfer(request).getId();
        return ResponseEntity.ok(transferService.getTransferDetail(transferId));
    }

    @Operation(summary = "Cap nhat phieu dieu chuyen truoc duyet")
    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("TRANSFER_UPDATE")
    @PutMapping("/{id}")
    public ResponseEntity<TransferDetailResponse> updateTransfer(
            @PathVariable Long id,
            @Valid @RequestBody TransferRequest request) {
        return ResponseEntity.ok(transferService.updateTransfer(id, request));
    }

    @Operation(summary = "Lay chi tiet phieu dieu chuyen")
    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("TRANSFER_VIEW")
    @GetMapping("/{id}")
    public ResponseEntity<TransferDetailResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(transferService.getTransferDetail(id));
    }

    @Operation(summary = "Chi nhanh nguon xac nhan dieu chuyen noi bo")
    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("TRANSFER_CREATE")
    @PostMapping("/{id}/source-confirm")
    public ResponseEntity<String> sourceConfirm(@PathVariable Long id) {
        transferService.sourceConfirm(id);
        return ResponseEntity.ok("Chi nhanh nguon da xac nhan. Phieu cho nguoi co quyen duyet xu ly.");
    }

    @Operation(summary = "Nguoi co quyen duyet duyet phieu dieu chuyen")
    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("TRANSFER_APPROVE")
    @PostMapping("/{id}/approve")
    public ResponseEntity<String> approveTransfer(@PathVariable Long id) {
        transferService.approveTransfer(id);
        return ResponseEntity.ok("Da duyet phieu dieu chuyen.");
    }

    @Operation(summary = "Xuat kho dieu chuyen")
    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("TRANSFER_APPROVE")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Xuat kho thanh cong"),
            @ApiResponse(responseCode = "400", description = "Loi xuat kho")
    })
    @PostMapping("/{id}/ship")
    public ResponseEntity<String> approveAndShipTransfer(@PathVariable Long id) {
        transferService.approveAndShip(id);
        return ResponseEntity.ok("Da xuat kho. Hang dang tren duong van chuyen.");
    }

    @Operation(summary = "Bat dau kiem hang")
    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("TRANSFER_CREATE")
    @PostMapping("/{id}/start-inspection")
    public ResponseEntity<String> startInspection(@PathVariable Long id) {
        transferService.startInspection(id);
        return ResponseEntity.ok("Da bat dau kiem hang.");
    }

    @Operation(summary = "Hoan thanh nhan hang va QC")
    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("TRANSFER_CREATE")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Nhan hang thanh cong"),
            @ApiResponse(responseCode = "400", description = "Loi nhan hang")
    })
    @PostMapping("/{id}/receive")
    public ResponseEntity<?> receiveTransfer(@PathVariable Long id, @RequestBody List<TransferQCRequest> qcItems) {
        transferService.receiveTransfer(id, qcItems);
        return ResponseEntity.ok("Da xac nhan nhan hang va kiem dem QC thanh cong.");
    }

    @Operation(summary = "Ghi nhan thanh toan noi bo dieu chuyen")
    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("TRANSFER_UPDATE")
    @PostMapping("/{id}/settle-payment")
    public ResponseEntity<TransferDetailResponse> settlePayment(
            @PathVariable Long id,
            @Valid @RequestBody TransferSettlementRequest request) {
        return ResponseEntity.ok(transferService.settleInternalPayment(id, request));
    }

    @Operation(summary = "Tu choi phieu dieu chuyen")
    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("TRANSFER_APPROVE")
    @PostMapping("/{id}/reject")
    public ResponseEntity<String> rejectTransfer(@PathVariable Long id) {
        transferService.rejectTransfer(id);
        return ResponseEntity.ok("Da tu choi phieu dieu chuyen.");
    }

    @Operation(summary = "Huy phieu dieu chuyen")
    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("TRANSFER_CANCEL")
    @PutMapping("/{id}/cancel")
    public ResponseEntity<?> cancelTransfer(@PathVariable Long id) {
        transferService.cancelTransfer(id);
        return ResponseEntity.ok("Da huy phieu thanh cong");
    }

    @Operation(summary = "Doi chi nhanh nhan")
    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("TRANSFER_UPDATE")
    @PutMapping("/{id}/change-destination")
    public ResponseEntity<?> changeDestination(@PathVariable Long id, @RequestParam Long newBranchId) {
        transferService.changeDestination(id, newBranchId);
        return ResponseEntity.ok("Da thay doi chi nhanh nhan");
    }

    @Operation(summary = "Xoa hoan toan phieu dieu chuyen")
    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("TRANSFER_DELETE")
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteTransfer(@PathVariable Long id) {
        transferService.deleteTransfer(id);
        return ResponseEntity.ok("Da xoa phieu dieu chuyen thanh cong!");
    }
}
