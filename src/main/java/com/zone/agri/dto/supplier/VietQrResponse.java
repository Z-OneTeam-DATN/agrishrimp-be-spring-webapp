package com.zone.agri.dto.supplier;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true) // Quan trọng: Bỏ qua lỗi nếu JSON có trường lạ
public class VietQrResponse {
    private String code;
    private String desc;
    private BusinessData data;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BusinessData {
        private String id;
        private String name;
        private String address;
        private String taxCode;

        @JsonProperty("owner")
        private String owner;

        @JsonProperty("phone")
        private String phone;

        @JsonProperty("email")
        private String email;
        private String content;
    }
}