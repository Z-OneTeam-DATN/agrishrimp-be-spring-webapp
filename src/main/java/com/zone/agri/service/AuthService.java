package com.zone.agri.service;

import com.zone.agri.dto.response.auth.AuthResponse;
import com.zone.agri.dto.request.auth.ForgotPasswordRequest;
import com.zone.agri.dto.request.auth.GoogleLoginRequest;
import com.zone.agri.dto.request.auth.LoginRequest;
import com.zone.agri.dto.request.auth.ResetPasswordRequest;
import com.zone.agri.dto.request.auth.SignupRequest;
import com.zone.agri.entity.Role;
import com.zone.agri.entity.User;
import com.zone.agri.entity.enums.AuthProvider;
import com.zone.agri.entity.enums.UserStatus;
import com.zone.agri.exception.BadRequestException;
import com.zone.agri.exception.ConflictException;
import com.zone.agri.exception.CustomAuthenticationException;
import com.zone.agri.repository.RoleRepository;
import com.zone.agri.repository.UserRepository;
import com.zone.agri.security.CustomUserDetail;
import com.zone.agri.security.CustomUserDetailsService;
import com.zone.agri.utils.JwtUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final CaptchaService captchaService;
    private final JwtUtils jwtUtils;
    private final CustomUserDetailsService userDetailsService;
    private final EmailService emailService;

    private final RestTemplate restTemplate;

    @Value("${app.web-base-url:https://agrishrimp.io.vn}")
    private String webBaseUrl;

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[\\w-\\.]+@([\\w-]+\\.)+[\\w-]{2,4}$");

    private static final String ROLE_USER = "USER";

    // =========================================================
    // ĐĂNG NHẬP (LOCAL: email + password + captcha)
    // =========================================================
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request, HttpServletRequest httpRequest) {

        // 1. Xác thực Captcha
        if (!captchaService.verifyCaptcha(request.getCaptchaToken(), httpRequest)) {
            throw new BadRequestException("Xác thực Captcha thất bại");
        }

        String contact = request.getContact().trim();

        // 2. Tìm user theo email (khong con ho tro dang nhap bang SDT)
        User user = userRepository.findByEmail(contact)
                .orElseThrow(() -> new CustomAuthenticationException("Email hoặc mật khẩu không chính xác"));

        // 3. Kiểm tra provider (chỉ LOCAL mới đăng nhập bằng password)
        if (user.getProvider() != AuthProvider.LOCAL) {
            throw new BadRequestException(
                    "Tài khoản này đăng nhập bằng " + user.getProvider() + ". Vui lòng dùng đăng nhập Google.");
        }

        // 4. Kiểm tra trạng thái tài khoản trước để luôn trả đúng thông báo khóa/vô hiệu hóa.
        checkUserStatus(user);

        // 5. Kiểm tra password
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new CustomAuthenticationException("Email hoặc mật khẩu không chính xác");
        }

        // 6. Tạo token và trả về
        CustomUserDetail userDetails = (CustomUserDetail) userDetailsService.loadUserByUsername(contact);
        return buildAuthResponse(userDetails, user);
    }

    // =========================================================
    // ĐĂNG KÝ (LOCAL)
    // =========================================================
    @Transactional
    public AuthResponse signup(SignupRequest request, HttpServletRequest httpServletRequest) {

        // 1. Xác thực Captcha
        if (!captchaService.verifyCaptcha(request.getCaptchaToken(), httpServletRequest)) {
            throw new BadRequestException("Xác thực Captcha thất bại");
        }

        // 2. Xác nhận mật khẩu
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new BadRequestException("Mật khẩu xác nhận không khớp");
        }

        String contact = request.getContact().trim();

        // 3. Chi chap nhan Email (khong con ho tro dang ky bang SDT)
        if (!EMAIL_PATTERN.matcher(contact).matches()) {
            throw new BadRequestException("Email không hợp lệ");
        }
        String email = contact;
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("Email này đã được sử dụng", true);
        }

        // 4. Lấy role mặc định USER
        Role defaultRole = roleRepository.findBySlug(ROLE_USER)
                .orElseThrow(() -> new BadRequestException("Cấu hình hệ thống lỗi: Role USER chưa được tạo"));

        // 5. Tạo user mới
        User newUser = User.builder()
                .fullName(request.getFullName().trim())
                .email(email)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .status(UserStatus.ACTIVE)
                .role(defaultRole)
                .avatarUrl(generateSmartAvatar(request.getFullName()))
                .provider(AuthProvider.LOCAL)
                .build();

        userRepository.save(newUser);

        try {
            emailService.sendWelcomeEmail(email, newUser.getFullName());
        } catch (Exception e) {
            log.warn("Welcome email was not sent to {}: {}", email, e.getMessage());
        }

        // 6. Tạo token
        CustomUserDetail userDetails = (CustomUserDetail) userDetailsService.loadUserByUsername(email);
        return buildAuthResponse(userDetails, newUser);
    }

    // =========================================================
    // QUEN MAT KHAU: gui email chua link dat lai mat khau
    // Luon tra ve thanh cong (khong tiet lo email co ton tai hay khong) de tranh bi do email
    // (user enumeration). Tai khoan dang nhap qua Google se nhan email thong bao rieng vi khong
    // co mat khau de dat lai.
    // =========================================================
    @Transactional(readOnly = true)
    public void forgotPassword(ForgotPasswordRequest request, HttpServletRequest httpServletRequest) {
        if (!captchaService.verifyCaptcha(request.getCaptchaToken(), httpServletRequest)) {
            throw new BadRequestException("Xác thực Captcha thất bại");
        }

        String email = request.getEmail().trim();
        userRepository.findByEmail(email).ifPresent(user -> {
            if (user.getProvider() == AuthProvider.GOOGLE) {
                emailService.sendPasswordResetGoogleAccountNotice(email, user.getFullName());
                return;
            }
            String token = jwtUtils.generatePasswordResetToken(email);
            String resetLink = webBaseUrl + "/reset-password/confirm?token=" + token;
            emailService.sendPasswordResetEmail(email, user.getFullName(), resetLink);
        });
    }

    // =========================================================
    // DAT LAI MAT KHAU: xac thuc token ngan han roi cap nhat mat khau moi
    // =========================================================
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new BadRequestException("Mật khẩu xác nhận không khớp");
        }

        String token = request.getToken();
        if (!jwtUtils.validateToken(token) || !jwtUtils.isPasswordResetToken(token)) {
            throw new BadRequestException("Liên kết đặt lại mật khẩu không hợp lệ hoặc đã hết hạn");
        }

        String email = jwtUtils.extractUsername(token);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadRequestException("Liên kết đặt lại mật khẩu không hợp lệ hoặc đã hết hạn"));

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        // Tang tokenVersion de vo hieu hoa moi access/refresh token da phat hanh truoc do — buoc
        // dang nhap lai tren tat ca thiet bi (quan trong: day la luong khoi phuc tai khoan, rat
        // co the dang co phien dang nhap la cua ke gia mao can bi day ra ngay).
        user.setTokenVersion((user.getTokenVersion() == null ? 0 : user.getTokenVersion()) + 1);
        userRepository.save(user);

        jwtUtils.revokeToken(token);
    }

    // =========================================================
    // ĐĂNG NHẬP GOOGLE
    // =========================================================
    @Transactional
    public AuthResponse loginWithGoogle(GoogleLoginRequest request) {

        String googleApiUrl = "https://www.googleapis.com/oauth2/v3/userinfo";

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(request.getToken());
        HttpEntity<String> entity = new HttpEntity<>(headers);

        Map<String, Object> googleUser;
        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    googleApiUrl, HttpMethod.GET, entity, Map.class);
            googleUser = response.getBody();
        } catch (Exception e) {
            log.error("Google Login Error: ", e);
            throw new CustomAuthenticationException("Token Google không hợp lệ hoặc đã hết hạn");
        }

        if (googleUser == null || googleUser.get("email") == null) {
            throw new CustomAuthenticationException("Không lấy được thông tin từ Google");
        }

        String email = (String) googleUser.get("email");
        String name = (String) googleUser.get("name");
        String picture = (String) googleUser.get("picture");

        User user = userRepository.findByEmail(email).orElse(null);

        if (user == null) {
            // Người dùng mới — tự động đăng ký
            Role defaultRole = roleRepository.findBySlug(ROLE_USER)
                    .orElseThrow(() -> new BadRequestException("Cấu hình hệ thống lỗi: Role USER chưa được tạo"));

            user = User.builder()
                    .email(email)
                    .fullName(name)
                    .avatarUrl(picture)
                    .role(defaultRole)
                    .status(UserStatus.ACTIVE)
                    .provider(AuthProvider.GOOGLE)
                    .passwordHash(passwordEncoder.encode(UUID.randomUUID().toString()))
                    .build();

            userRepository.save(user);
        } else {
            if (user.getProvider() != AuthProvider.GOOGLE) {
                throw new BadRequestException(
                        "Email này đã đăng ký bằng " + user.getProvider() + ". Vui lòng đăng nhập theo cách đó.");
            }
            checkUserStatus(user);
            if (user.getAvatarUrl() == null && picture != null) {
                user.setAvatarUrl(picture);
                userRepository.save(user);
            }
        }

        CustomUserDetail userDetails = (CustomUserDetail) userDetailsService.loadUserByUsername(email);
        return buildAuthResponse(userDetails, user);
    }

    // =========================================================
    // HELPER: Build AuthResponse kèm thông tin user
    // =========================================================
    private AuthResponse buildAuthResponse(CustomUserDetail userDetails, User user) {
        return AuthResponse.builder()
                .accessToken(jwtUtils.generateAccessToken(userDetails))
                .refreshToken(jwtUtils.generateRefreshToken(userDetails))
                .userId(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
                .contact(user.getEmail() != null ? user.getEmail() : user.getPhoneNumber()) // Linh động email hoặc sđt
                .avatarUrl(user.getAvatarUrl())
                .role(user.getRole() != null ? user.getRole().getSlug() : null)
                .build();
    }

    // =========================================================
    // HELPER: Kiểm tra trạng thái tài khoản
    // =========================================================
    private void checkUserStatus(User user) {
        switch (user.getStatus()) {
            case INACTIVE -> throw new CustomAuthenticationException("Tài khoản này đã bị khóa. Vui lòng liên hệ quản trị viên.");
            case UNVERIFIED -> throw new CustomAuthenticationException("Tài khoản chưa được xác thực");
            default -> {
                /* ACTIVE — OK */ }
        }
    }

    // =========================================================
    // HELPER: Tạo avatar từ tên
    // =========================================================
    private String generateSmartAvatar(String fullName) {
        if (fullName == null || fullName.isBlank())
            return null;
        try {
            return "https://ui-avatars.com/api/?name=" +
                    URLEncoder.encode(fullName, StandardCharsets.UTF_8) +
                    "&background=random&size=200&color=fff";
        } catch (Exception e) {
            return null;
        }
    }

}
