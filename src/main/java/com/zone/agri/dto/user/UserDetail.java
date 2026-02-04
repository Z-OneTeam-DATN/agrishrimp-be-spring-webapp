package com.zone.agri.dto.user;

import com.zone.agri.entity.enums.Gender;
import com.zone.agri.entity.enums.UserStatus;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserDetail implements Serializable {

    Long id;

    String fullName;

    String email;

    String phoneNumber;

    LocalDate dateOfBirth;

    String avatarUrl;

    Gender gender;

    UserStatus status;

    Long branchId;

    RoleDto role;

    LocalDateTime createdAt;

    LocalDateTime updatedAt;
}