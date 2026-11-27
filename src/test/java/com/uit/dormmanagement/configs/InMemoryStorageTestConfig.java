package com.uit.dormmanagement.configs;

import com.uit.dormmanagement.support.InMemoryPrivateObjectStorage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "app.storage.provider", havingValue = "in-memory")
public class InMemoryStorageTestConfig {

  @Bean
  public InMemoryPrivateObjectStorage inMemoryPrivateObjectStorage() {
    return new InMemoryPrivateObjectStorage();
  }
}
