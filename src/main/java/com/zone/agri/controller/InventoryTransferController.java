package com.zone.agri.controller;

import com.zone.agri.dto.transfer.TransferDetailResponse;
import com.zone.agri.dto.transfer.TransferItemRequest;
import com.zone.agri.dto.transfer.TransferRequest;
import com.zone.agri.dto.transfer.TransferResponse;
import com.zone.agri.entity.InventoryTransfer;
import com.zone.agri.service.InventoryTransferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/transfers")
@RequiredArgsConstructor
@Tag(name = "Inventory Transfer Management", description = "Quản lý điều chuyển kho, xuất/nhận hàng giữa các chi nhánh")
@CrossOrigin(origins = "http://localhost:3000")
public class InventoryTransferController {

    private final InventoryTransferService transferService;

    @Operation(summary = "Lấy danh sách phiếu điều chuyển", description = "Trả về danh sách phiếu điều chuyển có hỗ trợ phân trang, tìm kiếm và lọc theo trạng thái.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lấy danh sách thành công")
    })
    @GetMapping
    public ResponseEntity<Page<TransferResponse>> getAll(
            @Parameter(description = "Từ khóa tìm kiếm (mã phiếu, tài xế...)", example = "PDC-0001")
            @RequestParam(required = false) String keyword,
            @Parameter(description = "Trạng thái phiếu (PENDING, SHIPPING, COMPLETED, CANCELLED, all)", example = "all")
            @RequestParam(required = false, defaultValue = "all") String status,
            @Parameter(description = "Số trang (bắt đầu từ 0)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Số lượng bản ghi trên một trang", example = "10")
            @RequestParam(defaultValue = "10") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(transferService.getTransfers(keyword, status, pageable));
    }

    @Operation(summary = "Lập phiếu điều chuyển mới", description = "Tạo một phiếu điều chuyển hàng hóa mới. Trạng thái mặc định là PENDING (Chờ xuất).")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tạo phiếu thành công", content = @Content(schema = @Schema(implementation = InventoryTransfer.class))),
            @ApiResponse(responseCode = "400", description = "Dữ liệu không hợp lệ")
    })
    @PostMapping
    public ResponseEntity<InventoryTransfer> createTransfer(@RequestBody TransferRequest request) {
        return ResponseEntity.ok(transferService.createTransfer(request));
    }

    @Operation(summary = "Lấy chi tiết phiếu điều chuyển", description = "Trả về thông tin chi tiết và danh sách sản phẩm của một phiếu.")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable Long id) {
        try {
            InventoryTransfer t = transferService.getById(id);

            TransferDetailResponse res = TransferDetailResponse.builder()
                    .id(t.getId())
                    .transferCode(t.getTransferCode())
                    .transferType(t.getTransferType())
                    .status(t.getStatus())
                    .description(t.getDescription())
                    .vehicle(t.getVehicle())
                    .transporter(t.getTransporter())
                    .dispatchOrder(t.getDispatchOrder())
                    .referenceCode(t.getReferenceCode())
                    .createdAt(t.getCreatedAt())
                    .fromBranchName(t.getFromBranch() != null ? t.getFromBranch().getName() : "Không xác định")
                    .toBranchName(t.getToBranch() != null ? t.getToBranch().getName() : "Không xác định")
                    .totalQuantity(t.getTotalQuantity())
                    .totalValue(t.getTotalValue())
                    .items(t.getDetails() == null ? java.util.Collections.emptyList() :
                            t.getDetails().stream().map(d -> {
                                // Xử lý an toàn chống NullPointerException
                                String pName = "Chưa có tên";
                                String sku = "No-SKU";
                                String unit = "-";
                                Long vId = null;

                                if (d.getProductVariant() != null) {
                                    vId = d.getProductVariant().getId();
                                    sku = d.getProductVariant().getSku();
                                    unit = "";
                                    if (d.getProductVariant().getProduct() != null) {
                                        pName = d.getProductVariant().getProduct().getName();
                                    }
                                }

                                return TransferDetailResponse.ItemDetail.builder()
                                        .variantId(vId)
                                        .productName(pName)
                                        .sku(sku)
                                        .unit(unit)
                                        .quantityRequested(d.getQuantityRequested())
                                        .quantityReal(d.getQuantityReal())
                                        .note(d.getNote())
                                        .build();
                            }).toList())
                    .build();

            return ResponseEntity.ok(res);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Lỗi Backend: " + e.getMessage());
        }
    }

    @Operation(summary = "Xác nhận Xuất kho", description = "Duyệt phiếu và chuyển trạng thái sang SHIPPING (Đang vận chuyển), đồng thời trừ số lượng tồn kho tại chi nhánh xuất.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Xuất kho thành công"),
            @ApiResponse(responseCode = "400", description = "Lỗi xuất kho (kho không đủ hàng, sai trạng thái...)")
    })
    @PutMapping("/{id}/ship")
    public ResponseEntity<String> approveAndShipTransfer(
            @Parameter(description = "ID của phiếu điều chuyển", example = "1", required = true)
            @PathVariable Long id) {
        transferService.approveAndShip(id);
        return ResponseEntity.ok("Đã xuất kho và đang vận chuyển!");
    }

    @Operation(summary = "Xác nhận Nhận hàng", description = "Chi nhánh nhận xác nhận số lượng thực tế nhận được. Chuyển trạng thái sang COMPLETED, cộng tồn kho chi nhánh nhận, xử lý chênh lệch.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Nhận hàng thành công"),
            @ApiResponse(responseCode = "400", description = "Lỗi nhận hàng (phiếu không ở trạng thái đang chuyển, sai dữ liệu...)")
    })
    @PutMapping("/{id}/receive")
    public ResponseEntity<?> receiveTransfer(@PathVariable Long id, @RequestBody List<Map<String, Object>> receivedItems) {
        // Trong Service:
        // - Lấy quantityReal cập nhật vào phiếu.
        // - Cộng tồn kho cho chi nhánh nhận (toBranch) dựa trên quantityReal.
        // - Đổi status = COMPLETED.
        transferService.receiveTransfer(id, receivedItems);
        return ResponseEntity.ok("Đã xác nhận nhận hàng");
    }

    // 1. API Hủy phiếu
    @PutMapping("/{id}/cancel")
    public ResponseEntity<?> cancelTransfer(@PathVariable Long id) {
        // Trong Service: Chuyển status = CANCELLED.
        // Nếu phiếu đang SHIPPING (đã trừ kho xuất), thì phải CỘNG LẠI tồn kho cho kho xuất.
        transferService.cancelTransfer(id);
        return ResponseEntity.ok("Đã hủy phiếu thành công");
    }

    // 2. API Đổi chi nhánh nhận
    @PutMapping("/{id}/change-destination")
    public ResponseEntity<?> changeDestination(@PathVariable Long id, @RequestParam Long newBranchId) {
        // Trong Service: Cập nhật toBranch = newBranchId.
        transferService.changeDestination(id, newBranchId);
        return ResponseEntity.ok("Đã thay đổi chi nhánh nhận");
    }



}