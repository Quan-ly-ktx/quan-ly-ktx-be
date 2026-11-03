package com.uit.dormmanagement.security;

import com.uit.dormmanagement.entities.User;
import com.uit.dormmanagement.repositories.UserRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

  private final UserRepository userRepository;

  @Override
  @Transactional(readOnly = true)
  public UserDetails loadUserByUsername(String identifier) throws UsernameNotFoundException {
    log.debug("Loading user by identifier: {}", identifier);
    if (identifier == null || identifier.isBlank()) {
      throw new UsernameNotFoundException("Identifier cannot be null or empty");
    }

    String trimmed = identifier.trim();
    Optional<User> userOpt = userRepository.findByUsername(trimmed);
    if (userOpt.isEmpty()) {
      userOpt = userRepository.findByEmail(trimmed);
    }
    if (userOpt.isEmpty()) {
      userOpt = userRepository.findByPhone(trimmed);
    }

    return userOpt
        .map(UserPrincipal::create)
        .orElseThrow(
            () ->
                new UsernameNotFoundException(
                    "Không tìm thấy người dùng với định danh: " + identifier));
  }

  @Transactional(readOnly = true)
  public UserPrincipal loadUserById(UUID userId) throws UsernameNotFoundException {
    log.debug("Loading user by id: {}", userId);
    return userRepository
        .findById(userId)
        .map(UserPrincipal::create)
        .orElseThrow(
            () -> new UsernameNotFoundException("Không tìm thấy người dùng với ID: " + userId));
  }
}
