package com.zone.agri.controller;

import com.zone.agri.dto.response.auth.AuthResponse;
import com.zone.agri.dto.request.auth.ForgotPasswordRequest;
import com.zone.agri.dto.request.auth.GoogleLoginRequest;
import com.zone.agri.dto.request.auth.LoginRequest;
import com.zone.agri.dto.request.auth.ResetPasswordRequest;
import com.zone.agri.dto.request.auth.SignupRequest;
import com.zone.agri.dto.request.auth.TokenRefreshRequest;
import com.zone.agri.dto.response.common.MessageResponse;
import com.zone.agri.exception.CustomAuthenticationException;
import com.zone.agri.security.CustomUserDetail;
import com.zone.agri.security.CustomUserDetailsService;
import com.zone.agri.service.AuthService;
import com.zone.agri.utils.CookieUtils;
import com.zone.agri.utils.JwtUtils;
import com.zone.agri.common.AuthUtils;
import com.zone.agri.dto.response.user.UserDetail;
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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Đăng ký, Đăng nhập, Logout, Làm mới token")
public class AuthController {

    private final AuthService authService;
    private final JwtUtils jwtUtils;
    private final CustomUserDetailsService userDetailsService;
    private final CookieUtils cookieUtils;
    private final com.zone.agri.repository.UserRepository userRepository;
    // ---------------------------------------------------------
    // GET /api/auth/me — Lấy thông tin người dùng hiện tại
    // ---------------------------------------------------------
    @Operation(summary = "Lấy thông tin cá nhân", description = "Trả về thông tin chi tiết TƯƠI MỚI NHẤT từ Database.")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/me")
    public ResponseEntity<UserDetail> getCurrentUser() {

        // 1. Lấy thông tin định danh từ Token
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new CustomAuthenticationException("Chưa đăng nhập hoặc phiên làm việc hết hạn");
        }

        String contact = auth.getName();

        // 2. Chọc thẳng vào Database để lấy dữ liệu mới nhất (đầy đủ Giới tính, Ngày sinh)
        com.zone.agri.entity.User user = userRepository.findByEmail(contact)
                .or(() -> userRepository.findByPhoneNumber(contact))
                .orElseThrow(() -> new CustomAuthenticationException("Không tìm thấy người dùng"));

        // 3. Đóng gói dữ liệu trả về cho Frontend
        UserDetail userDetail = UserDetail.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
                .contact(contact)
                .dateOfBirth(user.getDateOfBirth())
                .gender(user.getGender())
                .avatarUrl(user.getAvatarUrl())
                .status(user.getStatus())
                .role(user.getRole() != null ? new com.zone.agri.dto.response.user.RoleDto(user.getRole()) : null)
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();

        return ResponseEntity.ok(userDetail);
    }

    // ---------------------------------------------------------
    // POST /api/auth/login — Đăng nhập bằng email/SĐT + password
    // ---------------------------------------------------------
    @Operation(summary = "Đăng nhập", description = "Đăng nhập bằng email hoặc số điện thoại kèm mật khẩu và Captcha. Trả về JWT và thông tin người dùng.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Đăng nhập thành công", content = @Content(schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "400", description = "Captcha thất bại hoặc tài khoản dùng OAuth"),
            @ApiResponse(responseCode = "401", description = "Sai thông tin đăng nhập"),
    })
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpServletRequest,
            HttpServletResponse response
    ) {
        AuthResponse authResponse = authService.login(request, httpServletRequest);
        cookieUtils.setAuthCookies(response, authResponse.getAccessToken(), authResponse.getRefreshToken());
        return ResponseEntity.ok(authResponse);
    }

    // ---------------------------------------------------------
    // POST /api/auth/signup — Đăng ký tài khoản mới
    // ---------------------------------------------------------
    @Operation(summary = "Đăng ký tài khoản", description = "Tạo tài khoản mới bằng email/SĐT, mật khẩu và Captcha. Mặc định nhận role CUSTOMER.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Đăng ký thành công", content = @Content(schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "400", description = "Dữ liệu không hợp lệ hoặc Captcha thất bại"),
            @ApiResponse(responseCode = "409", description = "Email hoặc SĐT đã tồn tại"),
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

    // ---------------------------------------------------------
    // POST /api/auth/forgot-password — Gửi email chứa link đặt lại mật khẩu
    // ---------------------------------------------------------
    @Operation(summary = "Quên mật khẩu", description = "Gửi email chứa liên kết đặt lại mật khẩu (hiệu lực 15 phút) nếu email tồn tại trong hệ thống.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Yêu cầu đã được ghi nhận"),
            @ApiResponse(responseCode = "400", description = "Captcha thất bại hoặc email không hợp lệ"),
    })
    @PostMapping("/forgot-password")
    public ResponseEntity<MessageResponse> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request,
            HttpServletRequest httpServletRequest
    ) {
        authService.forgotPassword(request, httpServletRequest);
        return ResponseEntity.ok(new MessageResponse(
                "Nếu email tồn tại trong hệ thống, chúng tôi đã gửi hướng dẫn đặt lại mật khẩu."));
    }

    // ---------------------------------------------------------
    // POST /api/auth/reset-password — Đặt lại mật khẩu bằng token từ email
    // ---------------------------------------------------------
    @Operation(summary = "Đặt lại mật khẩu", description = "Đặt mật khẩu mới bằng token nhận được từ email quên mật khẩu.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Đặt lại mật khẩu thành công"),
            @ApiResponse(responseCode = "400", description = "Token không hợp lệ, đã hết hạn, hoặc mật khẩu xác nhận không khớp"),
    })
    @PostMapping("/reset-password")
    public ResponseEntity<MessageResponse> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok(new MessageResponse("Đặt lại mật khẩu thành công. Vui lòng đăng nhập lại."));
    }

    // ---------------------------------------------------------
    // POST /api/auth/google-login — Đăng nhập bằng Google
    // ---------------------------------------------------------
    @Operation(summary = "Đăng nhập Google", description = "Xác thực qua Google Access Token. Tự động tạo tài khoản nếu chưa tồn tại.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Thành công", content = @Content(schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "400", description = "Email đã đăng ký theo cách khác"),
            @ApiResponse(responseCode = "401", description = "Google Token không hợp lệ"),
    })
    @PostMapping("/google-login")
    public ResponseEntity<AuthResponse> googleLogin(
            @Valid @RequestBody GoogleLoginRequest request,
            HttpServletResponse response
    ) {
        AuthResponse authResponse = authService.loginWithGoogle(request);
        cookieUtils.setAuthCookies(response, authResponse.getAccessToken(), authResponse.getRefreshToken());
        return ResponseEntity.ok(authResponse);
    }

    // ---------------------------------------------------------
    // POST /api/auth/logout — Đăng xuất
    // ---------------------------------------------------------
    @Operation(summary = "Đăng xuất", description = "Thu hồi token hiện tại và xóa cookie phiên làm việc.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Đăng xuất thành công"),
            @ApiResponse(responseCode = "401", description = "Chưa đăng nhập hoặc token hết hạn"),
    })
    @PostMapping("/logout")
    public ResponseEntity<MessageResponse> logout(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        String accessToken = null;
        String refreshToken = null;

        // 1. Lấy Access Token từ Header hoặc Cookie
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            accessToken = authHeader.substring(7);
        }

        if (request.getCookies() != null) {
            for (var cookie : request.getCookies()) {
                if (CookieUtils.ACCESS_TOKEN_COOKIE_NAME.equals(cookie.getName())) {
                    if (accessToken == null) accessToken = cookie.getValue();
                } else if (CookieUtils.REFRESH_TOKEN_COOKIE_NAME.equals(cookie.getName())) {
                    refreshToken = cookie.getValue();
                }
            }
        }

        // 2. Thu hồi Token (đưa vào blacklist trong Redis)
        if (accessToken != null) {
            try {
                jwtUtils.revokeToken(accessToken);
            } catch (Exception ignored) { }
        }

        if (refreshToken != null) {
            try {
                jwtUtils.revokeToken(refreshToken);
            } catch (Exception ignored) { }
        }

        // 3. Xóa Cookie (set maxAge = 0)
        cookieUtils.deleteAuthCookies(response);

        return ResponseEntity.ok(new MessageResponse("Đăng xuất thành công"));
    }

    // ---------------------------------------------------------
    // POST /api/auth/refresh — Làm mới Access Token
    // ---------------------------------------------------------
    @Operation(summary = "Làm mới Access Token", description = "Cấp lại Access Token mới bằng Refresh Token còn hiệu lực.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Thành công", content = @Content(schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "401", description = "Refresh Token không hợp lệ hoặc đã hết hạn"),
    })
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody TokenRefreshRequest request) {
        String refreshToken = request.getRefreshToken();

        if (refreshToken == null || !jwtUtils.validateToken(refreshToken)) {
            throw new CustomAuthenticationException("Refresh token không hợp lệ hoặc đã hết hạn");
        }

        String username = jwtUtils.extractUsername(refreshToken);
        CustomUserDetail userDetails = (CustomUserDetail) userDetailsService.loadUserByUsername(username);

        // Refresh token phat hanh truoc lan doi mat khau gan nhat (tokenVersion lech) -> tu choi,
        // buoc nguoi dung dang nhap lai thay vi am tham cap access token moi bang phien cu.
        if (!java.util.Objects.equals(jwtUtils.extractTokenVersion(refreshToken), userDetails.getUserDetail().getTokenVersion())) {
            throw new CustomAuthenticationException("Phiên đăng nhập đã hết hiệu lực do mật khẩu vừa được thay đổi. Vui lòng đăng nhập lại.");
        }

        // Tai khoan vua bi admin khoa/chan (status != ACTIVE) -> tu choi cap access token moi.
        if (!userDetails.isEnabled()) {
            throw new CustomAuthenticationException("Tài khoản này đã bị khóa. Vui lòng liên hệ quản trị viên.");
        }

        String newAccessToken = jwtUtils.generateAccessToken(userDetails);

        return ResponseEntity.ok(AuthResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(refreshToken)
                .build());
    }
}
