package com.uit.dormmanagement.services.impl;

import com.uit.dormmanagement.configs.VietQrProperties;
import com.uit.dormmanagement.dtos.response.invoice.VietQrInfoResponse;
import com.uit.dormmanagement.entities.Invoice;
import com.uit.dormmanagement.enums.InvoiceStatus;
import com.uit.dormmanagement.services.VietQrPayloadService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class VietQrPayloadServiceImpl implements VietQrPayloadService {

  private static final String DEFAULT_TRANSFER_PREFIX = "KTX";
  private static final String PAYLOAD_FORMAT_INDICATOR = "01";
  private static final String POINT_OF_INITIATION_DYNAMIC = "12";
  private static final String NAPAS_AID = "A000000727";
  private static final String NAPAS_SERVICE_CODE_ACCOUNT = "QRIBFTTA";
  private static final String TRANSACTION_CURRENCY_VND = "704";
  private static final String COUNTRY_CODE_VN = "VN";
  private static final String CRC_PREFIX = "6304";
  private static final int MAX_AMOUNT_LENGTH = 13;
  // Tag 62 has a 2-digit TLV length (<= 99) and wraps subtag "08" + 2-digit length (4 bytes).
  private static final int MAX_TRANSFER_CONTENT_BYTES = 95;
  private static final int MAX_TLV_VALUE_BYTES = 99;

  private static final Pattern BIN_PATTERN = Pattern.compile("^\\d{6}$");
  private static final Pattern ACCOUNT_NUMBER_PATTERN = Pattern.compile("^[A-Za-z0-9]{1,19}$");
  private static final Pattern COMBINING_MARKS_PATTERN = Pattern.compile("\\p{M}+");
  private static final Pattern NON_MEMO_CHARS_PATTERN = Pattern.compile("[^A-Za-z0-9 -]");
  private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");

  private static final Map<String, String> NAPAS_BIN_TO_BANK_NAME =
      Map.ofEntries(
          Map.entry("970405", "Agribank"),
          Map.entry("970436", "Vietcombank"),
          Map.entry("970415", "VietinBank"),
          Map.entry("970418", "BIDV"),
          Map.entry("970422", "MB Bank"),
          Map.entry("970407", "Techcombank"),
          Map.entry("970416", "ACB"),
          Map.entry("970432", "VPBank"),
          Map.entry("970423", "TPBank"),
          Map.entry("970403", "Sacombank"),
          Map.entry("970448", "OCB"),
          Map.entry("970437", "HDBank"),
          Map.entry("970441", "VIB"),
          Map.entry("970426", "MSB"));

  private final VietQrProperties properties;

  @Override
  public Optional<VietQrInfoResponse> buildForInvoice(Invoice invoice) {
    if (invoice == null || invoice.getStatus() != InvoiceStatus.DEBT) {
      return Optional.empty();
    }
    return buildForPayment(invoice.getInvoiceCode(), invoice.getAmount());
  }

  @Override
  public Optional<VietQrInfoResponse> buildForPayment(String invoiceCode, BigDecimal amount) {
    if (properties == null || isBlank(invoiceCode)) {
      return Optional.empty();
    }

    String bankBin = trimToNull(properties.getBankBin());
    String accountNumber = trimToNull(properties.getAccountNumber());
    String accountName = trimToNull(properties.getAccountName());
    if (!isValidConfig(bankBin, accountNumber, accountName)) {
      return Optional.empty();
    }

    BigDecimal normalizedAmount = normalizePositiveIntegerVnd(amount);
    if (normalizedAmount == null) {
      return Optional.empty();
    }
    String amountStr = normalizedAmount.toPlainString();
    if (amountStr.length() > MAX_AMOUNT_LENGTH) {
      return Optional.empty();
    }

    String transferContent = buildTransferContent(properties.getTransferPrefix(), invoiceCode);
    if (transferContent.isEmpty()) {
      return Optional.empty();
    }

    String bankName = resolveBankName(bankBin, properties.getBankName());
    String qrPayload = buildEmvCoQrPayload(bankBin, accountNumber, amountStr, transferContent);

    return Optional.of(
        VietQrInfoResponse.builder()
            .bankBin(bankBin)
            .bankName(bankName)
            .accountNumber(accountNumber)
            .accountName(accountName)
            .amount(normalizedAmount)
            .transferContent(transferContent)
            .qrPayload(qrPayload)
            .build());
  }

  private static boolean isValidConfig(String bankBin, String accountNumber, String accountName) {
    return bankBin != null
        && BIN_PATTERN.matcher(bankBin).matches()
        && accountNumber != null
        && ACCOUNT_NUMBER_PATTERN.matcher(accountNumber).matches()
        && accountName != null;
  }

  private static BigDecimal normalizePositiveIntegerVnd(BigDecimal amount) {
    if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
      return null;
    }
    if (amount.stripTrailingZeros().scale() > 0) {
      return null;
    }
    return amount.setScale(0, RoundingMode.UNNECESSARY);
  }

  private static String resolveBankName(String bankBin, String configuredBankName) {
    String trimmedConfigured = trimToNull(configuredBankName);
    if (trimmedConfigured != null) {
      return trimmedConfigured;
    }
    return NAPAS_BIN_TO_BANK_NAME.getOrDefault(bankBin, "Ngân hàng (BIN " + bankBin + ")");
  }

  private static String buildTransferContent(String configuredPrefix, String invoiceCode) {
    String prefix =
        isBlank(configuredPrefix) ? DEFAULT_TRANSFER_PREFIX : configuredPrefix.trim();
    String rawMemo = prefix + " " + invoiceCode.trim();
    String normalized = sanitizeAsciiMemo(rawMemo);
    return truncateUtf8Bytes(normalized, MAX_TRANSFER_CONTENT_BYTES).trim();
  }

  private static String sanitizeAsciiMemo(String raw) {
    String replacedVietnameseD = raw.replace('đ', 'd').replace('Đ', 'D');
    String decomposed = Normalizer.normalize(replacedVietnameseD, Normalizer.Form.NFD);
    String asciiOnly = COMBINING_MARKS_PATTERN.matcher(decomposed).replaceAll("");
    String cleaned = NON_MEMO_CHARS_PATTERN.matcher(asciiOnly).replaceAll(" ");
    return WHITESPACE_PATTERN.matcher(cleaned).replaceAll(" ").trim();
  }

  private static String truncateUtf8Bytes(String asciiText, int maxBytes) {
    byte[] utf8 = asciiText.getBytes(StandardCharsets.UTF_8);
    if (utf8.length <= maxBytes) {
      return asciiText;
    }
    int end = Math.min(asciiText.length(), maxBytes);
    while (end > 0
        && asciiText.substring(0, end).getBytes(StandardCharsets.UTF_8).length > maxBytes) {
      end--;
    }
    return asciiText.substring(0, end);
  }

  private static String buildEmvCoQrPayload(
      String bankBin, String accountNumber, String amountStr, String transferContent) {
    String beneficiaryAccount = tlv("00", bankBin) + tlv("01", accountNumber);
    String napasInfo =
        tlv("00", NAPAS_AID)
            + tlv("01", beneficiaryAccount)
            + tlv("02", NAPAS_SERVICE_CODE_ACCOUNT);
    String additionalData = tlv("08", transferContent);

    String payloadWithoutCrc =
        tlv("00", PAYLOAD_FORMAT_INDICATOR)
            + tlv("01", POINT_OF_INITIATION_DYNAMIC)
            + tlv("38", napasInfo)
            + tlv("53", TRANSACTION_CURRENCY_VND)
            + tlv("54", amountStr)
            + tlv("58", COUNTRY_CODE_VN)
            + tlv("62", additionalData)
            + CRC_PREFIX;

    return payloadWithoutCrc + calculateCrc16CcittFalse(payloadWithoutCrc);
  }

  private static String tlv(String tag, String value) {
    byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
    if (bytes.length == 0 || bytes.length > MAX_TLV_VALUE_BYTES) {
      throw new IllegalArgumentException(
          "EMVCo TLV value length out of bounds for tag " + tag + ": " + bytes.length);
    }
    return String.format(Locale.ROOT, "%s%02d%s", tag, bytes.length, value);
  }

  static String calculateCrc16CcittFalse(String payload) {
    byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
    int crc = 0xFFFF;
    for (byte b : bytes) {
      crc ^= (b & 0xFF) << 8;
      for (int bit = 0; bit < 8; bit++) {
        if ((crc & 0x8000) != 0) {
          crc = ((crc << 1) ^ 0x1021) & 0xFFFF;
        } else {
          crc = (crc << 1) & 0xFFFF;
        }
      }
    }
    return String.format(Locale.ROOT, "%04X", crc & 0xFFFF);
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private static String trimToNull(String value) {
    if (isBlank(value)) {
      return null;
    }
    return value.trim();
  }
}
