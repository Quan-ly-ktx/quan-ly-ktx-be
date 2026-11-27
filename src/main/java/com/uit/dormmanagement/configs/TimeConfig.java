package com.uit.dormmanagement.configs;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TimeConfig {

  public static final ZoneId DORM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

  @Bean
  @ConditionalOnMissingBean
  public Clock dormClock() {
    return Clock.system(DORM_ZONE);
  }
}
