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
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
@Tag(name = "Authentication Management", description = "Các API xác thực người dùng: Đăng ký, Đăng nhập, Logout, Refresh Token")
public class AuthController {

    private final AuthService authService;
    private final JwtUtils jwtUtils;
    private final CustomUserDetailsService userDetailsService;
    private final CookieUtils cookieUtils;

    @Operation(summary = "Đăng ký tài khoản mới", description = "Cho phép người dùng đăng ký bằng Email và Mật khẩu. Trả về Access Token và Refresh Token ngay lập tức.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Đăng ký thành công", content = @Content(schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "400", description = "Email đã tồn tại hoặc dữ liệu không hợp lệ"),
            @ApiResponse(responseCode = "500", description = "Lỗi hệ thống nội bộ")
    })
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

    @Operation(summary = "Đăng nhập bằng Google", description = "Xác thực người dùng thông qua Google ID Token. Nếu chưa có tài khoản, hệ thống sẽ tự động tạo.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Đăng nhập thành công", content = @Content(schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "401", description = "Google Token không hợp lệ")
    })
    @PostMapping("/google-login")
    public ResponseEntity<AuthResponse> googleLogin(
            @RequestBody GoogleLoginRequest request,
            HttpServletResponse response
    ) {
        AuthResponse authResponse = authService.loginWithGoogle(request);
        cookieUtils.setAuthCookies(response, authResponse.getAccessToken(), authResponse.getRefreshToken());
        return ResponseEntity.ok(authResponse);
    }

    @Operation(summary = "Đăng xuất (Logout)", description = "Vô hiệu hóa Token hiện tại và xóa Cookie phiên làm việc.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Đăng xuất thành công"),
            @ApiResponse(responseCode = "401", description = "Chưa đăng nhập hoặc Token hết hạn")
    })
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

    @Operation(summary = "Làm mới Access Token", description = "Cấp phát lại Access Token mới khi cái cũ hết hạn bằng cách sử dụng Refresh Token.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cấp Token mới thành công", content = @Content(schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "401", description = "Refresh Token không hợp lệ hoặc đã hết hạn")
    })
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
