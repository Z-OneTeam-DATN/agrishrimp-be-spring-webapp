package com.zone.agri.controller;

import com.zone.agri.dto.supplier.VietQrResponse;
import com.zone.agri.service.ExternalApiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/external")
@RequiredArgsConstructor
@Tag(name = "External API", description = "Các API tích hợp bên thứ 3 (Tra cứu MST,...)")
public class ExternalApiController {

    private final ExternalApiService externalApiService;

    @Operation(summary = "Tra cứu thông tin công ty qua MST")
    @GetMapping("/business/{taxCode}")
    public ResponseEntity<VietQrResponse.BusinessData> getBusinessInfo(@PathVariable String taxCode) {
        return ResponseEntity.ok(externalApiService.getBusinessByTaxCode(taxCode));
    }

//    @PostMapping("/bank-lookup")
//    public ResponseEntity<?> lookupBankAccount(@RequestBody Map<String, String> request) {
//        System.out.println("Dữ liệu nhận từ Frontend: " + request);
//        String bin = request.get("bin");
//        String accountNumber = request.get("accountNumber");
//
//        String accountName = externalApiService.lookupBankAccount(bin, accountNumber);
//
//        if (accountName != null) {
//            return ResponseEntity.ok(accountName);
//        }
//        return ResponseEntity.status(400).body("Không tìm thấy tài khoản");
//    }

    @PostMapping("/bank-lookup")
    public ResponseEntity<?> lookupBankAccount(@RequestBody Map<String, String> request) {
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