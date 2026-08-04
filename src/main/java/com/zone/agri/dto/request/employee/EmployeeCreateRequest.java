package com.zone.agri.dto.request.employee;

import com.zone.agri.entity.enums.Gender;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmployeeCreateRequest {

    @NotBlank(message = "Họ và tên không được để trống")
    private String fullName;

    // BỎ @NotBlank ở đây vì Backend sẽ tự sinh mã khi trả về
    private String employeeCode;

    @NotBlank(message = "Email không được để trống")
    @Email(message = "Email không hợp lệ")
    private String email;

    // Đổi 'phone' thành 'phoneNumber' cho khớp với Frontend
    @NotBlank(message = "Số điện thoại không được để trống")
    @Pattern(regexp = "^0\\d{9}$", message = "Số điện thoại phải có 10 chữ số và bắt đầu bằng 0")
    private String phoneNumber;

    // Thêm các trường Frontend gửi lên
    private String citizenId;
    private LocalDate dateOfBirth;
    private Gender gender;

    private String addressDetail;

    // Bat buoc hay khong tuy vai tro (workspace ky su/tu van thi khong can chi nhanh) — kiem tra o
    // EmployeeService.resolveBranch(...), khong dat @NotNull o day nua.
    private Long branchId;

    @NotNull(message = "Vai trò không được để trống")
    private Long roleId;

    private LocalDate startDate;
    private String avatarUrl;
    private String status;

    // Frontend gửi lên biến tên là 'password'
    private String password;
}
