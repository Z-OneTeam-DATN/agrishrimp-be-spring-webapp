package com.zone.agri.controller.miniapp;

import com.zone.agri.common.AuthUtils;
import com.zone.agri.dto.miniapp.response.MiniAppDiagnosisResponse;
import com.zone.agri.dto.response.user.UserDetail;
import com.zone.agri.exception.CustomAuthenticationException;
import com.zone.agri.service.miniapp.MiniAppDiagnosisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/miniapp")
@RequiredArgsConstructor
@Tag(name = "Mini App", description = "API dành riêng cho Zalo Mini App")
public class MiniAppDiagnosisController {

    private final MiniAppDiagnosisService diagnosisService;

    /**
     * POST /api/miniapp/diagnosis
     *
     * Nhận ảnh tôm từ FE Mini Zalo, gọi AI phân tích bệnh + tạo phác đồ điều trị,
     * trả kết quả kèm sản phẩm gợi ý.
     *
     * Yêu cầu: JWT nội bộ hợp lệ (từ luồng Zalo login ở Phase BE-1).
     */
    @Operation(
            summary = "Chẩn đoán bệnh tôm",
            description = "Nhận ảnh + triệu chứng từ Zalo Mini App, gọi AI predict + AI prescription, trả kết quả kèm sản phẩm gợi ý."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Chẩn đoán thành công"),
            @ApiResponse(responseCode = "400", description = "Ảnh thiếu hoặc AI không nhận ra bệnh"),
            @ApiResponse(responseCode = "401", description = "Chưa đăng nhập hoặc token hết hạn"),
    })
    @PostMapping(value = "/diagnosis", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MiniAppDiagnosisResponse> diagnose(
            @RequestPart("image") MultipartFile image,
            @RequestPart(value = "userSymptoms", required = false) String userSymptoms) {

        UserDetail userDetail = AuthUtils.getUserDetail();
        if (userDetail == null) {
            throw new CustomAuthenticationException("Bạn cần đăng nhập để sử dụng tính năng này");
        }

        MiniAppDiagnosisResponse response = diagnosisService.diagnose(image, userSymptoms, userDetail.getId());
        return ResponseEntity.ok(response);
    }
}
