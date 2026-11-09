package com.shareride.template;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shareride.common.CommonAutoConfiguration;
import com.shareride.template.controller.TemplateController;
import com.shareride.template.domain.TemplateItemStatus;
import com.shareride.template.dto.CreateTemplateItemRequest;
import com.shareride.template.dto.TemplateItemResponse;
import com.shareride.template.dto.UpdateTemplateItemRequest;
import com.shareride.template.service.TemplateService;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = TemplateController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(CommonAutoConfiguration.class)
class TemplateControllerTest {

    @Autowired private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @MockitoBean private TemplateService templateService;

    @Test
    @DisplayName("POST /api/v1/templates should return HTTP 201 Created with BaseResponse")
    void testCreateItemSuccess() throws Exception {
        UUID id = UUID.randomUUID();
        CreateTemplateItemRequest request =
                CreateTemplateItemRequest.builder()
                        .name("Standard Fare Rule")
                        .code("FARE_STANDARD")
                        .description("Standard base calculation rule")
                        .build();

        TemplateItemResponse response =
                TemplateItemResponse.builder()
                        .id(id)
                        .version(0L)
                        .name("Standard Fare Rule")
                        .code("FARE_STANDARD")
                        .description("Standard base calculation rule")
                        .status(TemplateItemStatus.ACTIVE)
                        .createdAt(Instant.now())
                        .updatedAt(Instant.now())
                        .build();

        when(templateService.createItem(any(CreateTemplateItemRequest.class))).thenReturn(response);

        mockMvc.perform(
                        post("/api/v1/templates")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("TEMPLATE_ITEM_CREATED"))
                .andExpect(jsonPath("$.data.id").value(id.toString()))
                .andExpect(jsonPath("$.data.code").value("FARE_STANDARD"));
    }

    @Test
    @DisplayName("GET /api/v1/templates/{id} should return HTTP 200 OK")
    void testGetItemByIdSuccess() throws Exception {
        UUID id = UUID.randomUUID();
        TemplateItemResponse response =
                TemplateItemResponse.builder()
                        .id(id)
                        .version(0L)
                        .name("Standard Fare Rule")
                        .code("FARE_STANDARD")
                        .status(TemplateItemStatus.ACTIVE)
                        .createdAt(Instant.now())
                        .updatedAt(Instant.now())
                        .build();

        when(templateService.getItemById(eq(id))).thenReturn(response);

        mockMvc.perform(get("/api/v1/templates/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(id.toString()));
    }

    @Test
    @DisplayName("PUT /api/v1/templates/{id} should return HTTP 200 OK")
    void testUpdateItemSuccess() throws Exception {
        UUID id = UUID.randomUUID();
        UpdateTemplateItemRequest request =
                UpdateTemplateItemRequest.builder()
                        .name("Updated Fare Rule")
                        .status(TemplateItemStatus.INACTIVE)
                        .build();

        TemplateItemResponse response =
                TemplateItemResponse.builder()
                        .id(id)
                        .version(1L)
                        .name("Updated Fare Rule")
                        .code("FARE_STANDARD")
                        .status(TemplateItemStatus.INACTIVE)
                        .createdAt(Instant.now())
                        .updatedAt(Instant.now())
                        .build();

        when(templateService.updateItem(eq(id), any(UpdateTemplateItemRequest.class)))
                .thenReturn(response);

        mockMvc.perform(
                        put("/api/v1/templates/" + id)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("TEMPLATE_ITEM_UPDATED"))
                .andExpect(jsonPath("$.data.name").value("Updated Fare Rule"));
    }

    @Test
    @DisplayName("DELETE /api/v1/templates/{id} should return HTTP 200 OK with emptySuccess")
    void testDeleteItemSuccess() throws Exception {
        UUID id = UUID.randomUUID();
        doNothing().when(templateService).deleteItem(eq(id), any());

        mockMvc.perform(delete("/api/v1/templates/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
