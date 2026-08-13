package com.zone.agri.dto.request.returns;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ReturnRequestRejectRequest {

    @NotBlank
    String rejectReason;

    String internalNote;
}
