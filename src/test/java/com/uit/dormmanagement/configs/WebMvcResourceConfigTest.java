package com.uit.dormmanagement.configs;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;

class WebMvcResourceConfigTest {

  @Test
  @DisplayName("addResourceHandlers should register /uploads/** handler")
  void addResourceHandlers_shouldRegisterUploadsPattern() {
    WebMvcResourceConfig config = new WebMvcResourceConfig("uploads");
    GenericApplicationContext context = new GenericApplicationContext();
    context.refresh();

    ResourceHandlerRegistry registry =
        new ResourceHandlerRegistry(context, new MockServletContext());

    config.addResourceHandlers(registry);

    assertThat(registry.hasMappingForPattern("/uploads/**")).isTrue();
  }
}
