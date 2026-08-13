package com.zone.agri.dto.request.returns;

import com.zone.agri.entity.enums.ReturnEvidenceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateReturnRequestEvidence {

    @NotNull
    ReturnEvidenceType mediaType;

    @NotBlank
    String fileUrl;

    String publicId;

    String fileName;
}
