package com.uit.dormmanagement.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uit.dormmanagement.dtos.request.auth.CreateTechnicianRequest;
import com.uit.dormmanagement.dtos.request.auth.UpdateTechnicianRequest;
import com.uit.dormmanagement.dtos.request.auth.UpdateTechnicianStatusRequest;
import com.uit.dormmanagement.dtos.response.ResponseFactory;
import com.uit.dormmanagement.dtos.response.auth.TechnicianResponse;
import com.uit.dormmanagement.dtos.response.auth.UserProfileResponse;
import com.uit.dormmanagement.entities.User;
import com.uit.dormmanagement.enums.UserRole;
import com.uit.dormmanagement.enums.UserStatus;
import com.uit.dormmanagement.exceptions.AppExceptionHandler;
import com.uit.dormmanagement.security.UserPrincipal;
import com.uit.dormmanagement.services.UserService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

  private MockMvc mockMvc;
  private ObjectMapper objectMapper;

  @Mock private UserService userService;

  @InjectMocks private UserController userController;

  private UUID userId;
  private UserPrincipal principal;

  @BeforeEach
  void setUp() {
    ResponseFactory responseFactory = new ResponseFactory();
    userController.setResponseFactory(responseFactory);
    objectMapper = new ObjectMapper();

    userId = UUID.randomUUID();
    User user =
        User.builder()
            .id(userId)
            .username("manager")
            .email("manager@dorm.vn")
            .phone("0901000001")
            .passwordHash("$2a$12$hash")
            .role(UserRole.MANAGER)
            .status(UserStatus.ACTIVE)
            .build();
    principal = UserPrincipal.create(user);

    HandlerMethodArgumentResolver principalResolver =
        new HandlerMethodArgumentResolver() {
          @Override
          public boolean supportsParameter(MethodParameter parameter) {
            return parameter.hasParameterAnnotation(AuthenticationPrincipal.class);
          }

          @Override
          public Object resolveArgument(
              MethodParameter parameter,
              ModelAndViewContainer mavContainer,
              NativeWebRequest webRequest,
              WebDataBinderFactory binderFactory) {
            return principal;
          }
        };

    mockMvc =
        MockMvcBuilders.standaloneSetup(userController)
            .setCustomArgumentResolvers(
                principalResolver, new PageableHandlerMethodArgumentResolver())
            .setControllerAdvice(new AppExceptionHandler(responseFactory))
            .build();
  }

  @Test
  @DisplayName("GET /api/v1/users/me should return 200 with UserProfileResponse")
  void shouldReturnCurrentUserProfile() throws Exception {
    UserProfileResponse.StaffInfo staffInfo =
        new UserProfileResponse.StaffInfo(
            UUID.randomUUID(), "Nguyễn Văn Quản Lý", "0901000001", true);
    UserProfileResponse profileResponse =
        new UserProfileResponse(
            userId,
            "manager",
            "manager@dorm.vn",
            "0901000001",
            UserRole.MANAGER,
            UserStatus.ACTIVE,
            staffInfo,
            null);

    when(userService.getCurrentUserProfile(userId)).thenReturn(profileResponse);

    mockMvc
        .perform(get("/api/v1/users/me"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.httpStatus").value(200))
        .andExpect(jsonPath("$.message").value("Lấy thông tin tài khoản thành công"))
        .andExpect(jsonPath("$.data.id").value(userId.toString()))
        .andExpect(jsonPath("$.data.username").value("manager"))
        .andExpect(jsonPath("$.data.role").value("MANAGER"))
        .andExpect(jsonPath("$.data.staffProfile.fullName").value("Nguyễn Văn Quản Lý"));
  }

  @Test
  @DisplayName("POST /api/v1/users/technicians should return 201 Created on valid request")
  void shouldCreateTechnician() throws Exception {
    CreateTechnicianRequest request =
        new CreateTechnicianRequest(
            "Võ Văn Thịnh", "thinh.vv@dorm.vn", "0908112245", "Password@123");
    UUID staffId = UUID.randomUUID();
    TechnicianResponse response =
        new TechnicianResponse(
            staffId,
            UUID.randomUUID(),
            "thinh.vv",
            "thinh.vv@dorm.vn",
            "0908112245",
            "Võ Văn Thịnh",
            true,
            UserStatus.ACTIVE,
            Instant.now());

    when(userService.createTechnician(any(CreateTechnicianRequest.class))).thenReturn(response);

    mockMvc
        .perform(
            post("/api/v1/users/technicians")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.httpStatus").value(201))
        .andExpect(jsonPath("$.message").value("Tạo tài khoản kỹ thuật viên thành công"))
        .andExpect(jsonPath("$.data.fullName").value("Võ Văn Thịnh"));
  }

  @Test
  @DisplayName("GET /api/v1/users/technicians should return 200 with paginated technicians")
  void shouldGetTechnicians() throws Exception {
    UUID staffId = UUID.randomUUID();
    TechnicianResponse item =
        new TechnicianResponse(
            staffId,
            UUID.randomUUID(),
            "thinh.vv",
            "thinh.vv@dorm.vn",
            "0908112245",
            "Võ Văn Thịnh",
            true,
            UserStatus.ACTIVE,
            Instant.now());
    Page<TechnicianResponse> page = new PageImpl<>(List.of(item), PageRequest.of(0, 10), 1);

    when(userService.getTechnicians(any(), any(), any())).thenReturn(page);

    mockMvc
        .perform(get("/api/v1/users/technicians?active=true&search=thinh"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.httpStatus").value(200))
        .andExpect(jsonPath("$.data.items[0].fullName").value("Võ Văn Thịnh"));
  }

  @Test
  @DisplayName("GET /api/v1/users/technicians/{id} should return 200 with technician details")
  void shouldGetTechnicianById() throws Exception {
    UUID staffId = UUID.randomUUID();
    TechnicianResponse item =
        new TechnicianResponse(
            staffId,
            UUID.randomUUID(),
            "thinh.vv",
            "thinh.vv@dorm.vn",
            "0908112245",
            "Võ Văn Thịnh",
            true,
            UserStatus.ACTIVE,
            Instant.now());

    when(userService.getTechnicianById(staffId)).thenReturn(item);

    mockMvc
        .perform(get("/api/v1/users/technicians/" + staffId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.httpStatus").value(200))
        .andExpect(jsonPath("$.data.id").value(staffId.toString()))
        .andExpect(jsonPath("$.data.fullName").value("Võ Văn Thịnh"));
  }

  @Test
  @DisplayName("PATCH /api/v1/users/technicians/{id}/status should return 200 on status update")
  void shouldUpdateTechnicianStatus() throws Exception {
    UUID staffId = UUID.randomUUID();
    UpdateTechnicianStatusRequest request = new UpdateTechnicianStatusRequest(false);
    TechnicianResponse item =
        new TechnicianResponse(
            staffId,
            UUID.randomUUID(),
            "thinh.vv",
            "thinh.vv@dorm.vn",
            "0908112245",
            "Võ Văn Thịnh",
            false,
            UserStatus.LOCKED,
            Instant.now());

    when(userService.updateTechnicianStatus(eq(staffId), any(UpdateTechnicianStatusRequest.class)))
        .thenReturn(item);

    mockMvc
        .perform(
            patch("/api/v1/users/technicians/" + staffId + "/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.httpStatus").value(200))
        .andExpect(jsonPath("$.data.active").value(false));
  }

  @Test
  @DisplayName("PUT /api/v1/users/technicians/{id} should return 200 on profile update")
  void shouldUpdateTechnicianProfile() throws Exception {
    UUID staffId = UUID.randomUUID();
    UpdateTechnicianRequest request =
        new UpdateTechnicianRequest("Võ Văn Thịnh Updated", "thinh.new@dorm.vn", "0909999888");
    TechnicianResponse item =
        new TechnicianResponse(
            staffId,
            UUID.randomUUID(),
            "thinh.vv",
            "thinh.new@dorm.vn",
            "0909999888",
            "Võ Văn Thịnh Updated",
            true,
            UserStatus.ACTIVE,
            Instant.now());

    when(userService.updateTechnician(eq(staffId), any(UpdateTechnicianRequest.class)))
        .thenReturn(item);

    mockMvc
        .perform(
            put("/api/v1/users/technicians/" + staffId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.httpStatus").value(200))
        .andExpect(jsonPath("$.data.fullName").value("Võ Văn Thịnh Updated"))
        .andExpect(jsonPath("$.data.email").value("thinh.new@dorm.vn"));
  }
}
