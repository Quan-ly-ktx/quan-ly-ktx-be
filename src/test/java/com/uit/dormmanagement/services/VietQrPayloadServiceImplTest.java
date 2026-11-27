package com.uit.dormmanagement.services;

import static org.assertj.core.api.Assertions.assertThat;

import com.uit.dormmanagement.configs.VietQrProperties;
import com.uit.dormmanagement.dtos.response.invoice.VietQrInfoResponse;
import com.uit.dormmanagement.entities.Invoice;
import com.uit.dormmanagement.enums.InvoiceStatus;
import com.uit.dormmanagement.services.impl.VietQrPayloadServiceImpl;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class VietQrPayloadServiceImplTest {

  private static final String FAKE_BIN = "970405";
  private static final String FAKE_ACCOUNT_NUMBER = "1900123456789";
  private static final String FAKE_ACCOUNT_NAME = "KY TUC XA TEST HUB";

  private VietQrProperties properties;
  private VietQrPayloadServiceImpl service;

  @BeforeEach
  void setUp() {
    properties = new VietQrProperties();
    properties.setBankBin(FAKE_BIN);
    properties.setAccountNumber(FAKE_ACCOUNT_NUMBER);
    properties.setAccountName(FAKE_ACCOUNT_NAME);
    properties.setTransferPrefix("KTX");
    service = new VietQrPayloadServiceImpl(properties);
  }

  @Test
  @DisplayName("buildForInvoice generates spec-compliant EMVCo/NAPAS VietQR TLV with valid CRC-16")
  void buildForInvoice_shouldGenerateValidEmvCoNapasPayloadAndCrc() {
    Invoice invoice =
        Invoice.builder()
            .invoiceCode("INV-202610-HD-2026-0001")
            .billingPeriod(LocalDate.of(2026, 10, 1))
            .amount(new BigDecimal("1500000.00"))
            .dueOn(LocalDate.of(2026, 10, 5))
            .status(InvoiceStatus.DEBT)
            .build();

    Optional<VietQrInfoResponse> result = service.buildForInvoice(invoice);

    assertThat(result).isPresent();
    VietQrInfoResponse qr = result.get();
    assertThat(qr.bankBin()).isEqualTo(FAKE_BIN);
    assertThat(qr.bankName()).isEqualTo("Agribank");
    assertThat(qr.accountNumber()).isEqualTo(FAKE_ACCOUNT_NUMBER);
    assertThat(qr.accountName()).isEqualTo(FAKE_ACCOUNT_NAME);
    assertThat(qr.amount()).isEqualByComparingTo("1500000");
    assertThat(qr.amount().scale()).isZero();
    assertThat(qr.transferContent()).isEqualTo("KTX INV-202610-HD-2026-0001");

    String payload = qr.qrPayload();
    assertThat(payload).isNotBlank();
    assertThat(payload).hasSizeGreaterThan(8);

    // Verify CRC-16/CCITT-FALSE over payload excluding last 4 CRC characters (includes "6304")
    int crcStart = payload.length() - 4;
    assertThat(payload.substring(crcStart - 4, crcStart)).isEqualTo("6304");
    String expectedCrc =
        computeCrc16CcittFalse(payload.substring(0, crcStart).getBytes(StandardCharsets.UTF_8));
    assertThat(payload.substring(crcStart)).isEqualTo(expectedCrc);

    // Strictly parse top-level and nested NAPAS TLV structure
    Map<String, String> rootTags = parseTlv(payload);
    assertThat(rootTags.keySet()).containsExactly("00", "01", "38", "53", "54", "58", "62", "63");
    assertThat(rootTags.get("00")).isEqualTo("01");
    assertThat(rootTags.get("01")).isEqualTo("12");
    assertThat(rootTags.get("53")).isEqualTo("704");
    assertThat(rootTags.get("54")).isEqualTo("1500000");
    assertThat(rootTags.get("58")).isEqualTo("VN");
    assertThat(rootTags.get("63")).isEqualTo(expectedCrc);

    Map<String, String> tag38 = parseTlv(rootTags.get("38"));
    assertThat(tag38.keySet()).containsExactly("00", "01", "02");
    assertThat(tag38.get("00")).isEqualTo("A000000727");
    assertThat(tag38.get("02")).isEqualTo("QRIBFTTA");

    Map<String, String> tag38Sub01 = parseTlv(tag38.get("01"));
    assertThat(tag38Sub01.keySet()).containsExactly("00", "01");
    assertThat(tag38Sub01.get("00")).isEqualTo(FAKE_BIN);
    assertThat(tag38Sub01.get("01")).isEqualTo(FAKE_ACCOUNT_NUMBER);

    Map<String, String> tag62 = parseTlv(rootTags.get("62"));
    assertThat(tag62.keySet()).containsExactly("08");
    assertThat(tag62.get("08")).isEqualTo("KTX INV-202610-HD-2026-0001");
  }

  @Test
  @DisplayName("CRC-16/CCITT-FALSE matches standard 123456789 -> 29B1 test vector")
  void crc16CcittFalse_matchesStandardTestVector() {
    assertThat(
            computeCrc16CcittFalse("123456789".getBytes(StandardCharsets.UTF_8)))
        .isEqualTo("29B1");
  }

  static Stream<Arguments> napasBinBankNames() {
    return Stream.of(
        Arguments.of("970405", "Agribank"),
        Arguments.of("970436", "Vietcombank"),
        Arguments.of("970415", "VietinBank"),
        Arguments.of("970418", "BIDV"),
        Arguments.of("970422", "MB Bank"),
        Arguments.of("970407", "Techcombank"),
        Arguments.of("970416", "ACB"),
        Arguments.of("970432", "VPBank"),
        Arguments.of("970423", "TPBank"),
        Arguments.of("970403", "Sacombank"),
        Arguments.of("970448", "OCB"),
        Arguments.of("970437", "HDBank"),
        Arguments.of("970441", "VIB"),
        Arguments.of("970426", "MSB"),
        Arguments.of("970499", "Ngân hàng (BIN 970499)"));
  }

  @ParameterizedTest
  @MethodSource("napasBinBankNames")
  @DisplayName("Resolves NAPAS BIN map bank names and unknown BIN fallback when bankName unset")
  void buildForPayment_shouldResolveBankNameFromNapasMapOrFallback(
      String bin, String expectedBankName) {
    properties.setBankBin(bin);
    properties.setBankName(null);

    VietQrInfoResponse qr =
        service.buildForPayment("INV-202610-001", new BigDecimal("1000000")).orElseThrow();

    assertThat(qr.bankName()).isEqualTo(expectedBankName);
  }

  @Test
  @DisplayName("Explicit configured bankName overrides NAPAS BIN lookup")
  void buildForPayment_withConfiguredBankName_shouldUseConfiguredValue() {
    properties.setBankBin("970405");
    properties.setBankName("  Agribank Chi Nhánh Thủ Đức  ");

    VietQrInfoResponse qr =
        service.buildForPayment("INV-202610-001", new BigDecimal("1000000")).orElseThrow();

    assertThat(qr.bankName()).isEqualTo("Agribank Chi Nhánh Thủ Đức");
  }

  @Test
  @DisplayName("Normalizes Vietnamese diacritics in prefix/code and truncates memo within EMVCo TLV limit")
  void buildForPayment_shouldNormalizeVietnameseAndTruncateMemo() {
    properties.setTransferPrefix("Đóng   Tiền@Phòng!!   Ký  Túc Xá");
    String longInvoiceCode =
        "INV-202610-HD-2026-0001-EXTRA-LONG-SEGMENT-ABCDEFGHIJKLMNOPQRSTUVWXYZ-0123456789-ABCDEFGHIJK";

    VietQrInfoResponse qr =
        service.buildForPayment(longInvoiceCode, new BigDecimal("9999999999999")).orElseThrow();

    assertThat(qr.transferContent()).startsWith("Dong Tien Phong Ky Tuc Xa INV-202610-");
    assertThat(qr.transferContent()).matches("^[A-Za-z0-9 -]+$");
    assertThat(qr.transferContent()).doesNotContain("  ");
    assertThat(qr.transferContent().getBytes(StandardCharsets.UTF_8).length)
        .isLessThanOrEqualTo(99);

    Map<String, String> rootTags = parseTlv(qr.qrPayload());
    assertThat(rootTags.get("54")).isEqualTo("9999999999999");
    Map<String, String> tag62 = parseTlv(rootTags.get("62"));
    assertThat(tag62.get("08")).isEqualTo(qr.transferContent());

    int crcStart = qr.qrPayload().length() - 4;
    String expectedCrc =
        computeCrc16CcittFalse(
            qr.qrPayload().substring(0, crcStart).getBytes(StandardCharsets.UTF_8));
    assertThat(qr.qrPayload().substring(crcStart)).isEqualTo(expectedCrc);
  }

  @Test
  @DisplayName("Defaults transferPrefix to KTX when blank or null")
  void buildForPayment_whenPrefixBlank_shouldDefaultToKtx() {
    properties.setTransferPrefix("   ");

    VietQrInfoResponse qr =
        service.buildForPayment("INV-202610-HD-001", new BigDecimal("1500000")).orElseThrow();

    assertThat(qr.transferContent()).isEqualTo("KTX INV-202610-HD-001");
  }

  @Test
  @DisplayName("Fails closed on null invoice or non-DEBT status")
  void buildForInvoice_nonDebtOrNull_shouldReturnEmpty() {
    assertThat(service.buildForInvoice(null)).isEmpty();

    Invoice pendingInvoice =
        Invoice.builder()
            .invoiceCode("INV-202610-001")
            .amount(new BigDecimal("1500000"))
            .status(InvoiceStatus.PENDING_CONFIRMATION)
            .build();
    assertThat(service.buildForInvoice(pendingInvoice)).isEmpty();

    Invoice paidInvoice =
        Invoice.builder()
            .invoiceCode("INV-202610-002")
            .amount(new BigDecimal("1500000"))
            .status(InvoiceStatus.PAID)
            .build();
    assertThat(service.buildForInvoice(paidInvoice)).isEmpty();
  }

  @Test
  @DisplayName("Fails closed on invalid invoice code, invalid amount, or amount > 13 digits")
  void buildForPayment_invalidInputs_shouldFailClosed() {
    assertThat(service.buildForPayment(null, new BigDecimal("1500000"))).isEmpty();
    assertThat(service.buildForPayment("   ", new BigDecimal("1500000"))).isEmpty();
    assertThat(service.buildForPayment("!!!", new BigDecimal("1500000"))).isNotEmpty();

    assertThat(service.buildForPayment("INV-1", null)).isEmpty();
    assertThat(service.buildForPayment("INV-1", BigDecimal.ZERO)).isEmpty();
    assertThat(service.buildForPayment("INV-1", new BigDecimal("-1000"))).isEmpty();
    assertThat(service.buildForPayment("INV-1", new BigDecimal("1500000.5"))).isEmpty();
    assertThat(service.buildForPayment("INV-1", new BigDecimal("10000000000000"))).isEmpty();
  }

  @Test
  @DisplayName("Fails closed when VietQR bank config is missing or invalid")
  void buildForPayment_invalidConfig_shouldFailClosed() {
    assertThat(new VietQrPayloadServiceImpl(null).buildForPayment("INV-1", new BigDecimal("1000")))
        .isEmpty();

    properties.setBankBin(null);
    assertThat(service.buildForPayment("INV-1", new BigDecimal("1000"))).isEmpty();
    properties.setBankBin("97040");
    assertThat(service.buildForPayment("INV-1", new BigDecimal("1000"))).isEmpty();
    properties.setBankBin("97040A");
    assertThat(service.buildForPayment("INV-1", new BigDecimal("1000"))).isEmpty();

    properties.setBankBin(FAKE_BIN);
    properties.setAccountNumber(" ");
    assertThat(service.buildForPayment("INV-1", new BigDecimal("1000"))).isEmpty();
    properties.setAccountNumber("12345678901234567890");
    assertThat(service.buildForPayment("INV-1", new BigDecimal("1000"))).isEmpty();
    properties.setAccountNumber("1900-1234-56");
    assertThat(service.buildForPayment("INV-1", new BigDecimal("1000"))).isEmpty();

    properties.setAccountNumber(FAKE_ACCOUNT_NUMBER);
    properties.setAccountName("   ");
    assertThat(service.buildForPayment("INV-1", new BigDecimal("1000"))).isEmpty();
  }

  @Test
  @DisplayName("VietQrProperties.toString never includes accountNumber or accountName")
  void vietQrProperties_toString_shouldRedactSensitiveFields() {
    String text = properties.toString();
    assertThat(text).doesNotContain(FAKE_ACCOUNT_NUMBER);
    assertThat(text).doesNotContain(FAKE_ACCOUNT_NAME);
    assertThat(text).doesNotContain("accountNumber");
    assertThat(text).doesNotContain("accountName");
    assertThat(text).contains("configured=true");
  }

  private static Map<String, String> parseTlv(String raw) {
    Map<String, String> parsed = new LinkedHashMap<>();
    int idx = 0;
    while (idx < raw.length()) {
      assertThat(idx + 4).isLessThanOrEqualTo(raw.length());
      String tag = raw.substring(idx, idx + 2);
      String lengthDigits = raw.substring(idx + 2, idx + 4);
      assertThat(lengthDigits).matches("\\d{2}");
      int byteLen = Integer.parseInt(lengthDigits);
      idx += 4;
      int charEnd = findUtf8ByteEnd(raw, idx, byteLen);
      parsed.put(tag, raw.substring(idx, charEnd));
      idx = charEnd;
    }
    assertThat(idx).isEqualTo(raw.length());
    return parsed;
  }

  private static int findUtf8ByteEnd(String s, int start, int expectedBytes) {
    int byteCount = 0;
    int pos = start;
    while (pos < s.length() && byteCount < expectedBytes) {
      int codePoint = s.codePointAt(pos);
      int charCount = Character.charCount(codePoint);
      byteCount += s.substring(pos, pos + charCount).getBytes(StandardCharsets.UTF_8).length;
      pos += charCount;
    }
    assertThat(byteCount).isEqualTo(expectedBytes);
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
