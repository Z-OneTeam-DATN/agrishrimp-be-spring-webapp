package com.zone.agri.dto.response.payment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * DTO cho response từ PayOS REST API.
 * @JsonIgnoreProperties(ignoreUnknown = true) để bỏ qua các field mới
 * mà SDK chưa hỗ trợ (ví dụ: expiredAt).
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PayOSApiResponse {

    private String code;
    private String desc;
    private PayOSLinkData data;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PayOSLinkData {
        private String bin;
        private String accountNumber;
        private String accountName;
        private Integer amount;
        private String description;
        private Long orderCode;
        private String currency;
        private String paymentLinkId;
        private String status;
        private Long expiredAt;
        private String checkoutUrl;
        private String qrCode;
    }
}
