package com.zone.agri.dto.request.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TokenRefreshRequest {
    @NotBlank(message = "Refresh token không được để trống")
    String refreshToken;
}
