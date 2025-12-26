package com.agrishrimp.agrishrimpbe.dto.auth;

import lombok.Data;

@Data
public  class LoginRequest {
	private String identifier;
	private String password;
}