package com.uit.dormmanagement.services.impl;

import com.uit.dormmanagement.services.RandomPasswordGenerator;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class SecureRandomPasswordGenerator implements RandomPasswordGenerator {

  private static final String UPPERCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
  private static final String LOWERCASE = "abcdefghijklmnopqrstuvwxyz";
  private static final String DIGITS = "0123456789";
  private static final String SPECIAL = "@$!%*?&#";
  private static final String ALL_CHARS = UPPERCASE + LOWERCASE + DIGITS + SPECIAL;
  private static final int DEFAULT_LENGTH = 12;

  private final SecureRandom random = new SecureRandom();

  @Override
  public String generate() {
    List<Character> chars = new ArrayList<>(DEFAULT_LENGTH);

    // Ensure at least one character from each required category
    chars.add(UPPERCASE.charAt(random.nextInt(UPPERCASE.length())));
    chars.add(LOWERCASE.charAt(random.nextInt(LOWERCASE.length())));
    chars.add(DIGITS.charAt(random.nextInt(DIGITS.length())));
    chars.add(SPECIAL.charAt(random.nextInt(SPECIAL.length())));

    // Fill the remainder
    for (int i = 4; i < DEFAULT_LENGTH; i++) {
      chars.add(ALL_CHARS.charAt(random.nextInt(ALL_CHARS.length())));
    }

    // Shuffle characters
    Collections.shuffle(chars, random);

    StringBuilder sb = new StringBuilder(DEFAULT_LENGTH);
    for (char c : chars) {
      sb.append(c);
    }
    return sb.toString();
  }
}
