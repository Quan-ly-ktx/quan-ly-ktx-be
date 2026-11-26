package com.uit.dormmanagement.utils;

import java.text.Normalizer;
import org.springframework.http.ContentDisposition;

public final class ContentDispositionUtils {

  private static final int MAX_FILENAME_LENGTH = 120;
  private static final String DEFAULT_FILENAME = "document";

  private ContentDispositionUtils() {}

  public static ContentDisposition inline(String originalFilename) {
    return ContentDisposition.inline().filename(sanitizeAsciiFilename(originalFilename)).build();
  }

  public static String sanitizeAsciiFilename(String originalFilename) {
    if (originalFilename == null || originalFilename.isBlank()) {
      return DEFAULT_FILENAME;
    }

    String normalizedSlashes = originalFilename.replace('\\', '/');
    int lastSlash = normalizedSlashes.lastIndexOf('/');
    String nameOnly =
        lastSlash >= 0 ? normalizedSlashes.substring(lastSlash + 1) : normalizedSlashes;

    String ascii =
        Normalizer.normalize(nameOnly, Normalizer.Form.NFD)
            .replaceAll("\\p{M}+", "")
            .replace('đ', 'd')
            .replace('Đ', 'D')
            .replaceAll("[\\p{Cntrl}]", "")
            .replaceAll("[^a-zA-Z0-9._-]", "_")
            .replaceAll("_+", "_");

    while (ascii.startsWith(".")) {
      ascii = ascii.substring(1);
    }
    if (ascii.isBlank() || "..".equals(ascii)) {
      return DEFAULT_FILENAME;
    }
    if (ascii.length() > MAX_FILENAME_LENGTH) {
      int dotIndex = ascii.lastIndexOf('.');
      if (dotIndex > 0 && dotIndex < ascii.length() - 1) {
        String ext = ascii.substring(dotIndex);
        if (ext.length() < 20) {
          int maxBase = MAX_FILENAME_LENGTH - ext.length();
          return ascii.substring(0, Math.max(1, maxBase)) + ext;
        }
      }
      return ascii.substring(0, MAX_FILENAME_LENGTH);
    }
    return ascii;
  }
}
