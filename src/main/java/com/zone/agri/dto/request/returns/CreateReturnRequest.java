package com.zone.agri.dto.request.returns;

import com.zone.agri.entity.enums.ReturnHandlingOption;
import com.zone.agri.entity.enums.ReturnIssueType;
import com.zone.agri.entity.enums.ReturnRefundMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class CreateReturnRequest {

    @NotNull
    Long orderId;

    @NotBlank
    String fullName;

    @NotBlank
    String phoneNumber;

    String email;

    String bankAccountName;

    String bankAccountNumber;

    String bankName;

    String bankBranch;

    @NotNull
    ReturnIssueType issueType;

    @NotNull
    ReturnRefundMethod refundMethod;

    ReturnHandlingOption handlingOption;

    @NotBlank
    String reason;

    @NotBlank
    String description;

    @Valid
    @NotEmpty
    List<CreateReturnRequestItem> items;

    @Valid
    @NotEmpty
    List<CreateReturnRequestEvidence> evidences;
}
