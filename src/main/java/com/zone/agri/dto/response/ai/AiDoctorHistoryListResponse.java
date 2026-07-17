package com.zone.agri.dto.response.ai;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Response cho GET /api/ai-doctor/history.
 */
@Data
@Builder
public class AiDoctorHistoryListResponse {

    private List<AiDoctorHistoryItemResponse> items;

    /** Tổng số bản ghi (để FE biết có còn trang tiếp không) */
    private long total;

    /** Trang hiện tại (0-indexed) */
    private int page;

    /** Kích thước trang */
    private int size;
}
