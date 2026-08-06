package com.zone.agri.dto.response;

import lombok.Data;

import java.util.List;

@Data
public class ImageIndexBatchResponse {
    private String status;
    private Integer success;
    private Integer failed;
    private List<Long> successIds;
    private List<FailedItem> failedIds;

    @Data
    public static class FailedItem {
        private Long productId;
        private String error;
    }
}
