package com.zone.agri.service;

import com.zone.agri.dto.supplier.BankAccountResponse;
import com.zone.agri.dto.supplier.VietQrResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ExternalApiService {

    private final RestTemplate restTemplate;

    // API miễn phí của VietQR
    private final String VIETQR_API_URL = "https://api.vietqr.io/v2/business/";

    public VietQrResponse.BusinessData getBusinessByTaxCode(String taxCode) {
        try {
            // Gọi API External
            String url = VIETQR_API_URL + taxCode;
            String rawJson = restTemplate.getForObject(url, String.class);
            System.out.println("DỮ LIỆU THUẾ TRẢ VỀ: " + rawJson);
            ResponseEntity<VietQrResponse> response = restTemplate.getForEntity(url, VietQrResponse.class);

            VietQrResponse body = response.getBody();

            // Kiểm tra xem có dữ liệu không (code 00 là thành công)
            if (body != null && "00".equals(body.getCode()) && body.getData() != null) {
                return body.getData();
            } else {
                throw new RuntimeException("Không tìm thấy thông tin doanh nghiệp hoặc MST không tồn tại.");
            }
        } catch (Exception e) {
            // Log lỗi nếu cần
            throw new RuntimeException("Lỗi khi kết nối đến cơ quan thuế: " + e.getMessage());
        }
    }

    public String lookupBankAccount(String bankBin, String accountNumber) {
        String url = "https://api.vietqr.io/v2/lookup";

        // Header yêu cầu từ VietQR (Bạn cần đăng ký Client ID/API Key trên vietqr.io)
        HttpHeaders headers = new HttpHeaders();
        headers.set("x-client-id", "36f10827-25b3-43ef-900f-ba5af24de8d6");
        headers.set("x-api-key", "08f37a19-7a38-44f6-bcfc-531dde5ce656");

        Map<String, String> body = new HashMap<>();
        body.put("bin", bankBin);
        body.put("accountNumber", accountNumber);

        HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<BankAccountResponse> response = restTemplate.postForEntity(url, entity, BankAccountResponse.class);
            System.out.println("PHẢN HỒI GỐC TỪ VIETQR: " + response.getBody());
            if (response.getBody() != null && "00".equals(response.getBody().getCode())) {
                return response.getBody().getData().getAccountName();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}