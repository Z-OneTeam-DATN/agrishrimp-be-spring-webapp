package com.zone.agri.controller.miniapp;

import com.zone.agri.common.AuthUtils;
import com.zone.agri.dto.miniapp.response.MiniAppDiagnosisResponse;
import com.zone.agri.dto.miniapp.response.MiniAppHistoryListResponse;
import com.zone.agri.dto.response.user.UserDetail;
import com.zone.agri.exception.CustomAuthenticationException;
import com.zone.agri.service.miniapp.MiniAppDiagnosisHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/miniapp")
@RequiredArgsConstructor
@Tag(name = "Mini App", description = "API dành riêng cho Zalo Mini App")
public class MiniAppDiagnosisHistoryController {

    private final MiniAppDiagnosisHistoryService historyService;

    // =========================================================
    // GET /api/miniapp/history
    // =========================================================

    @Operation(
            summary = "Lấy danh sách lịch sử chẩn đoán",
            description = "Trả danh sách tóm tắt các ca chẩn đoán của user hiện tại, mới nhất trước."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Thành công"),
            @ApiResponse(responseCode = "401", description = "Chưa đăng nhập hoặc token hết hạn"),
    })
    @GetMapping("/history")
    public ResponseEntity<MiniAppHistoryListResponse> getHistory(
            @Parameter(description = "Trang hiện tại (0-indexed, mặc định 0)")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Số bản ghi mỗi trang (mặc định 20, tối đa 50)")
            @RequestParam(defaultValue = "20") int size) {

        UserDetail userDetail = AuthUtils.getUserDetail();
        if (userDetail == null) {
            throw new CustomAuthenticationException("Bạn cần đăng nhập để xem lịch sử chẩn đoán");
        }

        // Cap page size để tránh query quá lớn
        int cappedSize = Math.min(size, 50);
        Pageable pageable = PageRequest.of(page, cappedSize);

        return ResponseEntity.ok(historyService.getMyDiagnosisHistory(userDetail.getId(), pageable));
    }

    // =========================================================
    // GET /api/miniapp/diagnosis/{id}
    // =========================================================

    @Operation(
            summary = "Lấy chi tiết một ca chẩn đoán",
            description = "Trả full kết quả chẩn đoán từ lịch sử. " +
                    "FE có thể render lại màn hình result/treatment mà không cần gọi AI lại. " +
                    "User chỉ xem được ca chẩn đoán của chính mình."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Thành công"),
            @ApiResponse(responseCode = "401", description = "Chưa đăng nhập hoặc token hết hạn"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy hoặc không có quyền truy cập"),
    })
    @GetMapping("/diagnosis/{id}")
    public ResponseEntity<MiniAppDiagnosisResponse> getDiagnosisDetail(
            @Parameter(description = "ID ca chẩn đoán (trả về trong diagnosisId của POST /diagnosis)")
            @PathVariable Long id) {

        UserDetail userDetail = AuthUtils.getUserDetail();
        if (userDetail == null) {
            throw new CustomAuthenticationException("Bạn cần đăng nhập để xem chi tiết chẩn đoán");
        }

        return ResponseEntity.ok(historyService.getDiagnosisDetail(id, userDetail.getId()));
    }
}
