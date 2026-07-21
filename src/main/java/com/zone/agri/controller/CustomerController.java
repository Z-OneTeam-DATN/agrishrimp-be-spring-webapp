package com.zone.agri.controller;

import com.zone.agri.dto.request.customer.CustomerRequest;
import com.zone.agri.dto.response.customer.CustomerDetailResponse;
import com.zone.agri.dto.response.customer.CustomerResponse;
import com.zone.agri.entity.Customer;
import com.zone.agri.security.annotation.RequirePermission;
import com.zone.agri.service.CustomerService;
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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.List;

@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
@Tag(name = "Customer Management", description = "Quản lý hồ sơ khách hàng, nông dân và lịch sử giao dịch")
public class CustomerController {

    private final CustomerService customerService;

    @Operation(summary = "Danh sách khách hàng", description = "Tìm kiếm khách hàng theo tên, số điện thoại hoặc mã khách hàng. Hỗ trợ phân trang.")
    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("CUSTOMER_VIEW")
    @GetMapping
    public ResponseEntity<Page<CustomerResponse>> getAll(
            @Parameter(description = "Từ khóa tìm kiếm (Tên, SĐT, Email)", example = "Nguyễn Văn A") @RequestParam(required = false) String keyword,

            @Parameter(description = "Trạng thái tài khoản (ACTIVE, LOCKED)", example = "ACTIVE") @RequestParam(required = false) String status,

            @Parameter(description = "Số trang (bắt đầu từ 0)", example = "0") @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "Số lượng bản ghi mỗi trang", example = "20") @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(customerService.getCustomers(keyword, status, pageable));
    }

    @Operation(summary = "Kiểm tra trùng email/số điện thoại", description = "Dùng cho validate realtime ở form thêm khách hàng")
    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("CUSTOMER_VIEW")
    @GetMapping("/check-duplicate")
    public ResponseEntity<Map<String, Boolean>> checkDuplicate(
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String phone) {
        return ResponseEntity.ok(customerService.checkDuplicate(email, phone));
    }

    @Operation(summary = "Chi tiết khách hàng", description = "Xem hồ sơ chi tiết, địa chỉ và thông tin liên lạc của khách hàng.")
    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("CUSTOMER_VIEW")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tìm thấy hồ sơ", content = @Content(schema = @Schema(implementation = Customer.class))),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy ID khách hàng")
    })
    @GetMapping("/{id}")
    public ResponseEntity<CustomerResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(customerService.getCustomerById(id));
    }

    @Operation(summary = "Chi tiết khách hàng đầy đủ", description = "Trả dữ liệu đầy đủ cho trang chi tiết: quick stats, địa chỉ giao hàng, ghi chú nội bộ, lịch sử trạng thái")
    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("CUSTOMER_VIEW")
    @GetMapping("/{id}/detail")
    public ResponseEntity<CustomerDetailResponse> getDetail(@PathVariable Long id) {
        return ResponseEntity.ok(customerService.getCustomerDetailById(id));
    }

    @Operation(summary = "Tạo hồ sơ khách hàng mới", description = "Thêm mới khách hàng vào hệ thống. Hệ thống có thể tự động tạo tài khoản User liên kết nếu được cấu hình.")
    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("CUSTOMER_CREATE")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tạo thành công", content = @Content(schema = @Schema(implementation = Customer.class))),
            @ApiResponse(responseCode = "400", description = "Số điện thoại hoặc Email đã tồn tại")
    })
    @PostMapping
    public ResponseEntity<Customer> create(@Valid @RequestBody CustomerRequest request) {
        return ResponseEntity.ok(customerService.createCustomer(request));
    }

    @Operation(summary = "Cập nhật hồ sơ", description = "Chỉnh sửa thông tin cá nhân, địa chỉ hoặc ghi chú quản lý.")
    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("CUSTOMER_UPDATE")
    @PutMapping("/{id}")
    public ResponseEntity<Customer> update(
            @Parameter(description = "ID khách hàng cần sửa", example = "100", required = true) @PathVariable Long id,
            @Valid @RequestBody CustomerRequest request) {
        return ResponseEntity.ok(customerService.updateCustomer(id, request));
    }

    @Operation(summary = "Khóa/Mở khóa tài khoản", description = "Đổi trạng thái đăng nhập của khách hàng")
    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("CUSTOMER_UPDATE")
    @PatchMapping("/{id}/toggle-status")
    public ResponseEntity<String> toggleStatus(@PathVariable Long id) {
        customerService.toggleUserStatus(id);
        return ResponseEntity.ok("Cập nhật trạng thái tài khoản thành công!");
    }

    @Operation(summary = "Gửi lại thông tin tài khoản qua email", description = "Gửi lại mật khẩu mới cho khách hàng qua email của họ")
    @SecurityRequirement(name = "bearerAuth")
    @RequirePermission("CUSTOMER_UPDATE")
    @PostMapping("/{id}/resend-credentials")
    public ResponseEntity<String> resendCredentials(@PathVariable Long id) {
        String result = customerService.resendCustomerCredentials(id);
        return ResponseEntity.ok(result);
    }

    // 🟢 Get all branches for FE dropdown
    @Operation(summary = "Danh sách chi nhánh", description = "Lấy danh sách chi nhánh để assign cho khách hàng")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/lookup/branches")
    public ResponseEntity<?> getBranches() {
        return ResponseEntity.ok(customerService.getAllBranches());
    }

    // 🟢 Get staff by branch for FE dropdown
    @Operation(summary = "Danh sách nhân viên theo chi nhánh", description = "Lấy danh sách nhân viên của chi nhánh để assign")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/lookup/staff-by-branch/{branchId}")
    public ResponseEntity<?> getStaffByBranch(@PathVariable Long branchId) {
        return ResponseEntity.ok(customerService.getStaffByBranch(branchId));
    }
}
