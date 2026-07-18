package com.zone.agri.controller;

import com.zone.agri.dto.request.driver.DriverRequest;
import com.zone.agri.dto.response.common.MessageResponse;
import com.zone.agri.dto.response.driver.DriverResponse;
import com.zone.agri.security.annotation.RequirePermission;
import com.zone.agri.service.DriverService;
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
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/drivers")
@RequiredArgsConstructor
@Tag(name = "Driver Management", description = "Quản lý hồ sơ tài xế và phương tiện điều động")
public class DriverController {

    private final DriverService driverService;

    @Operation(summary = "Thêm tài xế mới", description = "Tạo hồ sơ tài xế kèm thông tin liên lạc, GPLX, avatar.")
    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("DRIVER_CREATE")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tạo thành công", content = @Content(schema = @Schema(implementation = DriverResponse.class))),
            @ApiResponse(responseCode = "400", description = "Số điện thoại, email hoặc mã tài xế đã tồn tại")
    })
    @PostMapping
    public ResponseEntity<DriverResponse> createDriver(@Valid @RequestBody DriverRequest request) {
        return ResponseEntity.ok(driverService.createDriver(request));
    }

    @Operation(summary = "Danh sách tài xế", description = "Lấy danh sách tài xế phân trang, hỗ trợ tìm kiếm và lọc theo trạng thái.")
    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("DRIVER_VIEW")
    @GetMapping
    public ResponseEntity<Page<DriverResponse>> getAllDrivers(
            @Parameter(description = "Từ khóa tìm kiếm (Họ tên, SĐT, biển số xe)", example = "Nguyễn Văn A") @RequestParam(required = false) String keyword,
            @Parameter(description = "Trạng thái tài xế", example = "ACTIVE") @RequestParam(required = false) String status,
            @Parameter(hidden = true) @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(driverService.getAllDrivers(keyword, status, pageable));
    }

    @Operation(summary = "Chi tiết tài xế", description = "Xem hồ sơ chi tiết của tài xế.")
    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("DRIVER_VIEW")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tìm thấy hồ sơ", content = @Content(schema = @Schema(implementation = DriverResponse.class))),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy ID tài xế")
    })
    @GetMapping("/{id}")
    public ResponseEntity<DriverResponse> getDriverById(
            @Parameter(description = "ID tài xế", example = "1", required = true) @PathVariable Long id) {
        return ResponseEntity.ok(driverService.getDriverById(id));
    }

    @Operation(summary = "Cập nhật tài xế", description = "Cập nhật hồ sơ tài xế và phương tiện.")
    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("DRIVER_UPDATE")
    @PutMapping("/{id}")
    public ResponseEntity<DriverResponse> updateDriver(
            @Parameter(description = "ID tài xế cần cập nhật", example = "1", required = true) @PathVariable Long id,
            @Valid @RequestBody DriverRequest request) {
        return ResponseEntity.ok(driverService.updateDriver(id, request));
    }

    @Operation(summary = "Xóa tài xế", description = "Xóa tài xế khỏi hệ thống.")
    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("DRIVER_DELETE")
    @DeleteMapping("/{id}")
    public ResponseEntity<MessageResponse> deleteDriver(
            @Parameter(description = "ID tài xế cần xóa", example = "1", required = true) @PathVariable Long id) {
        driverService.deleteDriver(id);
        return ResponseEntity.ok(new MessageResponse("Đã xóa tài xế thành công!"));
    }
}
