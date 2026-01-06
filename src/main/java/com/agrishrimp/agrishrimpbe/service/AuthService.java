package com.agrishrimp.agrishrimpbe.service;
import com.agrishrimp.agrishrimpbe.model.enums.Status;
import com.agrishrimp.agrishrimpbe.dto.auth.*;
import com.agrishrimp.agrishrimpbe.exception.BadRequestException;
import com.agrishrimp.agrishrimpbe.model.User;
import com.agrishrimp.agrishrimpbe.repository.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import com.agrishrimp.agrishrimpbe.service.EmailService;
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RestTemplate restTemplate;
    private final EmailService emailService;

    // --- Cấu hình Social ---
    @Value("${social.google.user-info-uri}")
    private String googleUserInfoUri;

    @Value("${social.facebook.user-info-uri}")
    private String fbUserInfoUri;

    // --- Cấu hình Recaptcha ---
    @Value("${recaptcha.secret-key}")
    private String recaptchaSecret;

    @Value("${google.recaptcha.verify.url}")
    private String recaptchaVerifyUrl;

    @Value("${recaptcha.enabled:true}") // Mặc định là true nếu quên cấu hình
    private boolean isCaptchaEnabled;

    /**
     * LOGIN
     */
    public LoginResponse login(LoginRequest request) {
        // 1. Verify Captcha
        verifyCaptcha(request.getCaptchaToken());

        String genericErrorMsg = "Thông tin đăng nhập không chính xác";

        User user;
        if (request.getIdentifier().contains("@")) {
            user = userRepository.findByEmailAndIsDeletedFalse(request.getIdentifier()).orElse(null);
        } else {
            user = userRepository.findByPhoneNumberAndIsDeletedFalse(request.getIdentifier()).orElse(null);
        }

        if (user == null) {
            throw new BadRequestException(genericErrorMsg);
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BadRequestException(genericErrorMsg);
        }

        if (user.getStatus() != Status.ACTIVE) {
            throw new BadRequestException("Tài khoản đã bị khóa hoặc không khả dụng.");
        }

        String fakeToken = "JWT_TOKEN_SAMPLE_" + user.getUserId();

        return LoginResponse.builder()
                .userId(user.getUserId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
                .avatarUrl(user.getAvatarUrl()) // <--- ĐÃ THÊM: Trả về Avatar
                .accessToken(fakeToken)
                .build();
    }

    /**
     * REGISTER
     */
    public RegisterResponse register(RegisterRequest request) {
        // 1. Verify Captcha
        verifyCaptcha(request.getCaptchaToken());

        String EMAIL_REGEX = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$";
        String PHONE_REGEX = "^(0|\\+84)(\\s|\\.)?((3[2-9])|(5[689])|(7[06-9])|(8[1-689])|(9[0-46-9]))(\\d)(\\s|\\.)?(\\d{3})(\\s|\\.)?(\\d{3})$";

        String email = null;
        String phoneNumber = null;

        if (request.getIdentifier().contains("@")) {
            if (!Pattern.matches(EMAIL_REGEX, request.getIdentifier())) {
                throw new BadRequestException("Định dạng Email không hợp lệ");
            }
            email = request.getIdentifier();
            if (userRepository.existsByEmail(email)) {
                throw new BadRequestException("Email này đã được sử dụng!");
            }
        } else {
            if (!Pattern.matches(PHONE_REGEX, request.getIdentifier())) {
                throw new BadRequestException("Định dạng Số điện thoại không hợp lệ");
            }
            phoneNumber = request.getIdentifier();
            if (userRepository.existsByPhoneNumber(phoneNumber)) {
                throw new BadRequestException("Số điện thoại này đã được sử dụng!");
            }
        }

        User newUser = new User();
        newUser.setFullName(request.getFullName());
        newUser.setEmail(email);
        newUser.setPhoneNumber(phoneNumber);
        newUser.setDateOfBirth(request.getDateOfBirth());
        newUser.setGender(request.getGender());
        newUser.setIsNewsletterSubscribed(request.getIsNewsletterSubscribed());

        // Set ACTIVE luôn (Bỏ qua OTP theo yêu cầu cũ)
        newUser.setStatus(Status.ACTIVE);
        newUser.setIsDeleted(false);
        newUser.setAuthProvider("LOCAL");
        newUser.setPasswordHash(passwordEncoder.encode(request.getPassword()));

        User savedUser = userRepository.save(newUser);

        // Map sang DTO
        return RegisterResponse.builder()
                .userId(savedUser.getUserId())
                .fullName(savedUser.getFullName())
                .email(savedUser.getEmail())
                .phoneNumber(savedUser.getPhoneNumber())
                .status(savedUser.getStatus().name())
                .createdAt(savedUser.getCreatedAt())
                .build();
    }

    /**
     * SOCIAL LOGIN
     */
    public LoginResponse loginSocial(SocialLoginRequest request) {
        String email = "";
        String fullName = "";
        String avatarUrl = "";
        String providerId = "";

        if ("GOOGLE".equalsIgnoreCase(request.getProvider())) {
            String url = googleUserInfoUri + "?access_token=" + request.getToken();
            try {
                Map<String, Object> googleUser = restTemplate.getForObject(url, Map.class);
                if (googleUser == null || googleUser.get("email") == null) {
                    throw new BadRequestException("Token Google không hợp lệ");
                }
                email = (String) googleUser.get("email");
                fullName = (String) googleUser.get("name");
                avatarUrl = (String) googleUser.get("picture");
                providerId = (String) googleUser.get("sub");
            } catch (Exception e) {
                throw new BadRequestException("Lỗi xác thực Google: " + e.getMessage());
            }

        } else if ("FACEBOOK".equalsIgnoreCase(request.getProvider())) {
            String url = fbUserInfoUri + "?fields=id,name,email,picture&access_token=" + request.getToken();
            try {
                Map<String, Object> fbUser = restTemplate.getForObject(url, Map.class);
                if (fbUser == null || fbUser.get("email") == null) {
                    throw new BadRequestException("Token Facebook không hợp lệ");
                }
                email = (String) fbUser.get("email");
                fullName = (String) fbUser.get("name");
                providerId = (String) fbUser.get("id");

                Map<String, Object> pictureObj = (Map<String, Object>) fbUser.get("picture");
                if (pictureObj != null) {
                    Map<String, Object> dataObj = (Map<String, Object>) pictureObj.get("data");
                    if (dataObj != null) avatarUrl = (String) dataObj.get("url");
                }
            } catch (Exception e) {
                throw new BadRequestException("Lỗi xác thực Facebook: " + e.getMessage());
            }
        } else {
            throw new BadRequestException("Provider không hỗ trợ: " + request.getProvider());
        }

        User user = userRepository.findByEmail(email).orElse(null);

        if (user == null) {
            user = new User();
            user.setEmail(email);
            user.setFullName(fullName);
            user.setAvatarUrl(avatarUrl);
            user.setAuthProvider(request.getProvider().toUpperCase());
            user.setProviderId(providerId);
            user.setStatus(Status.ACTIVE);
            user.setIsDeleted(false);
            user.setIsNewsletterSubscribed(false);
            user.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
            user = userRepository.save(user);
        } else {
            if (!user.getAuthProvider().equalsIgnoreCase(request.getProvider())) {
                throw new BadRequestException(
                        "Email này đã được đăng ký bằng " + user.getAuthProvider() +
                                ". Vui lòng đăng nhập bằng phương thức đó.");
            }
            // Cập nhật lại avatar và ID mới nhất từ mạng xã hội
            user.setAvatarUrl(avatarUrl);
            user.setProviderId(providerId);
            userRepository.save(user);
        }

        String fakeToken = "JWT_TOKEN_SOCIAL_" + request.getProvider();

        return LoginResponse.builder()
                .userId(user.getUserId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
                .avatarUrl(user.getAvatarUrl()) // <--- ĐÃ THÊM: Trả về Avatar
                .accessToken(fakeToken)
                .build();
    }

    /**
     * Helper: VERIFY CAPTCHA
     */
    private void verifyCaptcha(String captchaToken) {
        if (!isCaptchaEnabled) {
            return;
        }

        if (captchaToken == null || captchaToken.trim().isEmpty()) {
            throw new BadRequestException("Captcha token không được để trống");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
        map.add("secret", recaptchaSecret);
        map.add("response", captchaToken);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(map, headers);

        try {
            Map response = restTemplate.postForObject(recaptchaVerifyUrl, request, Map.class);
            if (response == null || !Boolean.TRUE.equals(response.get("success"))) {
                throw new BadRequestException("Xác thực Captcha thất bại. Vui lòng thử lại.");
            }
        } catch (Exception e) {
            throw new BadRequestException("Lỗi kết nối đến dịch vụ xác thực Captcha: " + e.getMessage());
        }
    }

    /**
     * FORGOT PASSWORD
     */
    public void forgotPassword(ForgotPasswordRequest request) {
        // 1. Verify Captcha
        verifyCaptcha(request.getCaptchaToken());

        // 2. Tìm User
        User user;
        if (request.getIdentifier().contains("@")) {
            user = userRepository.findByEmailAndIsDeletedFalse(request.getIdentifier()).orElse(null);
        } else {

            user = userRepository.findByPhoneNumberAndIsDeletedFalse(request.getIdentifier()).orElse(null);
        }

        if (user == null) {

            throw new BadRequestException("Không tìm thấy tài khoản.");
        }

        if (user.getEmail() == null || user.getEmail().isEmpty()) {
            throw new BadRequestException("Tài khoản chưa có email để khôi phục.");
        }

        // 3. Tạo token & Thời gian hết hạn (ví dụ: 15 phút)
        String resetToken = UUID.randomUUID().toString();


        user.setResetPasswordToken(resetToken);
        user.setResetPasswordTokenExpiry(LocalDateTime.now().plusMinutes(15));
        userRepository.save(user);


        // 4. Gửi Email
        // Link gửi đi thường dạng: https://your-frontend.com/reset-password?token=...
        emailService.sendResetPasswordEmail(user.getEmail(), resetToken);
    }

    /**
     * MỚI: RESET PASSWORD (Xử lý form tạo mật khẩu mới)
     */
    public void resetPassword(ResetPasswordRequest request) {
        // 1. Kiểm tra mật khẩu xác nhận
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new BadRequestException("Mật khẩu xác nhận không khớp.");
        }

        // 2. Tìm User bằng token
        User user = userRepository.findByResetPasswordToken(request.getToken())
                .orElseThrow(() -> new BadRequestException("Token không hợp lệ hoặc đường dẫn đã thay đổi."));

        // 3. Kiểm tra hạn sử dụng Token
        if (user.getResetPasswordTokenExpiry().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("Đường dẫn khôi phục mật khẩu đã hết hạn. Vui lòng yêu cầu lại.");
        }

        // 4. Cập nhật mật khẩu mới
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));

        // 5. Xóa token để không dùng lại được nữa
        user.setResetPasswordToken(null);
        user.setResetPasswordTokenExpiry(null);

        userRepository.save(user);
    }
}