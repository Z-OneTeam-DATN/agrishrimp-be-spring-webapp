package com.zone.agri.dto.response.auth;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuthResponse {

    String accessToken;
    String refreshToken;

    // User info (optional — null on token refresh)
    Long userId;
    String fullName;
    String email;
    String phoneNumber;
    String contact; // Thêm trường contact linh động (email hoặc sđt)
    String avatarUrl;
    String role;
}
