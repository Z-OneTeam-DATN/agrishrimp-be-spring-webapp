package com.zone.agri.dto.supplier;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BankAccountResponse {
    private String code; // "00" là thành công
    private String desc;
    private BankData data;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BankData {
        private String accountName; // Tên chủ tài khoản
    }
}