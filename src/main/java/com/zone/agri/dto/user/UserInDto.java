package com.zone.agri.dto.user;

import com.zone.agri.entity.User;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserInDto {

  Long id;
  String displayName;
  String email;
  String password;

  public User toEntity() {
    return User.builder()
        .displayName(this.displayName)
        .email(this.email)
        .hashedPassword(this.password)
        .build();
  }
}
