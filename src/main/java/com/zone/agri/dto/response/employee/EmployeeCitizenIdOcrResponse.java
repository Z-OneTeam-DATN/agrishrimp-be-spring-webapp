package com.zone.agri.dto.response.employee;

import com.zone.agri.entity.enums.Gender;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Kết quả OCR CCCD/CMT để gợi ý điền form nhân viên")
public class EmployeeCitizenIdOcrResponse {

    @Schema(description = "Số CCCD/CMND nhận diện được")
    private String citizenId;

    @Schema(description = "Họ và tên nhận diện được")
    private String fullName;

    @Schema(description = "Ngày sinh đã chuẩn hóa theo yyyy-MM-dd")
    private String dateOfBirth;

    @Schema(description = "Giới tính đã chuẩn hóa về enum hệ thống")
    private Gender gender;

    @Schema(description = "Địa chỉ thường trú hoặc liên hệ nhận diện được")
    private String addressDetail;

    @Schema(description = "Quê quán hoặc nơi thường trú gốc nếu dịch vụ trả về")
    private String homeTown;

    @Schema(description = "Quốc tịch")
    private String nationality;

    @Schema(description = "Loại giấy tờ theo FPT AI Reader")
    private String cardType;

    @Schema(description = "Độ tin cậy trung bình của các trường chính, đơn vị phần trăm")
    private Double confidence;
}
