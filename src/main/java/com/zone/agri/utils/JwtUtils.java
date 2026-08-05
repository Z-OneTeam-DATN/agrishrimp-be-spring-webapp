package com.zone.agri.utils;

import com.zone.agri.exception.CustomAuthenticationException;
import com.zone.agri.security.CustomUserDetail;
import com.zone.agri.security.CustomUserDetailsService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap; // Thêm import
import java.util.Map;     // Thêm import
import java.util.concurrent.TimeUnit;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class JwtUtils {

    @Value("${security.jwt.secret-key}")
    private String secret;

    @Value("${security.jwt.issuer}")
    private String issuer;

    @Value("${security.jwt.expiry-time-in-seconds}")
    private Long accessExpiration;

    @Value("${security.jwt.refreshable-duration}")
    private Long refreshExpiration;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private CustomUserDetailsService userDetailsService;

    private static final String TOKEN_PREFIX = "revoked_token:";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String PURPOSE_CLAIM = "purpose";
    private static final String PASSWORD_RESET_PURPOSE = "PWD_RESET";
    private static final long PASSWORD_RESET_EXPIRATION_SECONDS = 15 * 60;
    private static final String TOKEN_VERSION_CLAIM = "tokenVersion";

    public String generateAccessToken(UserDetails userDetails) {
        CustomUserDetail customUserDetail = (CustomUserDetail) userDetails;

        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", customUserDetail.getUserDetail().getId());
        claims.put("fullName", customUserDetail.getUserDetail().getFullName());
        claims.put("warehouseId", customUserDetail.getUserDetail().getBranchId());
        claims.put("roleSlug", customUserDetail.getUserDetail().getRole() != null
            ? customUserDetail.getUserDetail().getRole().getSlug() : null);
        claims.put(TOKEN_VERSION_CLAIM, customUserDetail.getUserDetail().getTokenVersion());

        return buildToken(customUserDetail.getUsername(), claims, accessExpiration);
    }

    // Kem theo tokenVersion tai thoi diem phat hanh de endpoint /auth/refresh co the phat hien
    // refresh token da "cu" (phat hanh truoc mot lan doi mat khau) va tu choi cap access token moi.
    public String generateRefreshToken(UserDetails userDetails) {
        CustomUserDetail customUserDetail = (CustomUserDetail) userDetails;

        Map<String, Object> claims = new HashMap<>();
        claims.put(TOKEN_VERSION_CLAIM, customUserDetail.getUserDetail().getTokenVersion());

        return buildToken(customUserDetail.getUsername(), claims, refreshExpiration);
    }

    // Doc claim tokenVersion tu token; token cu phat hanh truoc khi co co che nay se khong co claim
    // -> mac dinh 0 de khop voi gia tri mac dinh cua User.tokenVersion, tranh dang xuat hang loat
    // ngay sau khi trien khai tinh nang.
    public Integer extractTokenVersion(String token) {
        try {
            Claims claims = parseClaimsFromToken(token);
            Object value = claims.get(TOKEN_VERSION_CLAIM);
            return value instanceof Number number ? number.intValue() : 0;
        } catch (JwtException e) {
            return 0;
        }
    }

    // Token ngan han (15 phut) dung cho luong "quen mat khau": subject = email, kem claim
    // "purpose" de phan biet voi access/refresh token binh thuong. Sau khi dat lai mat khau
    // thanh cong, token nay bi revokeToken() dua vao blacklist Redis de khong the dung lai (replay).
    public String generatePasswordResetToken(String email) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(PURPOSE_CLAIM, PASSWORD_RESET_PURPOSE);
        return buildToken(email, claims, PASSWORD_RESET_EXPIRATION_SECONDS);
    }

    public boolean isPasswordResetToken(String token) {
        try {
            Claims claims = parseClaimsFromToken(token);
            return PASSWORD_RESET_PURPOSE.equals(claims.get(PURPOSE_CLAIM));
        } catch (JwtException e) {
            return false;
        }
    }

    public Authentication setAuthentication(String token) {
        Claims payload = parseClaimsFromToken(token);
        String username = payload.getSubject();
        CustomUserDetail customUserDetail = (CustomUserDetail) userDetailsService.loadUserByUsername(username);

        return new UsernamePasswordAuthenticationToken(customUserDetail, "", customUserDetail.getAuthorities());
    }

    public boolean validateToken(String token) {
        try {
            parseClaimsFromToken(token);
            return !isTokenExpired(token) && !isTokenRevoked(token);
        } catch (JwtException e) {
            return false;
        }
    }

    public String extractUsername(String token) {
        Claims payload = parseClaimsFromToken(token);
        return payload.getSubject();
    }

    public boolean isTokenRevoked(String token) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(TOKEN_PREFIX + token));
    }

    public void revokeToken(String token) {
        if (token != null && validateToken(token)) {
            Claims claims = parseClaimsFromToken(token);
            long remainingTime = claims.getExpiration().getTime() - System.currentTimeMillis();
            if (remainingTime > 0) {
                redisTemplate.opsForValue().set(TOKEN_PREFIX + token, "revoked",
                        remainingTime, TimeUnit.MILLISECONDS);
            }
        } else {
            throw new CustomAuthenticationException("Token không hợp lệ");
        }
    }

    private String buildToken(String subject, Map<String, Object> claims, long expiration) {
        return Jwts.builder()
                .issuer(issuer)
                .subject(subject)
                .claims(claims)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + (expiration * 1000)))
                .signWith(getSecretKey())
                .compact();
    }

    private SecretKey getSecretKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    private Claims parseClaimsFromToken(String token) {
        return Jwts.parser()
                .verifyWith(getSecretKey())
                .build().parseSignedClaims(token).getPayload();
    }

    public boolean isTokenExpired(String token) {
        try {
            Claims claims = parseClaimsFromToken(token);
            Date expiration = claims.getExpiration();
            return expiration.before(new Date());
        } catch (JwtException e) {
            return true;
        }
    }

    public String extractBearerToken(String bearerToken) {
        if (StringUtils.hasText(bearerToken) && bearerToken.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return bearerToken.substring(7);
        }
        return null;
    }
}