package com.uit.dormmanagement.security;

import com.uit.dormmanagement.entities.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

  private final SecretKey signingKey;
  private final long accessTokenExpirationMs;
  private final long refreshTokenExpirationMs;

  @Autowired
  public JwtService(
      @Value("${app.jwt.secret}") String secret,
      @Value("${app.jwt.access-token-expiration:86400000}") long accessTokenExpirationMs,
      @Value("${app.jwt.refresh-token-expiration:604800000}") long refreshTokenExpirationMs) {
    if (secret == null || secret.isBlank()) {
      throw new IllegalArgumentException("JWT secret cannot be null or blank");
    }
    byte[] keyBytes;
    try {
      keyBytes = Decoders.BASE64.decode(secret);
      if (keyBytes.length < 32) {
        keyBytes = secret.getBytes(StandardCharsets.UTF_8);
      }
    } catch (Exception e) {
      keyBytes = secret.getBytes(StandardCharsets.UTF_8);
    }
    if (keyBytes.length < 32) {
      throw new IllegalArgumentException("JWT secret key must be at least 256 bits (32 bytes)");
    }
    this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    this.accessTokenExpirationMs = accessTokenExpirationMs;
    this.refreshTokenExpirationMs = refreshTokenExpirationMs;
  }

  public JwtService(String secret) {
    this(secret, 86400000L, 604800000L);
  }

  public String generateAccessToken(User user, String jti) {
    Instant now = Instant.now();
    Instant expiry = now.plusMillis(accessTokenExpirationMs);

    return Jwts.builder()
        .subject(user.getId().toString())
        .id(jti)
        .claim("username", user.getUsername())
        .claim("email", user.getEmail())
        .claim("role", user.getRole().name())
        .claim("type", "ACCESS")
        .issuedAt(Date.from(now))
        .expiration(Date.from(expiry))
        .signWith(signingKey)
        .compact();
  }

  public String generateRefreshToken(User user, String jti) {
    Instant now = Instant.now();
    Instant expiry = now.plusMillis(refreshTokenExpirationMs);

    return Jwts.builder()
        .subject(user.getId().toString())
        .id(jti)
        .claim("username", user.getUsername())
        .claim("type", "REFRESH")
        .issuedAt(Date.from(now))
        .expiration(Date.from(expiry))
        .signWith(signingKey)
        .compact();
  }

  public TokenPayload parseAndValidateToken(String token) {
    Claims claims =
        Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token).getPayload();

    String subject = claims.getSubject();
    UUID userId = (subject != null) ? UUID.fromString(subject) : null;
    String username = claims.get("username", String.class);
    String email = claims.get("email", String.class);
    String role = claims.get("role", String.class);
    String jti = claims.getId() != null ? claims.getId() : claims.get("jti", String.class);
    String tokenType =
        claims.get("type", String.class) != null
            ? claims.get("type", String.class)
            : claims.get("tokenType", String.class);
    Instant issuedAt = claims.getIssuedAt() != null ? claims.getIssuedAt().toInstant() : null;
    Instant expiresAt = claims.getExpiration() != null ? claims.getExpiration().toInstant() : null;

    return new TokenPayload(userId, username, email, role, jti, tokenType, issuedAt, expiresAt);
  }

  public long getRemainingExpirationMs(String token) {
    try {
      Claims claims =
          Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token).getPayload();
      Date expiration = claims.getExpiration();
      if (expiration == null) {
        return 0L;
      }
      long remaining = expiration.getTime() - System.currentTimeMillis();
      return Math.max(0L, remaining);
    } catch (ExpiredJwtException e) {
      return 0L;
    } catch (Exception e) {
      return 0L;
    }
  }

  public long getAccessTokenExpirationSeconds() {
    return accessTokenExpirationMs / 1000;
  }

  public long getRefreshTokenExpirationSeconds() {
    return refreshTokenExpirationMs / 1000;
  }
}
