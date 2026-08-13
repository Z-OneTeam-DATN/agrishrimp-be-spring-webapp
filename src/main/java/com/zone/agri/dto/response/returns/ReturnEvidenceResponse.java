package com.zone.agri.dto.response.returns;

import com.zone.agri.entity.enums.ReturnEvidenceType;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ReturnEvidenceResponse {
    private Long id;
    private ReturnEvidenceType mediaType;
    private String fileUrl;
    private String publicId;
    private String fileName;
}
