package com.zone.agri.dto.response.auth;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ZaloAuthResponse {

    String accessToken;
    Long userId;
    String fullName;
    String avatar;
    String phone;
    boolean hasPaymentPassword;
}
