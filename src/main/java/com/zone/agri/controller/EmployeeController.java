package com.zone.agri.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.zone.agri.dto.request.employee.EmployeeCreateRequest;
import com.zone.agri.dto.request.employee.OcrCccdRequest;
import com.zone.agri.dto.response.employee.EmployeeResponse;
import com.zone.agri.dto.response.employee.OcrCccdResponse;
import com.zone.agri.security.annotation.RequirePermission;
import com.zone.agri.service.EmployeeService;
import com.zone.agri.service.OcrService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

/**
 * REST Controller for Employee management
 */
@RestController
@RequestMapping("/api/employees")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Employee Management", description = "Các API quản lý nhân viên hệ thống")
public class EmployeeController {

        private final EmployeeService employeeService;
        private final OcrService ocrService;

        /**
         * Get paginated employee list with filters
         * Endpoint: GET /api/employees
         */
        @GetMapping
        @SecurityRequirement(name = "bearerAuth")
        @RequirePermission("STAFF_VIEW")
        @Operation(summary = "Danh sách nhân viên (Phân trang & Lọc)", description = "Lấy danh sách nhân viên với hỗ trợ tìm kiếm, lọc theo chi nhánh, vai trò, trạng thái và phân trang.", responses = {
                        @ApiResponse(responseCode = "200", description = "Thành công")
        })
        @SecurityRequirement(name = "bearerAuth")
        public ResponseEntity<Page<EmployeeResponse>> getEmployees(
                        @Parameter(description = "Từ khóa tìm kiếm (tên, email, SĐT)", example = "Nguyễn") @RequestParam(required = false) String keyword,

                        @Parameter(description = "Lọc theo ID chi nhánh", example = "1") @RequestParam(required = false) Long branchId,

                        @Parameter(description = "Lọc theo ID vai trò", example = "2") @RequestParam(required = false) Long roleId,

                        @Parameter(description = "Lọc theo trạng thái (ACTIVE, INACTIVE, BANNED)", example = "ACTIVE") @RequestParam(required = false) String status,

                        @Parameter(hidden = true) @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

                Page<EmployeeResponse> employees = employeeService.getEmployees(
                                keyword, branchId, roleId, status, pageable);
                return ResponseEntity.ok(employees);
        }

        /**
         * Get employee by ID
         * Endpoint: GET /api/employees/{id}
         */
        @GetMapping("/{id}")
        @SecurityRequirement(name = "bearerAuth")
        @RequirePermission("STAFF_VIEW")
        @Operation(summary = "Chi tiết nhân viên", description = "Lấy thông tin chi tiết của một nhân viên theo ID.", responses = {
                        @ApiResponse(responseCode = "200", description = "Thành công"),
                        @ApiResponse(responseCode = "404", description = "Không tìm thấy nhân viên")
        })
        @SecurityRequirement(name = "bearerAuth")
        public ResponseEntity<EmployeeResponse> getEmployeeById(@PathVariable Long id) {
                return ResponseEntity.ok(employeeService.getEmployeeById(id));
        }

        /**
         * Create a new employee
         * Endpoint: POST /api/employees
         */
        @PostMapping
        @SecurityRequirement(name = "bearerAuth")
        @RequirePermission("STAFF_CREATE")
        @Operation(summary = "Tạo nhân viên mới", description = "Tạo tài khoản nhân viên mới với thông tin cá nhân, chi nhánh, và vai trò. "
                        +
                        "Mật khẩu mặc định là 'Agri@2024' nếu không được cung cấp.", responses = {
                                        @ApiResponse(responseCode = "201", description = "Tạo nhân viên thành công", content = @Content(schema = @Schema(implementation = EmployeeResponse.class))),
                                        @ApiResponse(responseCode = "400", description = "Dữ liệu không hợp lệ"),
                                        @ApiResponse(responseCode = "404", description = "Không tìm thấy chi nhánh hoặc vai trò"),
                                        @ApiResponse(responseCode = "409", description = "Email hoặc số điện thoại đã tồn tại")
                        })
        @SecurityRequirement(name = "bearerAuth")
        public ResponseEntity<EmployeeResponse> createEmployee(
                        @Valid @RequestBody EmployeeCreateRequest request) {
                log.info("Received request to create employee: {}", request.getEmail());
                EmployeeResponse response = employeeService.createEmployee(request);
                return ResponseEntity.status(HttpStatus.CREATED).body(response);
        }

        /**
         * Update employee
         * Endpoint: PUT /api/employees/{id}
         */
        @PutMapping("/{id}")
        @SecurityRequirement(name = "bearerAuth")
        @RequirePermission("STAFF_UPDATE")
        @Operation(summary = "Cập nhật nhân viên", description = "Chỉnh sửa thông tin nhân viên. Không thể sửa nhân viên có vai trò hệ thống.", responses = {
                        @ApiResponse(responseCode = "200", description = "Cập nhật thành công"),
                        @ApiResponse(responseCode = "400", description = "Dữ liệu không hợp lệ"),
                        @ApiResponse(responseCode = "404", description = "Không tìm thấy nhân viên"),
                        @ApiResponse(responseCode = "409", description = "Email hoặc SĐT đã được sử dụng")
        })
        @SecurityRequirement(name = "bearerAuth")
        public ResponseEntity<EmployeeResponse> updateEmployee(
                        @PathVariable Long id,
                        @Valid @RequestBody EmployeeCreateRequest request) {
                log.info("Updating employee ID: {}", id);
                return ResponseEntity.ok(employeeService.updateEmployee(id, request));
        }

        /**
         * Delete employee
         * Endpoint: DELETE /api/employees/{id}
         */
        @DeleteMapping("/{id}")
        @SecurityRequirement(name = "bearerAuth")
        @RequirePermission("STAFF_DELETE")
        @Operation(summary = "Xóa nhân viên", description = "Xóa (soft delete) nhân viên khỏi hệ thống. Không thể xóa nhân viên có vai trò hệ thống.", responses = {
                        @ApiResponse(responseCode = "204", description = "Xóa thành công"),
                        @ApiResponse(responseCode = "403", description = "Không được phép xóa nhân viên hệ thống"),
                        @ApiResponse(responseCode = "404", description = "Không tìm thấy nhân viên")
        })
        @SecurityRequirement(name = "bearerAuth")
        public ResponseEntity<Void> deleteEmployee(@PathVariable Long id) {
                log.info("Deleting employee ID: {}", id);
                employeeService.deleteEmployee(id);
                return ResponseEntity.noContent().build();
        }

        /**
         * Lookup citizen info by CCCD
         * Endpoint: GET /api/employees/lookup-citizen/{citizenId}
         */
        @GetMapping("/lookup-citizen/{citizenId}")
        @Operation(summary = "Tra cứu thông tin từ CCCD", description = "Tra cứu thông tin nhân viên (địa chỉ, ngày sinh) từ số CCCD trong hệ thống", responses = {
                        @ApiResponse(responseCode = "200", description = "Tìm thấy thông tin"),
                        @ApiResponse(responseCode = "404", description = "Không tìm thấy CCCD này")
        })
        public ResponseEntity<?> lookupByCitizenId(
                        @Parameter(description = "Số CCCD (12 chữ số)", example = "012345678901") @PathVariable String citizenId) {
                log.info("Looking up citizen ID: {}", citizenId);
                var response = employeeService.lookupByCitizenId(citizenId);
                return ResponseEntity.ok(response);
        }

        /**
         * Extract CCCD information from uploaded image using OCR
         * Endpoint: POST /api/employees/ocr-cccd
         */
        @PostMapping(value = "/ocr-cccd", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        @Operation(summary = "OCR trích xuất thông tin từ ảnh CCCD", description = "Upload ảnh mặt trước CCCD để tự động trích xuất thông tin (họ tên, ngày sinh, giới tính, địa chỉ)", responses = {
                        @ApiResponse(responseCode = "200", description = "OCR thành công", content = @Content(schema = @Schema(implementation = OcrCccdResponse.class))),
                        @ApiResponse(responseCode = "400", description = "Ảnh không hợp lệ"),
                        @ApiResponse(responseCode = "500", description = "Lỗi xử lý OCR")
        })
        public ResponseEntity<OcrCccdResponse> extractCccdFromImage(
                        @Parameter(description = "Ảnh mặt trước CCCD (PNG, JPG, JPEG)") @RequestParam("image") MultipartFile image) {

                // Validate image
                if (image.isEmpty()) {
                        return ResponseEntity.badRequest().build();
                }

                // Check file type
                String contentType = image.getContentType();
                if (contentType == null || !contentType.startsWith("image/")) {
                        return ResponseEntity.badRequest().build();
                }

                log.info("Processing OCR for CCCD image: {}", image.getOriginalFilename());
                OcrCccdResponse result = ocrService.extractCccdInfo(image);
                return ResponseEntity.ok(result);
        }
}
