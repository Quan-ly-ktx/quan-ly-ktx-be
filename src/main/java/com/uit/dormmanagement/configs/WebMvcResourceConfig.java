package com.uit.dormmanagement.configs;

import java.nio.file.Path;
import java.nio.file.Paths;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcResourceConfig implements WebMvcConfigurer {

  private final String baseDir;

  public WebMvcResourceConfig(@Value("${storage.local.base-dir:uploads}") String baseDir) {
    this.baseDir = (baseDir == null || baseDir.isBlank()) ? "uploads" : baseDir;
  }

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    Path uploadDir = Paths.get(baseDir).toAbsolutePath().normalize();
    String uploadLocation = uploadDir.toUri().toString();
    if (!uploadLocation.endsWith("/")) {
      uploadLocation += "/";
    }

    registry
        .addResourceHandler("/uploads/**")
        .addResourceLocations(uploadLocation, "file:" + baseDir + "/");
  }
}
