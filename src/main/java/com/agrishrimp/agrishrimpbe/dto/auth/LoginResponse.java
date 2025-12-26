package com.agrishrimp.agrishrimpbe.dto.auth;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LoginResponse {
    private Long userId;
    private String fullName;
    private String email;
    private String phoneNumber;
    private String accessToken;
}