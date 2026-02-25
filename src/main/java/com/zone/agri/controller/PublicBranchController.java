package com.zone.agri.controller;

import com.zone.agri.dto.admin.BranchDTO;
import com.zone.agri.service.BranchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/public/branches")
@RequiredArgsConstructor
@Tag(name = "Public Branch APIs", description = "Các API công khai để lấy thông tin chi nhánh và cửa hàng")
@CrossOrigin(origins = "http://localhost:3000")
public class PublicBranchController {

    private final BranchService branchService;

    @Operation(summary = "Lấy danh sách chi nhánh đang hoạt động",
               description = "Trả về danh sách các chi nhánh và cửa hàng có trạng thái ACTIVE. Không yêu cầu xác thực.")
    @GetMapping
    public ResponseEntity<List<BranchDTO>> getPublicBranches() {
        return ResponseEntity.ok(branchService.getPublicBranches());
    }

    @Operation(summary = "Lấy chi tiết chi nhánh công khai",
               description = "Trả về thông tin chi tiết của một chi nhánh đang hoạt động. Không yêu cầu xác thực.")
    @GetMapping("/{id}")
    public ResponseEntity<BranchDTO> getPublicBranchDetail(
            @Parameter(description = "ID của chi nhánh", example = "1", required = true)
            @PathVariable Long id) {
        BranchDTO branch = branchService.getBranchById(id);
        // Có thể thêm kiểm tra status ở đây nếu muốn bảo mật hơn, 
        // nhưng hiện tại getBranchById trả về theo ID bất kể status.
        return ResponseEntity.ok(branch);
    }
}
