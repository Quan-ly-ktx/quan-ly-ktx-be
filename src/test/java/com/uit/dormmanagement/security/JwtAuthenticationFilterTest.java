package com.uit.dormmanagement.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uit.dormmanagement.entities.User;
import com.uit.dormmanagement.enums.UserRole;
import com.uit.dormmanagement.enums.UserStatus;
import com.uit.dormmanagement.services.TokenRevocationService;
import com.uit.dormmanagement.services.UserTokenInvalidationService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

  @Mock private JwtService jwtService;
  @Mock private TokenRevocationService tokenRevocationService;
  @Mock private UserTokenInvalidationService userTokenInvalidationService;
  @Mock private CustomUserDetailsService userDetailsService;
  @Mock private HttpServletRequest request;
  @Mock private HttpServletResponse response;
  @Mock private FilterChain filterChain;

  @InjectMocks private JwtAuthenticationFilter filter;

  @BeforeEach
  void setUp() {
    SecurityContextHolder.clearContext();
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("filter should reject token without sessionGeneration claim")
  void shouldRejectTokenWithoutSessionGeneration() throws Exception {
    String token = "jwt-token-without-gen";
    when(request.getHeader("Authorization")).thenReturn("Bearer " + token);

    TokenPayload payload =
        new TokenPayload(
            UUID.randomUUID(),
            "manager@dorm.vn",
            "manager@dorm.vn",
            "MANAGER",
            "jti-1",
            "ACCESS",
            Instant.now(),
            Instant.now().plusSeconds(3600),
            null); // missing generation!

    when(jwtService.parseAndValidateToken(token)).thenReturn(payload);

    filter.doFilter(request, response, filterChain);

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    verify(filterChain).doFilter(request, response);
  }

  @Test
  @DisplayName(
      "filter should reject token when sessionGeneration does not match Redis current generation")
  void shouldRejectTokenWithMismatchedSessionGeneration() throws Exception {
    UUID userId = UUID.randomUUID();
    String token = "jwt-token-old-gen";
    when(request.getHeader("Authorization")).thenReturn("Bearer " + token);

    TokenPayload payload =
        new TokenPayload(
            userId,
            "manager@dorm.vn",
            "manager@dorm.vn",
            "MANAGER",
            "jti-1",
            "ACCESS",
            Instant.now(),
            Instant.now().plusSeconds(3600),
            "old-generation");

    when(jwtService.parseAndValidateToken(token)).thenReturn(payload);
    when(tokenRevocationService.isAccessTokenBlacklisted("jti-1")).thenReturn(false);
    when(userTokenInvalidationService.isCurrentGeneration(userId, "old-generation"))
        .thenReturn(false);

    filter.doFilter(request, response, filterChain);

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    verify(filterChain).doFilter(request, response);
  }

  @Test
  @DisplayName(
      "filter should authenticate when sessionGeneration matches and token is not blacklisted")
  void shouldAuthenticateWhenSessionGenerationMatches() throws Exception {
    UUID userId = UUID.randomUUID();
    String token = "valid-token";
    when(request.getHeader("Authorization")).thenReturn("Bearer " + token);

    TokenPayload payload =
        new TokenPayload(
            userId,
            "manager@dorm.vn",
            "manager@dorm.vn",
            "MANAGER",
            "jti-1",
            "ACCESS",
            Instant.now(),
            Instant.now().plusSeconds(3600),
            "current-gen");

    when(jwtService.parseAndValidateToken(token)).thenReturn(payload);
    when(tokenRevocationService.isAccessTokenBlacklisted("jti-1")).thenReturn(false);
    when(userTokenInvalidationService.isCurrentGeneration(userId, "current-gen")).thenReturn(true);

    User user =
        User.builder()
            .id(userId)
            .email("manager@dorm.vn")
            .role(UserRole.MANAGER)
            .status(UserStatus.ACTIVE)
            .passwordHash("hash")
            .build();
    UserPrincipal userDetails = UserPrincipal.create(user);
    when(userDetailsService.loadUserById(userId)).thenReturn(userDetails);

    filter.doFilter(request, response, filterChain);

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    assertThat(SecurityContextHolder.getContext().getAuthentication().getName())
        .isEqualTo("manager@dorm.vn");
    verify(filterChain).doFilter(request, response);
  }
}
