package com.zone.agri.controller;

import com.zone.agri.dto.response.supplier.VietQrResponse;
import com.zone.agri.service.ExternalApiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/external")
@RequiredArgsConstructor
@Tag(name = "External Services", description = "Tích hợp dịch vụ bên thứ 3: VietQR, Tra cứu Doanh nghiệp, Ngân hàng")
public class ExternalApiController {

    private final ExternalApiService externalApiService;

    @Operation(summary = "Tra cứu Mã số thuế", description = "Lấy thông tin công ty từ Tổng cục Thuế qua API VietQR.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tìm thấy thông tin", content = @Content(schema = @Schema(implementation = VietQrResponse.BusinessData.class))),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy MST")
    })
    @GetMapping("/business/{taxCode}")
    public ResponseEntity<VietQrResponse.BusinessData> getBusinessInfo(
            @Parameter(description = "Mã số thuế doanh nghiệp", example = "0312345678", required = true)
            @PathVariable String taxCode) {
        return ResponseEntity.ok(externalApiService.getBusinessByTaxCode(taxCode));
    }

    @Operation(summary = "Tra cứu tài khoản ngân hàng", description = "Kiểm tra họ tên chủ tài khoản ngân hàng (Demo).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tra cứu thành công", content = @Content(examples = @ExampleObject(value = "NGUYEN VAN A"))),
            @ApiResponse(responseCode = "400", description = "Số tài khoản không hợp lệ")
    })
    @PostMapping("/bank-lookup")
    public ResponseEntity<?> lookupBankAccount(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Thông tin ngân hàng cần tra cứu",
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    name = "Tra cứu VCB",
                                    value = "{\"bin\": \"970436\", \"accountNumber\": \"0971505815\"}",
                                    summary = "Ví dụ tra cứu Vietcombank"
                            )
                    )
            )
            @RequestBody Map<String, String> request) {
        
        String accountNumber = request.get("accountNumber");

        if ("0971505815".equals(accountNumber)) {
            return ResponseEntity.ok("NGUYEN HOANG GIA HUY");
        }

        if (accountNumber != null && accountNumber.length() >= 6) {
            return ResponseEntity.ok("NGUYEN VAN DEMO");
        }

        return ResponseEntity.status(400).body("Số tài khoản không hợp lệ");
    }
}
