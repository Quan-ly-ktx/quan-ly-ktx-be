package com.uit.dormmanagement.security;

import com.uit.dormmanagement.entities.User;
import com.uit.dormmanagement.enums.UserRole;
import com.uit.dormmanagement.enums.UserStatus;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

@Getter
public class UserPrincipal implements UserDetails {
  private final UUID id;
  private final String username;
  private final String email;
  private final String phone;
  private final String passwordHash;
  private final UserRole role;
  private final UserStatus status;
  private final Collection<? extends GrantedAuthority> authorities;

  public UserPrincipal(
      UUID id,
      String username,
      String email,
      String phone,
      String passwordHash,
      UserRole role,
      UserStatus status,
      Collection<? extends GrantedAuthority> authorities) {
    this.id = id;
    this.username = username;
    this.email = email;
    this.phone = phone;
    this.passwordHash = passwordHash;
    this.role = role;
    this.status = status;
    this.authorities = authorities;
  }

  public static UserPrincipal create(User user) {
    List<GrantedAuthority> authorities =
        List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));

    return new UserPrincipal(
        user.getId(),
        user.getUsername(),
        user.getEmail(),
        user.getPhone(),
        user.getPasswordHash(),
        user.getRole(),
        user.getStatus(),
        authorities);
  }

  @Override
  public String getUsername() {
    return username;
  }

  @Override
  public String getPassword() {
    return passwordHash;
  }

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return authorities;
  }

  @Override
  public boolean isAccountNonExpired() {
    return true;
  }

  @Override
  public boolean isAccountNonLocked() {
    return status != UserStatus.LOCKED;
  }

  @Override
  public boolean isCredentialsNonExpired() {
    return true;
  }

  @Override
  public boolean isEnabled() {
    return status == UserStatus.ACTIVE;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    UserPrincipal that = (UserPrincipal) o;
    return Objects.equals(id, that.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }
}
