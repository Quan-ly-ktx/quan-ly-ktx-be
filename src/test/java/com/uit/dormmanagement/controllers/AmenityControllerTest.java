package com.uit.dormmanagement.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.uit.dormmanagement.dtos.response.ResponseFactory;
import com.uit.dormmanagement.entities.Amenity;
import com.uit.dormmanagement.exceptions.AppExceptionHandler;
import com.uit.dormmanagement.repositories.AmenityRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class AmenityControllerTest {

  @Mock private AmenityRepository amenityRepository;

  @InjectMocks private AmenityController amenityController;

  private MockMvc mockMvc;
  private final ResponseFactory responseFactory = new ResponseFactory();

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(amenityController, "responseFactory", responseFactory);
    mockMvc =
        MockMvcBuilders.standaloneSetup(amenityController)
            .setControllerAdvice(new AppExceptionHandler(responseFactory))
            .build();
  }

  @Test
  @DisplayName("GET /api/v1/admin/amenities: should return 200 and list of amenities")
  void getAmenities_shouldReturnList() throws Exception {
    UUID id1 = UUID.randomUUID();
    UUID id2 = UUID.randomUUID();
    Amenity a1 = Amenity.builder().id(id1).code("AC").name("Điều hòa không khí").build();
    Amenity a2 = Amenity.builder().id(id2).code("FRIDGE").name("Tủ lạnh").build();

    when(amenityRepository.findAll(any(Sort.class))).thenReturn(List.of(a1, a2));

    mockMvc
        .perform(get("/api/v1/admin/amenities"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.httpStatus").value(200))
        .andExpect(jsonPath("$.data[0].id").value(id1.toString()))
        .andExpect(jsonPath("$.data[0].code").value("AC"))
        .andExpect(jsonPath("$.data[0].name").value("Điều hòa không khí"))
        .andExpect(jsonPath("$.data[1].code").value("FRIDGE"));
  }
}
