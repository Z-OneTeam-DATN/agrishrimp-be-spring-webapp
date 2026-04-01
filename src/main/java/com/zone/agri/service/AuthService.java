package com.zone.agri.service;

import com.zone.agri.dto.request.auth.ZaloAuthRequest;
import com.zone.agri.dto.response.auth.AuthResponse;
import com.zone.agri.dto.response.auth.ZaloAuthResponse;
import org.springframework.dao.DataIntegrityViolationException;
import com.zone.agri.dto.request.auth.GoogleLoginRequest;
import com.zone.agri.dto.request.auth.LoginRequest;
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

    @Value("${zalo.app-secret}")
    private String zaloAppSecret;

    private final RestTemplate restTemplate;

    private static final String ZALO_VERIFY_URL = "https://graph.zalo.me/v2.0/me?fields=id";
    private static final String ZALO_PHONE_URL   = "https://graph.zalo.me/v2.0/me/info";

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[\\w-\\.]+@([\\w-]+\\.)+[\\w-]{2,4}$");

    private static final Pattern PHONE_PATTERN =
            Pattern.compile("^\\d{10}$");

    private static final String ROLE_USER = "USER";

    // =========================================================
    // ĐĂNG NHẬP (LOCAL: email/phone + password + captcha)
    // =========================================================
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request, HttpServletRequest httpRequest) {

        // 1. Xác thực Captcha
        if (!captchaService.verifyCaptcha(request.getCaptchaToken(), httpRequest)) {
            throw new BadRequestException("Xác thực Captcha thất bại");
        }

        String contact = request.getContact().trim();

        // 2. Tìm user theo email hoặc SĐT
        User user = userRepository.findByEmail(contact)
                .or(() -> userRepository.findByPhoneNumber(contact))
                .orElseThrow(() -> new CustomAuthenticationException("Email hoặc mật khẩu không chính xác"));

        // 3. Kiểm tra provider (chỉ LOCAL mới đăng nhập bằng password)
        if (user.getProvider() != AuthProvider.LOCAL) {
            throw new BadRequestException(
                    "Tài khoản này đăng nhập bằng " + user.getProvider() + ". Vui lòng dùng đăng nhập Google."
            );
        }

        // 4. Kiểm tra password
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new CustomAuthenticationException("Email hoặc mật khẩu không chính xác");
        }

        // 5. Kiểm tra trạng thái tài khoản
        checkUserStatus(user);

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
        String email = null;
        String phone = null;

        // 3. Phân loại và kiểm tra trùng lặp
        if (EMAIL_PATTERN.matcher(contact).matches()) {
            email = contact;
            if (userRepository.existsByEmail(email)) {
                throw new ConflictException("Email này đã được sử dụng");
            }
        } else if (PHONE_PATTERN.matcher(contact).matches()) {
            phone = contact;
            if (userRepository.existsByPhoneNumber(phone)) {
                throw new ConflictException("Số điện thoại này đã được sử dụng");
            }
        } else {
            throw new BadRequestException("Email hoặc số điện thoại không hợp lệ");
        }

        // 4. Lấy role mặc định USER
        Role defaultRole = roleRepository.findBySlug(ROLE_USER)
                .orElseThrow(() -> new BadRequestException("Cấu hình hệ thống lỗi: Role USER chưa được tạo"));

        // 5. Tạo user mới
        User newUser = User.builder()
                .fullName(request.getFullName().trim())
                .email(email)
                .phoneNumber(phone)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .status(UserStatus.ACTIVE)
                .role(defaultRole)
                .avatarUrl(generateSmartAvatar(request.getFullName()))
                .provider(AuthProvider.LOCAL)
                .build();

        userRepository.save(newUser);

        // 6. Tạo token
        String username = (email != null) ? email : phone;
        CustomUserDetail userDetails = (CustomUserDetail) userDetailsService.loadUserByUsername(username);
        return buildAuthResponse(userDetails, newUser);
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
                    googleApiUrl, HttpMethod.GET, entity, Map.class
            );
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
                        "Email này đã đăng ký bằng " + user.getProvider() + ". Vui lòng đăng nhập theo cách đó."
                );
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
            case BANNED -> throw new CustomAuthenticationException("Tài khoản đã bị khóa vĩnh viễn");
            case INACTIVE -> throw new CustomAuthenticationException("Tài khoản tạm thời bị vô hiệu hóa");
            case UNVERIFIED -> throw new CustomAuthenticationException("Tài khoản chưa được xác thực");
            default -> { /* ACTIVE — OK */ }
        }
    }

    // =========================================================
    // HELPER: Tạo avatar từ tên
    // =========================================================
    private String generateSmartAvatar(String fullName) {
        if (fullName == null || fullName.isBlank()) return null;
        try {
            return "https://ui-avatars.com/api/?name=" +
                    URLEncoder.encode(fullName, StandardCharsets.UTF_8) +
                    "&background=random&size=200&color=fff";
        } catch (Exception e) {
            return null;
        }
    }

    // =========================================================
    // ĐĂNG NHẬP ZALO MINI APP
    // =========================================================

    public ZaloAuthResponse loginWithZaloUserInfo(ZaloAuthRequest request) {

        // 1. Verify + lấy phone TRƯỚC khi vào transaction (các bước này gọi Zalo API, không cần TX)
        verifyZaloUserId(request.getZaloAccessToken(), request.getUserId());
        String normalizedPhone = normalizePhoneNumber(
                getPhoneFromZalo(request.getZaloAccessToken(), request.getPhoneToken()));

        try {
            return doZaloLogin(request, normalizedPhone);
        } catch (DataIntegrityViolationException e) {
            // Race condition: concurrent request đã tạo user cùng lúc — retry lookup
            log.warn("Race condition trên Zalo login zaloId={}, retrying", request.getUserId());
            return doZaloLogin(request, normalizedPhone);
        }
    }

    @Transactional
    protected ZaloAuthResponse doZaloLogin(ZaloAuthRequest request, String normalizedPhone) {

        // 2. Tìm hoặc tạo user
        User user = userRepository.findByZaloId(request.getUserId())
                .orElseGet(() -> userRepository.findByPhoneNumber(normalizedPhone).orElse(null));

        if (user == null) {
            // Tạo user mới
            Role defaultRole = roleRepository.findBySlug(ROLE_USER)
                    .orElseThrow(() -> new BadRequestException("Cấu hình hệ thống lỗi: Role USER chưa được tạo"));

            String displayName = (request.getName() != null && !request.getName().isBlank())
                    ? request.getName().trim() : "Zalo User";
            String avatarUrl = (request.getAvatar() != null && !request.getAvatar().isBlank())
                    ? request.getAvatar() : generateSmartAvatar(displayName);

            user = User.builder()
                    .fullName(displayName)
                    .phoneNumber(normalizedPhone)
                    .avatarUrl(avatarUrl)
                    .zaloId(request.getUserId())
                    .provider(AuthProvider.ZALO)
                    .role(defaultRole)
                    .status(UserStatus.ACTIVE)
                    .passwordHash(passwordEncoder.encode(UUID.randomUUID().toString()))
                    .build();

            userRepository.save(user);
            log.info("Tạo tài khoản Zalo mới: zaloId={}, phone={}", request.getUserId(), normalizedPhone);
        } else {
            // Kiểm tra trạng thái tài khoản
            checkUserStatus(user);

            // FIX ACCOUNT TAKEOVER: nếu phone này đã linked với zaloId KHÁC → reject
            if (user.getZaloId() != null && !user.getZaloId().equals(request.getUserId())) {
                log.warn("Zalo ID conflict: phone={}, dbZaloId={}, requestZaloId={}",
                        normalizedPhone, user.getZaloId(), request.getUserId());
                throw new BadRequestException(
                        "Số điện thoại này đã liên kết với một tài khoản Zalo khác. Vui lòng liên hệ hỗ trợ.");
            }

            // Liên kết / cập nhật thông tin
            boolean changed = false;

            if (user.getZaloId() == null) {
                user.setZaloId(request.getUserId());
                changed = true;
            }
            if (request.getName() != null && !request.getName().isBlank()
                    && !request.getName().trim().equals(user.getFullName())) {
                user.setFullName(request.getName().trim());
                changed = true;
            }
            if (request.getAvatar() != null && !request.getAvatar().isBlank()
                    && !request.getAvatar().equals(user.getAvatarUrl())) {
                user.setAvatarUrl(request.getAvatar());
                changed = true;
            }
            if (!normalizedPhone.equals(user.getPhoneNumber())) {
                user.setPhoneNumber(normalizedPhone);
                changed = true;
            }
            if (changed) {
                userRepository.save(user);
            }
            log.info("Đăng nhập Zalo thành công: userId={}, zaloId={}", user.getId(), request.getUserId());
        }

        // 3. Generate JWT nội bộ — subject = phoneNumber
        CustomUserDetail userDetails = (CustomUserDetail) userDetailsService.loadUserByUsername(normalizedPhone);
        String accessToken = jwtUtils.generateAccessTokenFromZalo(userDetails, request.getUserId());

        return ZaloAuthResponse.builder()
                .accessToken(accessToken)
                .userId(user.getId())
                .fullName(user.getFullName())
                .avatar(user.getAvatarUrl())
                .phone(user.getPhoneNumber())
                .hasPaymentPassword(false)
                .build();
    }

    // =========================================================
    // HELPER: Xác thực userId từ Zalo access token
    // =========================================================
    private void verifyZaloUserId(String zaloAccessToken, String expectedUserId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("access_token", zaloAccessToken);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        Map<?, ?> body;
        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    ZALO_VERIFY_URL, HttpMethod.GET, entity, Map.class
            );
            body = response.getBody();
        } catch (Exception e) {
            log.error("Lỗi khi verify Zalo access token: {}", e.getMessage());
            throw new BadRequestException("Zalo access token không hợp lệ hoặc đã hết hạn");
        }

        if (body == null || body.get("id") == null) {
            throw new BadRequestException("Không lấy được thông tin user từ Zalo");
        }

        String returnedId = String.valueOf(body.get("id"));
        if (!expectedUserId.equals(returnedId)) {
            log.warn("Zalo userId không khớp: expected={}, returned={}", expectedUserId, returnedId);
            throw new BadRequestException("userId không khớp với Zalo access token");
        }
    }

    // =========================================================
    // HELPER: Lấy số điện thoại từ Zalo API
    // =========================================================
    private String getPhoneFromZalo(String zaloAccessToken, String phoneToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("access_token", zaloAccessToken);
        headers.set("code", phoneToken);
        headers.set("secret_key", zaloAppSecret);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        Map<?, ?> body;
        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    ZALO_PHONE_URL, HttpMethod.GET, entity, Map.class
            );
            body = response.getBody();
        } catch (Exception e) {
            log.error("Lỗi khi lấy số điện thoại từ Zalo: {}", e.getMessage());
            throw new BadRequestException("Không thể lấy số điện thoại từ Zalo");
        }

        if (body == null || body.get("data") == null) {
            throw new BadRequestException("Zalo không trả về thông tin số điện thoại");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) body.get("data");
        Object numberObj = data.get("number");

        if (numberObj == null || String.valueOf(numberObj).isBlank()) {
            throw new BadRequestException("Số điện thoại từ Zalo bị rỗng hoặc không hợp lệ");
        }

        return String.valueOf(numberObj);
    }

    // =========================================================
    // HELPER: Chuẩn hoá số điện thoại Zalo (+84 → 0)
    // =========================================================
    private String normalizePhoneNumber(String phone) {
        if (phone == null) return null;
        String cleaned = phone.trim().replaceAll("\\s+", "");
        if (cleaned.startsWith("+84")) {
            cleaned = "0" + cleaned.substring(3);
        } else if (cleaned.startsWith("84") && cleaned.length() == 11) {
            cleaned = "0" + cleaned.substring(2);
        }
        if (!cleaned.matches("^0\\d{9}$")) {
            throw new BadRequestException("Số điện thoại không đúng định dạng Việt Nam: " + phone);
        }
        return cleaned;
    }
}
