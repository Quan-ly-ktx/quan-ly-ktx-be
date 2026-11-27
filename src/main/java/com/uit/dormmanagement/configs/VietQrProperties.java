package com.uit.dormmanagement.configs;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.vietqr")
public class VietQrProperties {

  private String bankBin;
  private String bankName;
  private String accountNumber;
  private String accountName;
  private String transferPrefix = "KTX";

  @Override
  public String toString() {
    boolean configured =
        bankBin != null
            && !bankBin.isBlank()
            && accountNumber != null
            && !accountNumber.isBlank()
            && accountName != null
            && !accountName.isBlank();
    return "VietQrProperties[bankBin="
        + (bankBin == null || bankBin.isBlank() ? "<unset>" : bankBin.trim())
        + ", transferPrefix="
        + transferPrefix
        + ", configured="
        + configured
        + "]";
  }
}
