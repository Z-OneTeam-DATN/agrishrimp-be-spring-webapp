package com.zone.agri.utils;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletResponse;

@Component
public class CookieUtils {

    public static final String ACCESS_TOKEN_COOKIE_NAME = "accessToken";
    public static final String REFRESH_TOKEN_COOKIE_NAME = "refreshToken";

    // ✅ LẤY ĐÚNG CONFIG TỪ application.yml
    @Value("${security.jwt.expiry-time-in-seconds}")
    private long accessExpirationSeconds;

    @Value("${security.jwt.refreshable-duration}")
    private long refreshExpirationSeconds;

    // ✅ Thêm config cookie (có default để khỏi crash nếu thiếu)
    @Value("${app.cookie.secure:false}")
    private boolean isSecure;

    @Value("${app.cookie.same-site:Lax}")
    private String sameSite;

    /**
     * Thiết lập Access Token và Refresh Token vào Cookie
     */
    public void setAuthCookies(HttpServletResponse response, String accessToken, String refreshToken) {
        ResponseCookie accessCookie = buildCookie(
                ACCESS_TOKEN_COOKIE_NAME,
                accessToken,
                accessExpirationSeconds
        );

        ResponseCookie refreshCookie = buildCookie(
                REFRESH_TOKEN_COOKIE_NAME,
                refreshToken,
                refreshExpirationSeconds
        );

        response.addHeader(HttpHeaders.SET_COOKIE, accessCookie.toString());
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());
    }

    /**
     * Xóa Access Token và Refresh Token khỏi Cookie
     */
    public void deleteAuthCookies(HttpServletResponse response) {
        ResponseCookie deleteAccess = buildCookie(ACCESS_TOKEN_COOKIE_NAME, "", 0);
        ResponseCookie deleteRefresh = buildCookie(REFRESH_TOKEN_COOKIE_NAME, "", 0);

        response.addHeader(HttpHeaders.SET_COOKIE, deleteAccess.toString());
        response.addHeader(HttpHeaders.SET_COOKIE, deleteRefresh.toString());
    }

    /**
     * Helper tạo cookie chuẩn bảo mật
     */
    private ResponseCookie buildCookie(String name, String value, long maxAgeSeconds) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)        // JS không đọc được
                .secure(isSecure)      // true khi dùng HTTPS
                .path("/")
                .maxAge(maxAgeSeconds) // ⚠️ PHẢI là SECONDS
                .sameSite(sameSite)    // Lax / Strict / None
                .build();
    }
}
