package com.uit.dormmanagement.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.uit.dormmanagement.entities.Tenant;
import com.uit.dormmanagement.entities.User;
import com.uit.dormmanagement.enums.UserRole;
import com.uit.dormmanagement.enums.UserStatus;
import com.uit.dormmanagement.repositories.TenantRepository;
import com.uit.dormmanagement.repositories.UserRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private TenantRepository tenantRepository;

  @InjectMocks private CustomUserDetailsService userDetailsService;

  @Test
  @DisplayName("loadUserByUsername masks CCCD and email in DEBUG logs and exception messages")
  void loadUserByUsername_shouldMaskSensitiveIdentifiersInLogsAndExceptions() {
    String rawCccd = "079203001187";
    String rawEmail = "staff.secret@dorm.vn";

    User tenantUser =
        User.builder()
            .id(UUID.randomUUID())
            .email("tenant@dorm.vn")
            .passwordHash("hash")
            .role(UserRole.TENANT)
            .status(UserStatus.ACTIVE)
            .build();
    Tenant tenant =
        Tenant.builder().id(UUID.randomUUID()).user(tenantUser).idCardNumber(rawCccd).build();

    when(userRepository.findByEmail(rawCccd)).thenReturn(Optional.empty());
    when(tenantRepository.findByIdCardNumber(rawCccd)).thenReturn(Optional.of(tenant));
    when(userRepository.findByEmail(rawEmail)).thenReturn(Optional.empty());
    when(tenantRepository.findByIdCardNumber(rawEmail)).thenReturn(Optional.empty());

    Logger logger = (Logger) LoggerFactory.getLogger(CustomUserDetailsService.class);
    Level previousLevel = logger.getLevel();
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    logger.setLevel(Level.DEBUG);

    try {
      UserDetails loaded = userDetailsService.loadUserByUsername(rawCccd);
      assertThat(loaded).isNotNull();

      assertThatThrownBy(() -> userDetailsService.loadUserByUsername(rawEmail))
          .isInstanceOf(UsernameNotFoundException.class)
          .hasMessageContaining("s***@dorm.vn")
          .hasMessageNotContaining(rawEmail);

      List<String> messages =
          appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
      assertThat(messages).anySatisfy(msg -> assertThat(msg).contains("***187"));
      assertThat(messages).anySatisfy(msg -> assertThat(msg).contains("s***@dorm.vn"));
      assertThat(messages).noneMatch(msg -> msg.contains(rawCccd) || msg.contains(rawEmail));
    } finally {
      logger.detachAppender(appender);
      logger.setLevel(previousLevel);
    }
  }
}
