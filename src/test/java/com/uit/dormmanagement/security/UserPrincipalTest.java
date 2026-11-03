package com.uit.dormmanagement.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.uit.dormmanagement.entities.User;
import com.uit.dormmanagement.enums.UserRole;
import com.uit.dormmanagement.enums.UserStatus;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UserPrincipalTest {

  @Test
  @DisplayName("create() should properly map User properties and Role authority")
  void shouldCreateUserPrincipalFromUser() {
    UUID userId = UUID.randomUUID();
    User user =
        User.builder()
            .id(userId)
            .username("manager01")
            .email("manager@dorm.vn")
            .phone("0901000001")
            .passwordHash("$2a$12$somehash")
            .role(UserRole.MANAGER)
            .status(UserStatus.ACTIVE)
            .build();

    UserPrincipal principal = UserPrincipal.create(user);

    assertThat(principal.getId()).isEqualTo(userId);
    assertThat(principal.getUsername()).isEqualTo("manager01");
    assertThat(principal.getEmail()).isEqualTo("manager@dorm.vn");
    assertThat(principal.getPhone()).isEqualTo("0901000001");
    assertThat(principal.getPassword()).isEqualTo("$2a$12$somehash");
    assertThat(principal.getRole()).isEqualTo(UserRole.MANAGER);
    assertThat(principal.getStatus()).isEqualTo(UserStatus.ACTIVE);
    assertThat(principal.isEnabled()).isTrue();
    assertThat(principal.isAccountNonLocked()).isTrue();
    assertThat(principal.getAuthorities()).extracting("authority").containsExactly("ROLE_MANAGER");
  }

  @Test
  @DisplayName("isAccountNonLocked should return false when status is LOCKED")
  void shouldReportLockedWhenUserStatusIsLocked() {
    User user =
        User.builder()
            .id(UUID.randomUUID())
            .username("locked_user")
            .email("locked@dorm.vn")
            .phone("0909999999")
            .passwordHash("$2a$12$somehash")
            .role(UserRole.TENANT)
            .status(UserStatus.LOCKED)
            .build();

    UserPrincipal principal = UserPrincipal.create(user);

    assertThat(principal.isAccountNonLocked()).isFalse();
    assertThat(principal.isEnabled()).isFalse();
  }

  @Test
  @DisplayName("isEnabled should return false when status is INACTIVE")
  void shouldReportDisabledWhenUserStatusIsInactive() {
    User user =
        User.builder()
            .id(UUID.randomUUID())
            .username("inactive_user")
            .email("inactive@dorm.vn")
            .phone("0908888888")
            .passwordHash("$2a$12$somehash")
            .role(UserRole.TECHNICIAN)
            .status(UserStatus.INACTIVE)
            .build();

    UserPrincipal principal = UserPrincipal.create(user);

    assertThat(principal.isEnabled()).isFalse();
    assertThat(principal.isAccountNonLocked()).isTrue();
  }
}
