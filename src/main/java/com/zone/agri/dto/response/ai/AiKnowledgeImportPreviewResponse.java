package com.zone.agri.dto.response.ai;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiKnowledgeImportPreviewResponse {

    private String mode;
    private int totalRows;
    private int validRows;
    private int invalidRows;
    private List<AiKnowledgeImportPreviewRowResponse> rows;
}
