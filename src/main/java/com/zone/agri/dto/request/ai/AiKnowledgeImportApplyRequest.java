package com.zone.agri.dto.request.ai;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiKnowledgeImportApplyRequest {

    private String mode;
    private List<AiKnowledgeImportPreviewRowRequest> rows;
}
