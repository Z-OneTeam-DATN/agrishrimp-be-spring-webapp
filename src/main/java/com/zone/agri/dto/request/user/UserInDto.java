package com.zone.agri.dto.request.user;

import com.zone.agri.entity.enums.Gender;
import com.zone.agri.entity.enums.UserStatus;
import java.time.LocalDate;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserInDto {

    Long id; // Dùng khi Update (nếu Create thì để null)

    String fullName;

    String email;

    String phoneNumber;

    String password; // Nếu update mà trường này null thì giữ nguyên pass cũ

    LocalDate dateOfBirth;

    String avatarUrl;

    Gender gender;      // 0: MALE, 1: FEMALE, ...

    UserStatus status;  // ACTIVE, BANNED (Admin dùng để khóa nick)

    Long branchId;      // Chuyển công tác sang chi nhánh khác

    Long roleId;        // Thăng chức/Giáng chức (Quan trọng: Chỉ là 1 ID, không phải List)
}
