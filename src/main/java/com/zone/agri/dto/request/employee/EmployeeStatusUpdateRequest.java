package com.zone.agri.dto.request.employee;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmployeeStatusUpdateRequest {

    @NotBlank(message = "Trạng thái tài khoản không được để trống")
    @Schema(description = "Trạng thái tài khoản nhân viên", allowableValues = { "ACTIVE", "INACTIVE" }, example = "INACTIVE")
    private String status;
}
