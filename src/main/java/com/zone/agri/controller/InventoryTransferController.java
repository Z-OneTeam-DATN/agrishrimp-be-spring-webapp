package com.zone.agri.controller;

import com.zone.agri.dto.transfer.TransferDetailResponse;
import com.zone.agri.dto.transfer.TransferItemRequest;
import com.zone.agri.dto.transfer.TransferRequest;
import com.zone.agri.dto.transfer.TransferResponse;
import com.zone.agri.entity.InventoryTransfer;
import com.zone.agri.repository.UserRepository;
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
import org.springframework.security.core.context.SecurityContextHolder;
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
    private final UserRepository userRepository;

    private boolean isCurrentUserAdmin() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .map(user -> user.getRole() != null &&
                        (user.getRole().getId() == 1L || "ADMIN".equalsIgnoreCase(user.getRole().getSlug())))
                .orElse(false);
    }

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
        // 1. Tạo phiếu điều chuyển (Mặc định PENDING)
        InventoryTransfer transfer = transferService.createTransfer(request);

        // 2. ADMIN -> Tự động gọi hàm xác nhận xuất kho (Chuyển sang SHIPPING & trừ kho xuất)
        if (isCurrentUserAdmin()) {
            transferService.approveAndShip(transfer.getId());
            // Trả về bản ghi đã cập nhật trạng thái
            return ResponseEntity.ok(transfer);
        }

        // Quản lý kho -> Dừng ở PENDING, chờ Admin duyệt hoặc Kho xuất xác nhận
        return ResponseEntity.ok(transfer);
    }

    @Operation(summary = "Lấy chi tiết phiếu điều chuyển", description = "Trả về thông tin chi tiết và danh sách sản phẩm của một phiếu.")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/{id}")
    public ResponseEntity<TransferDetailResponse> getById(@PathVariable Long id) {
        // Vì Service đã lo toàn bộ việc build DTO và bắt lỗi,
        // Controller chỉ việc gọi hàm và trả về trực tiếp.
        return ResponseEntity.ok(transferService.getById(id));
    }

    @Operation(summary = "Xác nhận Xuất kho", description = "Duyệt phiếu và chuyển trạng thái sang SHIPPING (Đang vận chuyển), đồng thời trừ số lượng tồn kho tại chi nhánh xuất.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Xuất kho thành công"),
            @ApiResponse(responseCode = "400", description = "Lỗi xuất kho (kho không đủ hàng, sai trạng thái...)")
    })
    @PutMapping("/{id}/ship")
    public ResponseEntity<String> approveAndShipTransfer(@PathVariable Long id) {

        // CHẶN QUYỀN DUYỆT CỦA NHÂN VIÊN TẠI ĐÂY
        if (!isCurrentUserAdmin()) {
            throw new RuntimeException("CẢNH BÁO: BẠN KHÔNG PHẢI ADMIN, KHÔNG ĐƯỢC PHÉP XÁC NHẬN ĐIỀU CHUYỂN!");
        }

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

    @Operation(summary = "Xóa hoàn toàn phiếu điều chuyển", description = "Chỉ áp dụng cho phiếu PENDING")
    @SecurityRequirement(name = "bearerAuth")
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteTransfer(@PathVariable Long id) {
        transferService.deleteTransfer(id);
        return ResponseEntity.ok("Đã xóa phiếu điều chuyển thành công!");
    }



}