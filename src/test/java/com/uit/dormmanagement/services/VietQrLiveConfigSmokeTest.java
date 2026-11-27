package com.uit.dormmanagement.services;

import static org.assertj.core.api.Assertions.assertThat;

import com.uit.dormmanagement.configs.VietQrProperties;
import com.uit.dormmanagement.dtos.response.invoice.VietQrInfoResponse;
import com.uit.dormmanagement.services.impl.VietQrPayloadServiceImpl;
import io.github.cdimascio.dotenv.Dotenv;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@Tag("vietqr-smoke")
@EnabledIfEnvironmentVariable(named = "VIETQR_SMOKE_TEST_ENABLED", matches = "true")
class VietQrLiveConfigSmokeTest {

  private static final String SYNTHETIC_INVOICE_CODE = "INV-TEST-202611-0001";
  private static final BigDecimal SMOKE_AMOUNT = new BigDecimal("10000");

  @Test
  @DisplayName(
      "Local .env VietQR configuration produces spec-compliant EMVCo/NAPAS payload without leaking secrets")
  void liveVietQrConfig_buildsValidEmvCoPayloadAndRedactsProperties() {
    Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();

    String bankBin = resolveRequired("VIETQR_BANK_BIN", dotenv).trim();
    String accountNumber = resolveRequired("VIETQR_ACCOUNT_NUMBER", dotenv).trim();
    String accountName = resolveRequired("VIETQR_ACCOUNT_NAME", dotenv).trim();
    String configuredBankName = resolveOptional("VIETQR_BANK_NAME", dotenv);
    String configuredTransferPrefix = resolveOptional("VIETQR_TRANSFER_PREFIX", dotenv);

    VietQrProperties properties = new VietQrProperties();
    properties.setBankBin(bankBin);
    properties.setAccountNumber(accountNumber);
    properties.setAccountName(accountName);
    if (configuredBankName != null) {
      properties.setBankName(configuredBankName);
    }
    if (configuredTransferPrefix != null) {
      properties.setTransferPrefix(configuredTransferPrefix);
    }

    VietQrPayloadServiceImpl service = new VietQrPayloadServiceImpl(properties);
    Optional<VietQrInfoResponse> result =
        service.buildForPayment(SYNTHETIC_INVOICE_CODE, SMOKE_AMOUNT);

    assertThat(result)
        .withFailMessage("VietQR payload generation failed for configured .env properties")
        .isPresent();

    VietQrInfoResponse qr = result.get();
    assertThat(qr.bankBin()).matches("^\\d{6}$");
    assertThat(qr.bankName()).isNotBlank();
    if ("970405".equals(qr.bankBin()) && configuredBankName == null) {
      assertThat(qr.bankName()).isEqualTo("Agribank");
    }
    assertThat(qr.amount()).isEqualByComparingTo(SMOKE_AMOUNT);
    assertThat(qr.amount().scale()).isZero();
    assertThat(qr.transferContent()).endsWith(SYNTHETIC_INVOICE_CODE);

    String payload = qr.qrPayload();
    assertThat(payload).withFailMessage("Generated qrPayload must not be blank").isNotBlank();
    assertThat(payload.length() > 8)
        .withFailMessage("Generated qrPayload length is unexpectedly short")
        .isTrue();

    int crcStart = payload.length() - 4;
    assertThat("6304".equals(payload.substring(crcStart - 4, crcStart)))
        .withFailMessage("EMVCo CRC prefix 6304 missing before checksum")
        .isTrue();
    String expectedCrc =
        computeCrc16CcittFalse(payload.substring(0, crcStart).getBytes(StandardCharsets.UTF_8));
    assertThat(expectedCrc.equals(payload.substring(crcStart)))
        .withFailMessage("EMVCo CRC-16/CCITT-FALSE checksum mismatch")
        .isTrue();

    Map<String, String> rootTags = parseTlv(payload);
    assertThat(rootTags.keySet())
        .withFailMessage("Unexpected EMVCo root tag set")
        .containsExactly("00", "01", "38", "53", "54", "58", "62", "63");
    assertThat(rootTags.get("00")).isEqualTo("01");
    assertThat(rootTags.get("01")).isEqualTo("12");
    assertThat(rootTags.get("53")).isEqualTo("704");
    assertThat(rootTags.get("54")).isEqualTo("10000");
    assertThat(rootTags.get("58")).isEqualTo("VN");

    Map<String, String> tag38 = parseTlv(rootTags.get("38"));
    assertThat(tag38.keySet()).containsExactly("00", "01", "02");
    assertThat(tag38.get("00")).isEqualTo("A000000727");
    assertThat(tag38.get("02")).isEqualTo("QRIBFTTA");

    Map<String, String> tag38Sub01 = parseTlv(tag38.get("01"));
    assertThat(tag38Sub01.keySet()).containsExactly("00", "01");
    assertThat(tag38Sub01.get("00")).isEqualTo(qr.bankBin());
    assertThat(accountNumber.equals(tag38Sub01.get("01")))
        .withFailMessage("EMVCo tag 38-01 subtag 01 does not match configured VietQR account")
        .isTrue();

    Map<String, String> tag62 = parseTlv(rootTags.get("62"));
    assertThat(tag62.keySet()).containsExactly("08");
    assertThat(tag62.get("08")).isEqualTo(qr.transferContent());

    String propsString = properties.toString();
    assertThat(!propsString.contains(accountNumber))
        .withFailMessage("VietQrProperties.toString() leaked accountNumber")
        .isTrue();
    assertThat(!propsString.contains(accountName))
        .withFailMessage("VietQrProperties.toString() leaked accountName")
        .isTrue();
    assertThat(propsString).contains("configured=true");
  }

  private static String resolveRequired(String name, Dotenv dotenv) {
    String envValue = System.getenv(name);
    String value = (envValue != null && !envValue.isBlank()) ? envValue : dotenv.get(name);
    if (value == null || value.isBlank()) {
      throw new IllegalStateException(
          "Missing required environment variable for VietQR smoke test: " + name);
    }
    return value;
  }

  private static String resolveOptional(String name, Dotenv dotenv) {
    String envValue = System.getenv(name);
    String value = (envValue != null && !envValue.isBlank()) ? envValue : dotenv.get(name);
    return (value != null && !value.isBlank()) ? value.trim() : null;
  }

  private static Map<String, String> parseTlv(String raw) {
    assertThat(raw != null).withFailMessage("TLV payload segment must not be null").isTrue();
    Map<String, String> parsed = new LinkedHashMap<>();
    int idx = 0;
    while (idx < raw.length()) {
      assertThat(idx + 4 <= raw.length()).withFailMessage("Truncated EMVCo TLV header").isTrue();
      String tag = raw.substring(idx, idx + 2);
      String lengthDigits = raw.substring(idx + 2, idx + 4);
      assertThat(lengthDigits.matches("\\d{2}"))
          .withFailMessage("Non-numeric EMVCo TLV length for tag " + tag)
          .isTrue();
      int byteLen = Integer.parseInt(lengthDigits);
      idx += 4;
      int charEnd = findUtf8ByteEnd(raw, idx, byteLen, tag);
      parsed.put(tag, raw.substring(idx, charEnd));
      idx = charEnd;
    }
    assertThat(idx == raw.length())
        .withFailMessage("EMVCo TLV contains unexpected trailing bytes")
        .isTrue();
    return parsed;
  }

  private static int findUtf8ByteEnd(String s, int start, int expectedBytes, String tag) {
    int byteCount = 0;
    int pos = start;
    while (pos < s.length() && byteCount < expectedBytes) {
      int codePoint = s.codePointAt(pos);
      int charCount = Character.charCount(codePoint);
      byteCount += s.substring(pos, pos + charCount).getBytes(StandardCharsets.UTF_8).length;
      pos += charCount;
    }
    assertThat(byteCount == expectedBytes)
        .withFailMessage("EMVCo TLV byte length mismatch for tag " + tag)
        .isTrue();
    return pos;
  }

  private static String computeCrc16CcittFalse(byte[] bytes) {
    int crc = 0xFFFF;
    for (byte b : bytes) {
      crc ^= (b & 0xFF) << 8;
      for (int i = 0; i < 8; i++) {
        crc = ((crc & 0x8000) != 0) ? ((crc << 1) ^ 0x1021) & 0xFFFF : (crc << 1) & 0xFFFF;
      }
    }
    return String.format("%04X", crc);
  }
}
