package com.zone.agri.dto.user;

import java.time.LocalDateTime;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserOutDto {

  Long id;
  String displayName;
  String email;
  LocalDateTime createdAt;
  LocalDateTime updatedAt;
  Set<RoleDto> roles;
}
