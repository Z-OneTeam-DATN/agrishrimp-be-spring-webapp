package com.agrishrimp.agrishrimpbe.service;

import com.agrishrimp.agrishrimpbe.dto.auth.LoginRequest;
import com.agrishrimp.agrishrimpbe.dto.auth.LoginResponse;
import com.agrishrimp.agrishrimpbe.exception.BadRequestException;
import com.agrishrimp.agrishrimpbe.exception.NotFoundException;
import com.agrishrimp.agrishrimpbe.model.User;
import com.agrishrimp.agrishrimpbe.dto.auth.RegisterRequest;
import com.agrishrimp.agrishrimpbe.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import com.agrishrimp.agrishrimpbe.dto.auth.SocialLoginRequest;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.ResponseEntity;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * LOGIN: Email hoặc SĐT + Password
     */
    public LoginResponse login(LoginRequest request) {

        // 1. Validate input
        if (request.getIdentifier() == null || request.getPassword() == null) {
            throw new BadRequestException("Email/Số điện thoại và mật khẩu không được để trống");
        }

        // 2. Tìm user theo Email hoặc Phone
        User user = request.getIdentifier().contains("@")
                ? userRepository.findByEmailAndIsDeletedFalse(request.getIdentifier())
                    .orElseThrow(() -> new NotFoundException("Email không tồn tại"))
                : userRepository.findByPhoneNumberAndIsDeletedFalse(request.getIdentifier())
                    .orElseThrow(() -> new NotFoundException("Số điện thoại không tồn tại"));

        // 3. Check trạng thái tài khoản
        if (!user.getStatus().name().equals("ACTIVE")) {
            throw new BadRequestException("Tài khoản chưa được kích hoạt hoặc đã bị khóa");
        }

        // 4. So khớp mật khẩu
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BadRequestException("Mật khẩu không chính xác");
        }

        // 5. (Sau này) Generate JWT
        String fakeToken = "JWT_TOKEN_SAMPLE";

        // 6. Trả response
        return LoginResponse.builder()
                .userId(user.getUserId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
                .accessToken(fakeToken)
                .build();
    }

    public User register(RegisterRequest request) {
        // 1. Kiểm tra input có trống không
        if (request.getIdentifier() == null || request.getPassword() == null) {
            throw new BadRequestException("Thông tin đăng ký không được để trống");
        }

        // 2. Phân loại Email hay SĐT
        String email = null;
        String phoneNumber = null;

        // Nếu chứa @ -> Là Email
        if (request.getIdentifier().contains("@")) {
            email = request.getIdentifier();
            if (userRepository.existsByEmail(email)) {
                throw new BadRequestException("Email này đã được sử dụng!");
            }
        } else {
            // Ngược lại -> Coi là Số điện thoại
            phoneNumber = request.getIdentifier();
            if (userRepository.existsByPhoneNumber(phoneNumber)) {
                throw new BadRequestException("Số điện thoại này đã được sử dụng!");
            }
        }

        // 3. Tạo Entity User mới
        User newUser = new User();
        newUser.setFullName(request.getFullName());
        newUser.setEmail(email);            // Lưu vào cột email
        newUser.setPhoneNumber(phoneNumber); // Lưu vào cột phone
        newUser.setDateOfBirth(request.getDateOfBirth());
        newUser.setGender(request.getGender());
        newUser.setIsNewsletterSubscribed(request.getIsNewsletterSubscribed());

        // Mặc định các trường khác
        newUser.setStatus(User.Status.ACTIVE);
        newUser.setIsDeleted(false);
        newUser.setAuthProvider("LOCAL"); // Đăng ký trực tiếp

        // 4. Mã hóa mật khẩu (QUAN TRỌNG)
        newUser.setPasswordHash(passwordEncoder.encode(request.getPassword()));

        // 5. Lưu xuống DB
        return userRepository.save(newUser);
    }


    public LoginResponse loginSocial(SocialLoginRequest request) {
        String email = "";
        String fullName = "";
        String avatarUrl = "";
        String providerId = ""; // ID riêng của user trên Google/FB

        // 1. Kiểm tra Token với server của Provider
        if ("GOOGLE".equalsIgnoreCase(request.getProvider())) {
            // Gọi API Google để lấy thông tin user từ token
            String googleApiUrl = "https://www.googleapis.com/oauth2/v3/userinfo?access_token=" + request.getToken();
            RestTemplate restTemplate = new RestTemplate();
            try {
                // Google trả về JSON gồm: sub (id), name, email, picture...
                Map<String, Object> googleUser = restTemplate.getForObject(googleApiUrl, Map.class);
                if (googleUser == null || googleUser.get("email") == null) {
                    throw new BadRequestException("Token Google không hợp lệ hoặc không lấy được email");
                }
                email = (String) googleUser.get("email");
                fullName = (String) googleUser.get("name");
                avatarUrl = (String) googleUser.get("picture");
                providerId = (String) googleUser.get("sub");
            } catch (Exception e) {
                throw new BadRequestException("Lỗi xác thực với Google: " + e.getMessage());
            }

        } else if ("FACEBOOK".equalsIgnoreCase(request.getProvider())) {
            // Gọi API Facebook Graph
            String fbApiUrl = "https://graph.facebook.com/me?fields=id,name,email,picture&access_token=" + request.getToken();
            RestTemplate restTemplate = new RestTemplate();
            try {
                Map<String, Object> fbUser = restTemplate.getForObject(fbApiUrl, Map.class);
                if (fbUser == null || fbUser.get("email") == null) {
                    throw new BadRequestException("Token Facebook không hợp lệ hoặc không có quyền lấy email");
                }
                email = (String) fbUser.get("email");
                fullName = (String) fbUser.get("name");
                providerId = (String) fbUser.get("id");

                // Lấy ảnh FB hơi phức tạp chút (nó lồng trong object picture.data.url)
                Map<String, Object> pictureObj = (Map<String, Object>) fbUser.get("picture");
                if (pictureObj != null) {
                    Map<String, Object> dataObj = (Map<String, Object>) pictureObj.get("data");
                    if (dataObj != null) avatarUrl = (String) dataObj.get("url");
                }

            } catch (Exception e) {
                throw new BadRequestException("Lỗi xác thực với Facebook: " + e.getMessage());
            }
        } else {
            throw new BadRequestException("Chưa hỗ trợ đăng nhập bằng: " + request.getProvider());
        }

        // 2. Xử lý User trong Database (Tìm hoặc Tạo mới)
        // Tìm user theo email
        User user = userRepository.findByEmail(email).orElse(null);

        if (user == null) {
            // a. Nếu chưa có -> TẠO MỚI (Register ngầm)
            user = new User();
            user.setEmail(email);
            user.setFullName(fullName);
            user.setAvatarUrl(avatarUrl);
            user.setAuthProvider(request.getProvider().toUpperCase()); // GOOGLE hoặc FACEBOOK
            user.setProviderId(providerId);
            user.setStatus(User.Status.ACTIVE);
            user.setIsDeleted(false);
            user.setIsNewsletterSubscribed(false);

            // Mật khẩu: Tạo ngẫu nhiên vì user này không dùng pass để login
            user.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));

            user = userRepository.save(user);
        } else {
            // b. Nếu có rồi -> Cập nhật lại thông tin (nếu cần) và cho đăng nhập
            // (Tuỳ chọn: cập nhật lại avatar hoặc tên nếu họ đổi bên Google)
            user.setAvatarUrl(avatarUrl);
            user.setAuthProvider(request.getProvider().toUpperCase()); // Cập nhật provider lần cuối đăng nhập
            userRepository.save(user);
        }

        // 3. Tạo JWT Token (Fake token như bài trước, sau này bạn sẽ thay bằng JWT thật)
        String fakeToken = "JWT_TOKEN_SOCIAL_" + request.getProvider();

        return LoginResponse.builder()
                .userId(user.getUserId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber()) // Có thể null
                .accessToken(fakeToken)
                .build();
    }

}
