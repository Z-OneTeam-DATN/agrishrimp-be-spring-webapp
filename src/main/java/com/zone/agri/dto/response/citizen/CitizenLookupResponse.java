package com.zone.agri.dto.response.citizen;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Thông tin tra cứu từ CCCD")
public class CitizenLookupResponse {

    @Schema(description = "Họ và tên")
    private String fullName;

    @Schema(description = "Ngày sinh")
    private LocalDate dateOfBirth;

    @Schema(description = "Giới tính")
    private String gender;

    @Schema(description = "Địa chỉ thường trú")
    private String address;
}
