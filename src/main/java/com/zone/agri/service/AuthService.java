package com.zone.agri.service;

import com.zone.agri.dto.auth.AuthResponse;
import com.zone.agri.dto.auth.GoogleLoginRequest;
import com.zone.agri.dto.auth.SignupRequest;
import com.zone.agri.entity.Role;
import com.zone.agri.entity.User;
import com.zone.agri.entity.enums.AuthProvider;
import com.zone.agri.entity.enums.UserStatus;
import com.zone.agri.exception.BadRequestException;
import com.zone.agri.exception.ConflictException;
import com.zone.agri.exception.CustomAuthenticationException;
import com.zone.agri.repository.RoleRepository;
import com.zone.agri.repository.UserRepository;
import com.zone.agri.security.CustomUserDetailsService;
import com.zone.agri.utils.JwtUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.security.core.userdetails.UserDetails;
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

    private final RestTemplate restTemplate = new RestTemplate();

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[\\w-\\.]+@([\\w-]+\\.)+[\\w-]{2,4}$");

    private static final Pattern PHONE_PATTERN =
            Pattern.compile("^\\d{10}$");

    private static final String ROLE_USER = "USER";

    // =========================================================
    // SIGNUP (LOCAL)
    // =========================================================
    @Transactional
    public AuthResponse signup(SignupRequest request, HttpServletRequest httpServletRequest) {

        // 1. Verify Captcha
        if (!captchaService.verifyCaptcha(request.getCaptchaToken(), httpServletRequest)) {
            throw new BadRequestException("Xác thực Captcha thất bại");
        }

        String contact = request.getContact().trim();
        String email = null;
        String phone = null;

        // 2. Validate contact
        if (EMAIL_PATTERN.matcher(contact).matches()) {
            email = contact;
            if (userRepository.existsByEmail(email)) {
                throw new ConflictException("Email này đã tồn tại");
            }
        } else if (PHONE_PATTERN.matcher(contact).matches()) {
            phone = contact;
            if (userRepository.existsByPhoneNumber(phone)) {
                throw new ConflictException("Số điện thoại này đã tồn tại");
            }
        } else {
            throw new BadRequestException("Email hoặc số điện thoại không hợp lệ");
        }

        // 3. Load default role
        Role defaultRole = roleRepository.findBySlug(ROLE_USER)
                .orElseThrow(() -> new BadRequestException("Role USER chưa được cấu hình"));

        // 4. Create user
        User newUser = User.builder()
                .fullName(request.getFullName())
                .email(email)
                .phoneNumber(phone)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .status(UserStatus.ACTIVE)
                .role(defaultRole)
                .avatarUrl(generateSmartAvatar(request.getFullName()))
                .provider(AuthProvider.LOCAL) // Đánh dấu là LOCAL
                .build();

        userRepository.save(newUser);

        // 5. Generate tokens
        String username = (email != null) ? email : phone;
        UserDetails userDetails = userDetailsService.loadUserByUsername(username);

        return AuthResponse.builder()
                .accessToken(jwtUtils.generateAccessToken(userDetails))
                .refreshToken(jwtUtils.generateRefreshToken(userDetails))
                .build();
    }

    // =========================================================
    // GOOGLE LOGIN (FIXED)
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
                    googleApiUrl,
                    HttpMethod.GET,
                    entity,
                    Map.class
            );
            googleUser = response.getBody();
        } catch (Exception e) {
            log.error("Google Login Error: ", e);
            throw new CustomAuthenticationException("Token Google không hợp lệ hoặc đã hết hạn");
        }

        if (googleUser == null || googleUser.get("email") == null) {
            throw new CustomAuthenticationException("Không lấy được email từ Google");
        }

        String email = (String) googleUser.get("email");
        String name = (String) googleUser.get("name");
        String picture = (String) googleUser.get("picture");

        User user = userRepository.findByEmail(email).orElse(null);

        if (user == null) {
            // ====================================================
            // CASE: Chưa tồn tại -> TẠO MỚI (GOOGLE)
            // ====================================================
            Role defaultRole = roleRepository.findBySlug(ROLE_USER)
                    .orElseThrow(() -> new BadRequestException("Role USER chưa được cấu hình"));

            user = User.builder()
                    .email(email)
                    .fullName(name)
                    .avatarUrl(picture)
                    .role(defaultRole)
                    .status(UserStatus.ACTIVE)
                    .provider(AuthProvider.GOOGLE) // Đánh dấu là GOOGLE
                    .passwordHash(passwordEncoder.encode(UUID.randomUUID().toString())) // Random password
                    .build();

            userRepository.save(user);

        } else {
            // ====================================================
            // CASE: Đã tồn tại -> CHECK PROVIDER
            // ====================================================

            // Nếu provider KHÔNG PHẢI là GOOGLE (tức là LOCAL hoặc FACEBOOK...) -> CHẶN
            if (user.getProvider() != AuthProvider.GOOGLE) {
                throw new BadRequestException(
                        "Email này đã được đăng ký bằng tài khoản " + user.getProvider()
                );
            }

            // Nếu là GOOGLE -> Cập nhật avatar nếu thiếu (Logic phụ)
            if (user.getAvatarUrl() == null && picture != null) {
                user.setAvatarUrl(picture);
                userRepository.save(user);
            }
        }

        UserDetails userDetails = userDetailsService.loadUserByUsername(email);

        return AuthResponse.builder()
                .accessToken(jwtUtils.generateAccessToken(userDetails))
                .refreshToken(jwtUtils.generateRefreshToken(userDetails))
                .build();
    }

    // =========================================================
    // AVATAR GENERATOR
    // =========================================================
    private String generateSmartAvatar(String fullName) {
        if (fullName == null || fullName.isEmpty()) return null;
        try {
            return "https://ui-avatars.com/api/?name=" +
                    URLEncoder.encode(fullName, StandardCharsets.UTF_8) +
                    "&background=random&size=200&color=fff";
        } catch (Exception e) {
            return null;
        }
    }
}