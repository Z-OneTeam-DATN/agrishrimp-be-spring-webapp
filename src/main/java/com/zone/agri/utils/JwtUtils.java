package com.zone.agri.utils;

import com.zone.agri.exception.CustomAuthenticationException;
import com.zone.agri.security.CustomUserDetail;
import com.zone.agri.security.CustomUserDetailsService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Date;
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


  public String generateAccessToken(UserDetails userDetails) {
    return buildToken(userDetails.getUsername(), accessExpiration);
  }

  public String generateRefreshToken(UserDetails userDetails) {
    return buildToken(userDetails.getUsername(), refreshExpiration);
  }

  public Authentication setAuthentication(String token) {
    Claims payload = parseClaimsFromToken(token);
    String username = payload.getSubject();
    CustomUserDetail customUserDetail = userDetailsService.loadUserByUsername(username);
    return new UsernamePasswordAuthenticationToken(customUserDetail, "", Collections.emptyList());
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
    String username = payload.getSubject();
    return username;
  }

  public boolean isTokenRevoked(String token) {
    return redisTemplate.hasKey(TOKEN_PREFIX + token);
  }

  public void revokeToken(String token) {
    if (token != null && validateToken(token)) {
      Claims claims = parseClaimsFromToken(token);
      redisTemplate.opsForValue().set(TOKEN_PREFIX + token, "revoked",
          claims.getExpiration().getTime(), TimeUnit.SECONDS);
    } else {
      throw new CustomAuthenticationException("Token không hợp lệ");
    }
  }

  private String buildToken(String subject, long expiration) {
    return Jwts.
        builder()
        .issuer(issuer)
        .subject(subject)
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
    if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(BEARER_PREFIX)) {
      return bearerToken.substring(7);
    }
    return null;
  }
}
