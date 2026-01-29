package com.zone.agri.dto.user;

import com.zone.agri.entity.Role;
import lombok.*;
import lombok.experimental.FieldDefaults;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
public class RoleDto {

  String name;
  boolean saveFlag;
  boolean deleteFlag;

  public RoleDto(Role role) {
    this.name = role.getName();
    this.saveFlag = role.isSaveFlag();
    this.deleteFlag = role.isDeleteFlag();
  }
}
