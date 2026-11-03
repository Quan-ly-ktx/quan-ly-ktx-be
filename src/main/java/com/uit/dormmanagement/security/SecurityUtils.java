package com.uit.dormmanagement.security;

import com.uit.dormmanagement.enums.UserRole;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtils {

  private SecurityUtils() {
    // Utility class
  }

  public static Optional<UserPrincipal> getCurrentUserPrincipal() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null
        && authentication.getPrincipal() instanceof UserPrincipal principal) {
      return Optional.of(principal);
    }
    return Optional.empty();
  }

  public static Optional<UUID> getCurrentUserId() {
    return getCurrentUserPrincipal().map(UserPrincipal::getId);
  }

  public static Optional<String> getCurrentUsername() {
    return getCurrentUserPrincipal().map(UserPrincipal::getUsername);
  }

  public static Optional<UserRole> getCurrentUserRole() {
    return getCurrentUserPrincipal().map(UserPrincipal::getRole);
  }

  public static boolean isAuthenticated() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    return authentication != null
        && authentication.isAuthenticated()
        && authentication.getPrincipal() instanceof UserPrincipal;
  }
}
