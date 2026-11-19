package com.uit.dormmanagement.services;

import static org.assertj.core.api.Assertions.assertThat;

import com.uit.dormmanagement.services.impl.SecureRandomPasswordGenerator;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SecureRandomPasswordGeneratorTest {

  private SecureRandomPasswordGenerator generator;

  @BeforeEach
  void setUp() {
    generator = new SecureRandomPasswordGenerator();
  }

  @Test
  @DisplayName("generate should produce a 12-char password containing all character classes")
  void shouldGeneratePasswordWithRequiredClasses() {
    String password = generator.generate();

    assertThat(password).hasSize(12);
    assertThat(password).matches(".*[A-Z].*"); // uppercase
    assertThat(password).matches(".*[a-z].*"); // lowercase
    assertThat(password).matches(".*[0-9].*"); // digit
    assertThat(password).matches(".*[@$!%*?&#].*"); // special
  }

  @Test
  @DisplayName("generate should produce distinct passwords across calls")
  void shouldGenerateUniquePasswords() {
    Set<String> passwords = new HashSet<>();
    for (int i = 0; i < 50; i++) {
      passwords.add(generator.generate());
    }
    assertThat(passwords).hasSize(50);
  }
}
