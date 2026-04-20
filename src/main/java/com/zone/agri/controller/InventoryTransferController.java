package com.zone.agri.controller;

import com.zone.agri.dto.request.transfer.TransferQCRequest;
import com.zone.agri.dto.request.transfer.TransferRequest;
import com.zone.agri.dto.response.transfer.TransferDetailResponse;
import com.zone.agri.dto.response.transfer.TransferResponse;
import com.zone.agri.entity.InventoryTransfer;
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
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/transfers")
@RequiredArgsConstructor
@Tag(name = "Inventory Transfer Management", description = "Quản lý điều chuyển kho, xuất/nhận hàng giữa các chi nhánh")
public class InventoryTransferController {

    private final InventoryTransferService transferService;

    @Operation(summary = "Lấy danh sách phiếu điều chuyển", description = "Trả về danh sách phiếu điều chuyển có hỗ trợ phân trang, tìm kiếm và lọc theo trạng thái.")
    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("TRANSFER_VIEW")
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

    @Operation(
        summary = "Lập phiếu điều chuyển mới",
        description = "Tạo phiếu điều chuyển ở trạng thái chờ xử lý để đi đúng quy trình duyệt, xuất kho, vận chuyển và kiểm hàng."
    )
    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("TRANSFER_CREATE")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tạo phiếu thành công", content = @Content(schema = @Schema(implementation = InventoryTransfer.class))),
            @ApiResponse(responseCode = "400", description = "Dữ liệu không hợp lệ")
    })
    @PostMapping
    public ResponseEntity<InventoryTransfer> createTransfer(@Valid @RequestBody TransferRequest request) {
        return ResponseEntity.ok(transferService.createTransfer(request));
    }

    @Operation(summary = "Lấy chi tiết phiếu điều chuyển", description = "Trả về thông tin chi tiết và danh sách sản phẩm của một phiếu.")
    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("TRANSFER_VIEW")
    @GetMapping("/{id}")
    public ResponseEntity<TransferDetailResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(transferService.getById(id));
    }

    @Operation(
        summary = "Chi nhánh nguồn xác nhận (Flow 5)",
        description = "Áp dụng cho phiếu INTERNAL_SALE: Chi nhánh A xác nhận có đủ hàng và đồng ý chuyển. Hệ thống reserve kho A và chuyển trạng thái PENDING -> SOURCE_CONFIRMED."
    )
    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("TRANSFER_CREATE")
    @PostMapping("/{id}/source-confirm")
    public ResponseEntity<String> sourceConfirm(@PathVariable Long id) {
        transferService.sourceConfirm(id);
        return ResponseEntity.ok("Chi nhánh nguồn đã xác nhận. Phiếu chờ Admin duyệt.");
    }

    @Operation(
        summary = "Admin duyệt phiếu điều chuyển",
        description = "Flow 4: PENDING -> APPROVED (reserve kho tổng). Flow 5: SOURCE_CONFIRMED -> APPROVED (kiểm tra giá nội bộ)."
    )
    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("TRANSFER_APPROVE")
    @PostMapping("/{id}/approve")
    public ResponseEntity<String> approveTransfer(@PathVariable Long id) {
        transferService.approveTransfer(id);
        return ResponseEntity.ok("Đã duyệt phiếu điều chuyển.");
    }

    @Operation(
        summary = "Xuất kho - hàng lên đường (APPROVED -> SHIPPING)",
        description = "Trừ thực tế khỏi kho nguồn, giải phóng reservation. Yêu cầu phiếu ở trạng thái APPROVED."
    )
    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("TRANSFER_APPROVE")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Xuất kho thành công"),
            @ApiResponse(responseCode = "400", description = "Lỗi xuất kho (kho không đủ hàng, sai trạng thái...)")
    })
    @PostMapping("/{id}/ship")
    public ResponseEntity<String> approveAndShipTransfer(@PathVariable Long id) {
        transferService.approveAndShip(id);
        return ResponseEntity.ok("Đã xuất kho. Hàng đang trên đường vận chuyển.");
    }

    @Operation(
        summary = "Bắt đầu kiểm hàng (SHIPPING -> INSPECTING)",
        description = "Chi nhánh nhận mở phiếu, nhấn 'Bắt đầu kiểm hàng'. Không có biến động kho ở bước này."
    )
    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("TRANSFER_CREATE")
    @PostMapping("/{id}/start-inspection")
    public ResponseEntity<String> startInspection(@PathVariable Long id) {
        transferService.startInspection(id);
        return ResponseEntity.ok("Đã bắt đầu kiểm hàng.");
    }

    @Operation(
        summary = "Hoàn thành nhận hàng - QC (INSPECTING -> COMPLETED)",
        description = "Nhập số lượng nhận tốt và số lượng lỗi/thiếu. Hệ thống cộng hàng tốt vào kho nhận, cộng hàng lỗi vào kho lỗi hệ thống. Với INTERNAL_SALE còn ghi nhận công nợ nội bộ (B nợ A)."
    )
    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("TRANSFER_CREATE")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Nhận hàng thành công"),
            @ApiResponse(responseCode = "400", description = "Lỗi nhận hàng")
    })
    @PostMapping("/{id}/receive")
    public ResponseEntity<?> receiveTransfer(@PathVariable Long id, @RequestBody List<TransferQCRequest> qcItems) {
        transferService.receiveTransfer(id, qcItems);
        return ResponseEntity.ok("Đã xác nhận nhận hàng và kiểm đếm QC thành công.");
    }

    @Operation(
        summary = "Từ chối phiếu điều chuyển",
        description = "Áp dụng khi phiếu ở PENDING hoặc SOURCE_CONFIRMED. Giải phóng reservation nếu có."
    )
    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("TRANSFER_APPROVE")
    @PostMapping("/{id}/reject")
    public ResponseEntity<String> rejectTransfer(@PathVariable Long id) {
        transferService.rejectTransfer(id);
        return ResponseEntity.ok("Đã từ chối phiếu điều chuyển.");
    }

    @Operation(summary = "Hủy phiếu điều chuyển", description = "Hủy phiếu và hoàn tồn kho nếu đang ở trạng thái reserve.")
    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("TRANSFER_CANCEL")
    @PutMapping("/{id}/cancel")
    public ResponseEntity<?> cancelTransfer(@PathVariable Long id) {
        transferService.cancelTransfer(id);
        return ResponseEntity.ok("Đã hủy phiếu thành công");
    }

    @Operation(summary = "Đổi chi nhánh nhận", description = "Cập nhật chi nhánh nhận hàng cho phiếu đang ở trạng thái PENDING.")
    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("TRANSFER_UPDATE")
    @PutMapping("/{id}/change-destination")
    public ResponseEntity<?> changeDestination(@PathVariable Long id, @RequestParam Long newBranchId) {
        transferService.changeDestination(id, newBranchId);
        return ResponseEntity.ok("Đã thay đổi chi nhánh nhận");
    }

    @Operation(summary = "Xóa hoàn toàn phiếu điều chuyển", description = "Chỉ áp dụng cho phiếu PENDING.")
    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("TRANSFER_DELETE")
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteTransfer(@PathVariable Long id) {
        transferService.deleteTransfer(id);
        return ResponseEntity.ok("Đã xóa phiếu điều chuyển thành công!");
    }
}
