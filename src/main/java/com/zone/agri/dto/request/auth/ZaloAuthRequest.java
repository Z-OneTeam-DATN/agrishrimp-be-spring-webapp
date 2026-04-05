package com.zone.agri.dto.request.auth;

import jakarta.validation.constraints.NotBlank;
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
public class ZaloAuthRequest {

    @NotBlank(message = "userId không được để trống")
    String userId;

    String name;

    String avatar;

    @NotBlank(message = "zaloAccessToken không được để trống")
    String zaloAccessToken;

    String phoneToken;
}
