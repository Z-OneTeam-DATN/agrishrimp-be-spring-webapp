package com.agrishrimp.agrishrimpbe.config;


import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;
import java.util.List;

/**
 * CẤU HÌNH BẢO MẬT CHÍNH (SECURITY CONFIGURATION)
 * Kiểm soát mọi request
 */

@Configuration // [Spring IoC] Chạy đầu tiên khi khởi động
@EnableWebSecurity // [Security] Bộ lọc bảo mật (Security Filter Chain)
public class SecurityConfig {

    /**
     * PASSWORD ENCODER
     * Dùng thuật toán BCrypt: hashing 1 chiều
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }


    /**
     * AUTHENTICATION MANAGER
     * Dùng trong AuthController để kiểm tra
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }


    /**
     * SECURITY FILTER CHAIN
     * Cấu hình chuỗi bộ lọc bảo mật (Security Filter Chain).
     * @param http Đối tượng cấu hình bảo mật của Spring Security.
     * @return SecurityFilterChain Chuỗi lọc đã được build.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 1. TẮT CSRF (Cross-Site Request Forgery)
                // Kiến trúc hiện tại: RESTful API (Stateless), xác thực qua Token (Bearer Token) ở Header,
                // nên CSRF không cần và sẽ gây lỗi 403 cho các request POST/PUT/DELETE từ Client.
                .csrf(AbstractHttpConfigurer::disable)

                // 2. Quản lý Session (Session Management)
                // Cấu hình: STATELESS (Không lưu trạng thái) Tuân thủ nguyên tắc REST
                // Tác động: Mỗi Request gửi lên bắt buộc phải kèm theo thông tin xác thực (VD: JWT Token).
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // 3. Phân quyền Endpoint (Authorization Rules)
                .authorizeHttpRequests(auth -> auth

                        // a.PUBLIC
                        .requestMatchers("/api/auth/**").permitAll()

                        // Cho phép xem tài liệu API
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html").permitAll()

                        // b.PRIVATE
                        .anyRequest().authenticated()
                );

        return http.build();
    }


    /**
     * 4: CẤU HÌNH CORS (CROSS-ORIGIN RESOURCE SHARING)
     * Cấu hình bộ lọc CORS để xử lý vấn đề Same-Origin Policy của trình duyệt.
     * Bean này hoạt động như một Middleware cho phép Frontend (khác domain/port) gọi vào Backend.
     *
     * @return CorsFilter Bean quản lý CORS.
     */
    @Bean
    public CorsFilter corsFilter() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration config = new CorsConfiguration();

        // 1. Allowed Origins: Nguồn được phép
        config.setAllowedOrigins(List.of("http://localhost:3000", "http://localhost:5173"));

        // 2. Allowed Methods (Phương thức được phép)
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));

        // 3. Allowed Headers (Header được phép):
        config.setAllowedHeaders(List.of("*"));

        // 4. Allow Credentials (Cho phép thông tin xác thực):
        config.setAllowCredentials(true);

        // Áp dụng cấu hình trên cho toàn bộ các endpoint (/**) trong hệ thống.
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }
}
