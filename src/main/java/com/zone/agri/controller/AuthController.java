package com.zone.agri.controller;

import com.zone.agri.dto.auth.AuthResponse;
import com.zone.agri.dto.auth.GoogleLoginRequest;
import com.zone.agri.dto.auth.SignupRequest;
import com.zone.agri.dto.auth.TokenRefreshRequest;
import com.zone.agri.dto.common.MessageResponse;
import com.zone.agri.exception.CustomAuthenticationException;
import com.zone.agri.security.CustomUserDetail;
import com.zone.agri.security.CustomUserDetailsService;
import com.zone.agri.service.AuthService;
import com.zone.agri.utils.CookieUtils;
import com.zone.agri.utils.JwtUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "1. Authentication", description = "API quản lý xác thực: Đăng ký, Đăng nhập, Refresh Token")
public class AuthController {

    private final AuthService authService;
    private final JwtUtils jwtUtils;
    private final CustomUserDetailsService userDetailsService;
    private final CookieUtils cookieUtils;

    @Operation(summary = "Đăng ký tài khoản", description = "Đăng ký bằng Email hoặc SĐT, trả về AccessToken để đăng nhập ngay lập tức.")
    @PostMapping("/signup")
    public ResponseEntity<AuthResponse> signup(
            @Valid @RequestBody SignupRequest request,
            HttpServletRequest httpServletRequest,
            HttpServletResponse response
    ) {
        AuthResponse authResponse = authService.signup(request, httpServletRequest);
        cookieUtils.setAuthCookies(response, authResponse.getAccessToken(), authResponse.getRefreshToken());
        return ResponseEntity.ok(authResponse);
    }

    // --- 2. GOOGLE LOGIN ---
    @Operation(summary = "Đăng nhập bằng Google", description = "Gửi ID Token từ Google, server sẽ xác thực và trả về AccessToken.")
    @PostMapping("/google-login")
    public ResponseEntity<AuthResponse> googleLogin(
            @RequestBody GoogleLoginRequest request,
            HttpServletResponse response
    ) {
        AuthResponse authResponse = authService.loginWithGoogle(request);
        cookieUtils.setAuthCookies(response, authResponse.getAccessToken(), authResponse.getRefreshToken());
        return ResponseEntity.ok(authResponse);
    }

//    // --- 2. ĐĂNG NHẬP ---
//    @Operation(summary = "Đăng nhập hệ thống", description = "Trả về Access Token và Refresh Token để truy cập các API khác.")
//    @ApiResponses(value = {
//            @ApiResponse(responseCode = "200", description = "Đăng nhập thành công"),
//            @ApiResponse(responseCode = "401", description = "Sai email hoặc mật khẩu")
//    })
//
//    @PostMapping("/login")
//    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
//        AuthResponse response = authService.login(request);
//        return ResponseEntity.ok(response);
//    }
//
//    // --- 3. LẤY THÔNG TIN BẢN THÂN ---
//    @Operation(summary = "Lấy thông tin người dùng hiện tại", description = "Yêu cầu phải có Access Token hợp lệ.")
//    @SecurityRequirement(name = "bearerAuth") // Icon ổ khóa báo hiệu cần Token
//    @GetMapping("/me")
//    public ResponseEntity<UserOutDto> me() {
//        return ResponseEntity.ok(userService.getMe());
//    }

    // --- 4. ĐĂNG XUẤT ---
    @Operation(summary = "Đăng xuất", description = "Vô hiệu hóa Token và xóa Cookie.")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/logout")
    public ResponseEntity<MessageResponse> logout(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        String token = null;
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
        }
        else if (request.getCookies() != null) {
            for (var cookie : request.getCookies()) {
                if (CookieUtils.ACCESS_TOKEN_COOKIE_NAME.equals(cookie.getName())) {
                    token = cookie.getValue();
                    break;
                }
            }
        }
        if (token != null) {
            jwtUtils.revokeToken(token);
        }
        cookieUtils.deleteAuthCookies(response);
        return ResponseEntity.ok(new MessageResponse("Logout successful"));
    }

    // --- 5. LÀM MỚI TOKEN ---
    @Operation(summary = "Làm mới Access Token", description = "Dùng Refresh Token để lấy Access Token mới khi cái cũ hết hạn.")
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@RequestBody TokenRefreshRequest request) {
        String refreshToken = request.getRefreshToken();

        if (refreshToken == null || !jwtUtils.validateToken(refreshToken)) {
            throw new CustomAuthenticationException("Refresh token không hợp lệ hoặc đã hết hạn");
        }

        String username = jwtUtils.extractUsername(refreshToken);
        CustomUserDetail userDetails = (CustomUserDetail) userDetailsService.loadUserByUsername(username);

        String newAccessToken = jwtUtils.generateAccessToken(userDetails);

        return ResponseEntity.ok(new AuthResponse(newAccessToken, refreshToken));
    }
}