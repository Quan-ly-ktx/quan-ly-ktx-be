package com.uit.dormmanagement.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uit.dormmanagement.support.RedisTestContainerSupport;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class OpenApiContractVerificationTest {

  @DynamicPropertySource
  static void registerRedis(DynamicPropertyRegistry registry) {
    RedisTestContainerSupport.registerRedisProperties(registry);
  }

  @Autowired private MockMvc mockMvc;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  @DisplayName(
      "GET /v3/api-docs exposes Phase 2, Phase 3, and Phase 4 endpoints accurately and omits PUT /api/v1/admin/vehicles/{id}")
  void openApiDocs_matchesLivePhase2Phase3AndPhase4Contract() throws Exception {
    MvcResult result = mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn();

    JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
    JsonNode paths = root.get("paths");
    assertThat(paths).isNotNull();

    // Auth & Users
    assertThat(paths.has("/api/v1/auth/login")).isTrue();
    assertThat(paths.get("/api/v1/auth/login").has("post")).isTrue();
    assertThat(paths.has("/api/v1/auth/refresh-token")).isTrue();
    assertThat(paths.has("/api/v1/auth/logout")).isTrue();
    assertThat(paths.has("/api/v1/auth/logout-all")).isTrue();
    assertThat(paths.has("/api/v1/auth/change-password")).isTrue();
    assertThat(paths.has("/api/v1/auth/forgot-password")).isTrue();
    assertThat(paths.has("/api/v1/auth/verify-forgot-password-otp")).isTrue();
    assertThat(paths.has("/api/v1/auth/reset-password")).isTrue();
    assertThat(paths.has("/api/v1/users/me")).isTrue();
    assertThat(paths.has("/api/v1/users/technicians")).isTrue();
    assertThat(paths.has("/api/v1/users/technicians/{id}")).isTrue();
    assertThat(paths.has("/api/v1/users/technicians/{id}/status")).isTrue();
    assertThat(paths.has("/api/v1/users/technicians/{id}/reset-password")).isTrue();

    // Rooms, Beds & Amenities
    assertThat(paths.has("/api/v1/admin/rooms")).isTrue();
    assertThat(paths.has("/api/v1/admin/rooms/archived")).isTrue();
    assertThat(paths.has("/api/v1/admin/rooms/matrix")).isTrue();
    assertThat(paths.has("/api/v1/admin/rooms/{id}")).isTrue();
    assertThat(paths.has("/api/v1/admin/rooms/{id}/restore")).isTrue();
    assertThat(paths.has("/api/v1/admin/rooms/{id}/permanent")).isTrue();
    assertThat(paths.has("/api/v1/admin/rooms/{id}/incorrect")).isTrue();
    assertThat(paths.has("/api/v1/admin/beds/{id}/status")).isTrue();
    assertThat(paths.has("/api/v1/admin/amenities")).isTrue();

    // Tenants
    assertThat(paths.has("/api/v1/admin/tenants")).isTrue();
    assertThat(paths.has("/api/v1/admin/tenants/{id}")).isTrue();
    assertThat(paths.has("/api/v1/admin/tenants/{id}/reset-password")).isTrue();
    assertThat(paths.has("/api/v1/tenant/profile")).isTrue();
    assertThat(paths.get("/api/v1/tenant/profile").has("patch")).isTrue();

    // Vehicles & Change Requests (note PATCH methods and F-02 PUT absence)
    assertThat(paths.has("/api/v1/tenant/vehicles/me")).isTrue();
    assertThat(paths.has("/api/v1/tenant/vehicles")).isTrue();
    assertThat(paths.has("/api/v1/tenant/vehicles/{vehicleId}/evidence/{side}")).isTrue();
    assertThat(paths.has("/api/v1/tenant/vehicle-change-requests")).isTrue();
    assertThat(paths.has("/api/v1/tenant/vehicle-change-requests/{id}")).isTrue();
    assertThat(paths.has("/api/v1/tenant/vehicle-change-requests/{requestId}/evidence/{side}"))
        .isTrue();

    assertThat(paths.has("/api/v1/admin/vehicles")).isTrue();
    assertThat(paths.has("/api/v1/admin/vehicles/{id}")).isTrue();
    JsonNode adminVehicleById = paths.get("/api/v1/admin/vehicles/{id}");
    assertThat(adminVehicleById.has("get")).isTrue();
    assertThat(adminVehicleById.has("delete")).isTrue();
    assertThat(adminVehicleById.has("put")).isFalse();

    assertThat(paths.get("/api/v1/admin/vehicles/{id}/issue-card").has("patch")).isTrue();
    assertThat(paths.get("/api/v1/admin/vehicles/{id}/reject").has("patch")).isTrue();
    assertThat(paths.get("/api/v1/admin/vehicles/{id}/revoke-card").has("patch")).isTrue();
    assertThat(paths.has("/api/v1/admin/vehicles/{vehicleId}/evidence/{side}")).isTrue();

    assertThat(paths.has("/api/v1/admin/vehicle-change-requests")).isTrue();
    assertThat(paths.has("/api/v1/admin/vehicle-change-requests/{id}")).isTrue();
    assertThat(paths.get("/api/v1/admin/vehicle-change-requests/{id}/approve").has("patch"))
        .isTrue();
    assertThat(paths.get("/api/v1/admin/vehicle-change-requests/{id}/reject").has("patch"))
        .isTrue();
    assertThat(paths.has("/api/v1/admin/vehicle-change-requests/{requestId}/evidence/{side}"))
        .isTrue();

    // Onboarding & Contracts (Phase 3A + Phase 3B)
    assertThat(paths.has("/api/v1/admin/onboarding")).isTrue();
    assertThat(paths.get("/api/v1/admin/onboarding").has("post")).isTrue();

    assertThat(paths.has("/api/v1/admin/contracts")).isTrue();
    assertThat(paths.get("/api/v1/admin/contracts").has("get")).isTrue();
    assertThat(paths.has("/api/v1/admin/contracts/{id}")).isTrue();
    assertThat(paths.get("/api/v1/admin/contracts/{id}").has("get")).isTrue();
    assertThat(paths.has("/api/v1/admin/contracts/{id}/renew")).isTrue();
    assertThat(paths.get("/api/v1/admin/contracts/{id}/renew").has("post")).isTrue();
    assertThat(paths.has("/api/v1/admin/contracts/{id}/terminate")).isTrue();
    assertThat(paths.get("/api/v1/admin/contracts/{id}/terminate").has("post")).isTrue();
    assertThat(paths.has("/api/v1/admin/contracts/{id}/documents")).isTrue();
    assertThat(paths.get("/api/v1/admin/contracts/{id}/documents").has("post")).isTrue();
    assertThat(paths.has("/api/v1/admin/contracts/{id}/documents/{documentId}")).isTrue();
    assertThat(paths.get("/api/v1/admin/contracts/{id}/documents/{documentId}").has("delete"))
        .isTrue();
    assertThat(paths.has("/api/v1/admin/contracts/{contractId}/documents/{documentId}/content"))
        .isTrue();
    assertThat(
            paths
                .get("/api/v1/admin/contracts/{contractId}/documents/{documentId}/content")
                .has("get"))
        .isTrue();

    assertThat(paths.has("/api/v1/tenant/contracts/me")).isTrue();
    assertThat(paths.get("/api/v1/tenant/contracts/me").has("get")).isTrue();
    assertThat(paths.has("/api/v1/tenant/contracts")).isTrue();
    assertThat(paths.get("/api/v1/tenant/contracts").has("get")).isTrue();
    assertThat(paths.has("/api/v1/tenant/contracts/{id}")).isTrue();
    assertThat(paths.get("/api/v1/tenant/contracts/{id}").has("get")).isTrue();
    assertThat(paths.has("/api/v1/tenant/contracts/{contractId}/documents/{documentId}/content"))
        .isTrue();
    assertThat(
            paths
                .get("/api/v1/tenant/contracts/{contractId}/documents/{documentId}/content")
                .has("get"))
        .isTrue();

    // Billing & Payments (Phase 4)
    assertThat(paths.has("/api/v1/admin/invoices")).isTrue();
    assertThat(paths.get("/api/v1/admin/invoices").has("get")).isTrue();
    assertThat(paths.has("/api/v1/admin/invoices/summary")).isTrue();
    assertThat(paths.get("/api/v1/admin/invoices/summary").has("get")).isTrue();
    assertThat(paths.has("/api/v1/admin/invoices/{id}")).isTrue();
    assertThat(paths.get("/api/v1/admin/invoices/{id}").has("get")).isTrue();
    assertThat(paths.has("/api/v1/admin/invoices/issue-batch")).isTrue();
    assertThat(paths.get("/api/v1/admin/invoices/issue-batch").has("post")).isTrue();
    assertThat(paths.has("/api/v1/admin/invoices/{id}/proof/content")).isTrue();
    assertThat(paths.get("/api/v1/admin/invoices/{id}/proof/content").has("get")).isTrue();
    assertThat(paths.has("/api/v1/admin/invoices/{id}/confirm-cash")).isTrue();
    assertThat(paths.get("/api/v1/admin/invoices/{id}/confirm-cash").has("patch")).isTrue();
    assertThat(paths.has("/api/v1/admin/invoices/{id}/confirm-transfer")).isTrue();
    assertThat(paths.get("/api/v1/admin/invoices/{id}/confirm-transfer").has("patch")).isTrue();
    assertThat(paths.has("/api/v1/admin/invoices/{id}/reject-proof")).isTrue();
    assertThat(paths.get("/api/v1/admin/invoices/{id}/reject-proof").has("patch")).isTrue();

    assertThat(paths.has("/api/v1/tenant/invoices")).isTrue();
    assertThat(paths.get("/api/v1/tenant/invoices").has("get")).isTrue();
    assertThat(paths.has("/api/v1/tenant/invoices/{id}")).isTrue();
    assertThat(paths.get("/api/v1/tenant/invoices/{id}").has("get")).isTrue();
    assertThat(paths.has("/api/v1/tenant/invoices/{id}/proof")).isTrue();
    assertThat(paths.get("/api/v1/tenant/invoices/{id}/proof").has("post")).isTrue();
    assertThat(paths.has("/api/v1/tenant/invoices/{id}/proof/content")).isTrue();
    assertThat(paths.get("/api/v1/tenant/invoices/{id}/proof/content").has("get")).isTrue();

    // Streaming endpoints must not expose injected HttpMethod as a client API parameter
    assertStreamingEndpointHasNoHttpMethodParam(
        paths,
        "/api/v1/admin/contracts/{contractId}/documents/{documentId}/content",
        List.of("contractId", "documentId"));
    assertStreamingEndpointHasNoHttpMethodParam(
        paths,
        "/api/v1/tenant/contracts/{contractId}/documents/{documentId}/content",
        List.of("contractId", "documentId"));
    assertStreamingEndpointHasNoHttpMethodParam(
        paths, "/api/v1/admin/vehicles/{vehicleId}/evidence/{side}", List.of("vehicleId", "side"));
    assertStreamingEndpointHasNoHttpMethodParam(
        paths, "/api/v1/tenant/vehicles/{vehicleId}/evidence/{side}", List.of("vehicleId", "side"));
    assertStreamingEndpointHasNoHttpMethodParam(
        paths,
        "/api/v1/admin/vehicle-change-requests/{requestId}/evidence/{side}",
        List.of("requestId", "side"));
    assertStreamingEndpointHasNoHttpMethodParam(
        paths,
        "/api/v1/tenant/vehicle-change-requests/{requestId}/evidence/{side}",
        List.of("requestId", "side"));
    assertStreamingEndpointHasNoHttpMethodParam(
        paths, "/api/v1/admin/invoices/{id}/proof/content", List.of("id"));
    assertStreamingEndpointHasNoHttpMethodParam(
        paths, "/api/v1/tenant/invoices/{id}/proof/content", List.of("id"));
  }

  private void assertStreamingEndpointHasNoHttpMethodParam(
      JsonNode paths, String path, List<String> expectedPathParams) {
    JsonNode getOp = paths.get(path).get("get");
    assertThat(getOp).isNotNull();
    JsonNode parameters = getOp.get("parameters");
    assertThat(parameters).isNotNull();
    List<String> actualNames = new ArrayList<>();
    for (JsonNode param : parameters) {
      String name = param.get("name").asText();
      String in = param.get("in").asText();
      assertThat(name).isNotEqualTo("httpMethod");
      assertThat(in).isEqualTo("path");
      actualNames.add(name);
    }
    assertThat(actualNames).containsExactlyInAnyOrderElementsOf(expectedPathParams);
  }
}
