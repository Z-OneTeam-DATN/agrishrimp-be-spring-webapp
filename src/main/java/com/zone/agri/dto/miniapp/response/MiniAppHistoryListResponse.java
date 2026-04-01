package com.zone.agri.dto.miniapp.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Response cho GET /api/miniapp/history.
 */
@Data
@Builder
public class MiniAppHistoryListResponse {

    private List<MiniAppHistoryItemResponse> items;

    /** Tổng số bản ghi (để FE biết có còn trang tiếp không) */
    private long total;

    /** Trang hiện tại (0-indexed) */
    private int page;

    /** Kích thước trang */
    private int size;
}
