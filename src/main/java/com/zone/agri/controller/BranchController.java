package com.zone.agri.controller;

import com.zone.agri.dto.response.admin.BranchDTO;
import com.zone.agri.dto.request.branch.FindNearestBranchRequest;
import com.zone.agri.dto.response.branch.NearestBranchResponse;
import com.zone.agri.security.annotation.RequirePermission;
import com.zone.agri.service.BranchSearchService;
import com.zone.agri.service.BranchService;
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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.zone.agri.dto.request.branch.CheckStockItemRequest;

import java.util.List;

@RestController
@RequestMapping("/api/branches")
@RequiredArgsConstructor
@Tag(name = "Branch Management", description = "Quản lý chi nhánh, kho bãi và điểm giao dịch")
public class BranchController {

    private final BranchService branchService;
    private final BranchSearchService branchSearchService;

    // VIEW
    @Operation(summary = "Lấy danh sách chi nhánh", description = "Trả về toàn bộ danh sách chi nhánh và kho đang hoạt động.")
    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission({"BRANCH_VIEW", "STAFF_CREATE"})
    @GetMapping()
    public ResponseEntity<List<BranchDTO>> getAll() {
        return ResponseEntity.ok(branchService.getAll());
    }

    // VIEW
    @Operation(summary = "Chi tiết chi nhánh", description = "Lấy thông tin chi tiết của một chi nhánh dựa trên ID.")
    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("BRANCH_VIEW")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tìm thấy chi nhánh", content = @Content(schema = @Schema(implementation = BranchDTO.class))),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy chi nhánh với ID cung cấp")
    })
    @GetMapping("/{id}")
    public ResponseEntity<BranchDTO> getById(
            @Parameter(description = "ID của chi nhánh cần tìm", example = "1", required = true)
            @PathVariable Long id) {
        return ResponseEntity.ok(branchService.getBranchById(id));
    }

    // CREATE
    @Operation(summary = "Tạo mới chi nhánh", description = "Thêm một chi nhánh hoặc kho mới vào hệ thống.")
    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("BRANCH_CREATE")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tạo thành công", content = @Content(schema = @Schema(implementation = BranchDTO.class))),
            @ApiResponse(responseCode = "400", description = "Dữ liệu không hợp lệ hoặc mã chi nhánh đã tồn tại")
    })
    @PostMapping
    public ResponseEntity<BranchDTO> create(@Valid @RequestBody BranchDTO dto) {
        return ResponseEntity.ok(branchService.create(dto));
    }


    // UPDATE
    @Operation(summary = "Cập nhật chi nhánh", description = "Chỉnh sửa thông tin chi nhánh đã tồn tại.")
    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("BRANCH_UPDATE")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cập nhật thành công"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy chi nhánh")
    })
    @PutMapping("/{id}")
    public ResponseEntity<BranchDTO> update(
            @Parameter(description = "ID của chi nhánh cần sửa", example = "1", required = true)
            @PathVariable Long id,
            @Valid @RequestBody BranchDTO dto) {
        return ResponseEntity.ok(branchService.update(id, dto));
    }


    // DELETE
    @Operation(summary = "Xóa chi nhánh", description = "Xóa mềm (Soft Delete) hoặc vô hiệu hóa một chi nhánh.")
    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("BRANCH_DELETE")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Xóa thành công"),
            @ApiResponse(responseCode = "400", description = "Không thể xóa chi nhánh đang có dữ liệu ràng buộc")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @Parameter(description = "ID của chi nhánh cần xóa", example = "1", required = true)
            @PathVariable Long id) {
        branchService.delete(id);
        return ResponseEntity.noContent().build();
    }

    //VIEW
    @Operation(summary = "Kiểm tra tồn kho", description = "Trả về danh sách các chi nhánh có ĐỦ SỐ LƯỢNG cho TẤT CẢ sản phẩm truyền vào.")
    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("BRANCH_VIEW")
    @PostMapping("/check-stock")
    public ResponseEntity<List<BranchDTO>> checkStock(@RequestBody List<@Valid CheckStockItemRequest> items) {
        return ResponseEntity.ok(branchService.findBranchesWithEnoughStock(items));
    }


    //PUBLIC
    @Operation(
            summary = "Tìm chi nhánh gần nhất",
            description = "Tìm danh sách chi nhánh gần nhất với vị trí người dùng theo 3 tầng lọc: "
                    + "Bounding Box → Haversine → Distance Matrix API. "
                    + "Kết quả sort theo thời gian di chuyển thực tế (tăng dần)."
    )
    @PostMapping("/nearest")
    public ResponseEntity<List<NearestBranchResponse>> findNearest(
            @Valid @RequestBody FindNearestBranchRequest request) {

        List<BranchSearchService.BranchWithRealDistance> nearest =
                branchSearchService.findNearestBranches(
                        request.getLat(),
                        request.getLng(),
                        request.getRadiusKm());

        List<NearestBranchResponse> response = nearest.stream()
                .limit(request.getLimit() != null ? request.getLimit() : 5)
                .map(bwr -> NearestBranchResponse.builder()
                        .id(bwr.branch().getId())
                        .name(bwr.branch().getName())
                        .addressText(bwr.branch().getAddressDetail())
                        .phone(bwr.branch().getPhone())
                        .distanceKm(Math.round(bwr.distanceKm() * 100.0) / 100.0)
                        .durationMinutes(Math.round(bwr.durationMinutes() * 10.0) / 10.0)
                        .build())
                .toList();

        return ResponseEntity.ok(response);
    }
}
