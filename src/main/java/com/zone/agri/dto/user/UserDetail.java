package com.zone.agri.dto.user;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserDetail implements Serializable {

  Long id;
  String email;
  String hashedPassword;
  String displayName;
  LocalDateTime createdAt;
  LocalDateTime updatedAt;
  Set<RoleDto> roleList;
}
