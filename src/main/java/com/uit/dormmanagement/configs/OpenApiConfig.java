package com.uit.dormmanagement.configs;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
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
                    """
                    ### Hệ thống Quản lý Ký túc xá Tư nhân & Sleepbox (KTX Hub / PDMS)

                    API RESTful chuẩn hóa phục vụ quản trị vận hành lưu trú tập trung:
                    - **Bed-level Tracking (BR-01):** Quản lý định mức phân mảnh đến từng vị trí giường độc lập (`RoomCode-BedCode`).
                    - **Strict Gender Isolation (BR-02):** Cách ly giới tính sinh học 100% theo phòng.
                    - **All-inclusive Rent & Net Profit (BR-03):** Mô hình giá thuê trọn gói và hạch toán Lợi nhuận ròng thực tế.
                    - **Monthly Billing Cycle (BR-05):** Phát hành đồng loạt ngày 25 hàng tháng, hạn nộp cố định ngày 05 tháng sau.
                    - **Maintenance Prioritization (BR-06):** Thứ tự ưu tiên sự cố kỹ thuật (Khẩn cấp > Cao > Bình thường).
                    - **Simplified RBAC (BR-11):** 3 vai trò tác nhân: `MANAGER` (Desktop Web), `TECHNICIAN` (Mobile App), `TENANT` (Mobile Web).
                    """)
                .contact(
                    new Contact()
                        .name("Ban Kỹ thuật KTX Hub")
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
        .tags(
            List.of(
                new Tag()
                    .name("1. Authentication")
                    .description("Xác thực JWT, đăng nhập và OTP email"),
                new Tag()
                    .name("2. Rooms & Beds")
                    .description("Sơ đồ ma trận phòng, giường và tiện nghi"),
                new Tag().name("3. Tenants").description("Hồ sơ định danh khách thuê đa đối tượng"),
                new Tag()
                    .name("4. Contracts")
                    .description("Tiếp nhận, xếp phòng, gia hạn và thanh lý"),
                new Tag()
                    .name("5. Invoices & Billing")
                    .description("Phát hành hóa đơn trọn gói và đối soát nợ"),
                new Tag()
                    .name("6. Payments")
                    .description("Giao dịch VietQR Napas247 và xác nhận tiền mặt"),
                new Tag()
                    .name("7. Facility Expenses")
                    .description("Hóa đơn điện nước tổng cơ sở và Lợi nhuận ròng"),
                new Tag()
                    .name("8. Maintenance Tickets")
                    .description("Phiếu sự cố điều phối theo thứ tự ưu tiên"),
                new Tag()
                    .name("9. Violations")
                    .description("Biên bản vi phạm nội quy và kỷ luật cá nhân"),
                new Tag()
                    .name("10. Vehicles")
                    .description("Đăng ký phương tiện, ảnh cà vẹt xe và thẻ hầm"),
                new Tag()
                    .name("11. Reports & KPI")
                    .description("Bảng điều khiển phân tích tỷ lệ lấp đầy và doanh thu")))
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
                        .description("Nhập JSON Web Token dạng: Bearer {token}")));
  }
}
