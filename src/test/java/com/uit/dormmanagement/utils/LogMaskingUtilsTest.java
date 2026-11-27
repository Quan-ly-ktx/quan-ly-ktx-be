package com.uit.dormmanagement.utils;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LogMaskingUtilsTest {

  @Test
  @DisplayName("maskEmail should mask local part while keeping domain and never expose full email")
  void maskEmail_shouldMaskSafely() {
    assertThat(LogMaskingUtils.maskEmail(null)).isEqualTo("***");
    assertThat(LogMaskingUtils.maskEmail("")).isEqualTo("***");
    assertThat(LogMaskingUtils.maskEmail("   ")).isEqualTo("***");
    assertThat(LogMaskingUtils.maskEmail("a@dorm.vn")).isEqualTo("***@dorm.vn");
    assertThat(LogMaskingUtils.maskEmail("manager@dorm.vn")).isEqualTo("m***@dorm.vn");
    assertThat(LogMaskingUtils.maskEmail("  nam.nguyen@example.com  "))
        .isEqualTo("n***@example.com");
    assertThat(LogMaskingUtils.maskEmail("@dorm.vn")).isEqualTo("***");
    assertThat(LogMaskingUtils.maskEmail("user@")).isEqualTo("***");
    assertThat(LogMaskingUtils.maskEmail("plain-value")).isEqualTo("***lue");
  }

  @Test
  @DisplayName("maskIdCard should keep at most last 3 digits of CCCD")
  void maskIdCard_shouldMaskSafely() {
    assertThat(LogMaskingUtils.maskIdCard(null)).isEqualTo("***");
    assertThat(LogMaskingUtils.maskIdCard("")).isEqualTo("***");
    assertThat(LogMaskingUtils.maskIdCard("123")).isEqualTo("***");
    assertThat(LogMaskingUtils.maskIdCard("079203001187")).isEqualTo("***187");
    assertThat(LogMaskingUtils.maskIdCard("  079203001187  ")).isEqualTo("***187");
  }

  @Test
  @DisplayName("maskPhone should keep at most last 3 digits")
  void maskPhone_shouldMaskSafely() {
    assertThat(LogMaskingUtils.maskPhone(null)).isEqualTo("***");
    assertThat(LogMaskingUtils.maskPhone("   ")).isEqualTo("***");
    assertThat(LogMaskingUtils.maskPhone("090")).isEqualTo("***");
    assertThat(LogMaskingUtils.maskPhone("0908112245")).isEqualTo("***245");
  }

  @Test
  @DisplayName("maskIdentifier should dispatch emails, 12-digit CCCD, and generic strings safely")
  void maskIdentifier_shouldMaskAllFormats() {
    assertThat(LogMaskingUtils.maskIdentifier(null)).isEqualTo("***");
    assertThat(LogMaskingUtils.maskIdentifier("   ")).isEqualTo("***");
    assertThat(LogMaskingUtils.maskIdentifier("manager.secret@dorm.vn")).isEqualTo("m***@dorm.vn");
    assertThat(LogMaskingUtils.maskIdentifier("079203001187")).isEqualTo("***187");
    assertThat(LogMaskingUtils.maskIdentifier("abc")).isEqualTo("***");
    assertThat(LogMaskingUtils.maskIdentifier("staffuser")).isEqualTo("***ser");
  }
}
