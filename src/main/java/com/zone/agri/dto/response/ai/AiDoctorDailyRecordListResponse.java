package com.zone.agri.dto.response.ai;

import java.util.List;
import lombok.Builder;
import lombok.Data;

/** Danh sách các ngày user đã chat/chẩn đoán với AI Doctor — dùng cho sidebar "Sổ khám". */
@Data
@Builder
public class AiDoctorDailyRecordListResponse {

    /** "yyyy-MM-dd", mới nhất trước. */
    private List<String> dates;
}
