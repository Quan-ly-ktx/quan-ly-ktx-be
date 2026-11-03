package com.uit.dormmanagement.configs;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

  @Bean
  public OpenAPI customOpenAPI() {
    final String securitySchemeName = "BearerAuth";

    return new OpenAPI()
        .info(
            new Info()
                .title("KTX Hub / PDMS — API Reference")
                .version("2.1")
                .description(
                    "RESTful API for KTX Hub / PDMS (Private Dormitory Management System). "
                        + "Features: Bed-level tracking (BR-01), strict gender isolation (BR-02), all-inclusive rental (BR-03), and simplified RBAC (BR-11).")
                .contact(
                    new Contact()
                        .name("KTX Hub Engineering Team")
                        .email("support@dormitoryhub.vn")
                        .url("https://dormitoryhub.vn"))
                .license(
                    new License()
                        .name("Apache 2.0")
                        .url("https://www.apache.org/licenses/LICENSE-2.0.html")))
        .servers(
            List.of(
                new Server().url("http://localhost:8080").description("Local Development Server"),
                new Server().url("https://api.dormitoryhub.vn").description("Production Gateway")))
        .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
        .components(
            new Components()
                .addSecuritySchemes(
                    securitySchemeName,
                    new SecurityScheme()
                        .name(securitySchemeName)
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Enter JWT bearer token in format: Bearer <token>")));
  }
}
