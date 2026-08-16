package com.zone.agri.dto.response.ai;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TreatmentStageOptionResponse {

    /** Zero-based index để FE gửi lại đúng giai đoạn cần lấy phác đồ. */
    private Integer stageIndex;

    /** Số thứ tự thân thiện để hiển thị cho người dùng. */
    private Integer stageNumber;

    private String stageTitle;

    private Integer subStageIndex;
    private String subStageNumber;
    private String subStageTitle;
}
