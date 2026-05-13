package com.vanter.ember.catalog.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vanter.ember.catalog.model.Category;
import com.vanter.ember.catalog.model.MenuItem;
import com.vanter.ember.catalog.service.MenuItemService;
import com.vanter.ember.config.CorsConfig;
import com.vanter.ember.config.ResourceNotFoundException;
import com.vanter.ember.config.SecurityConfig;
import com.vanter.ember.identity.service.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MenuItemController.class)
@Import({SecurityConfig.class, CorsConfig.class})
class MenuItemControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean MenuItemService menuItemService;
    @MockBean JwtService jwtService;
    @MockBean UserDetailsService userDetailsService;

    private MenuItem burger() {
        return MenuItem.builder()
                .id(1L).name("Classic Burger").description("Beef patty")
                .price(new BigDecimal("9.99"))
                .category(Category.builder().id(1L).name("Burgers").build())
                .available(true).build();
    }

    @Test
    @WithMockUser
    void getAll_returns200WithList() throws Exception {
        when(menuItemService.findAll()).thenReturn(List.of(burger()));

        mockMvc.perform(get("/api/catalog/items"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Classic Burger"));
    }

    @Test
    @WithMockUser
    void getById_returns200() throws Exception {
        when(menuItemService.findById(1L)).thenReturn(burger());

        mockMvc.perform(get("/api/catalog/items/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Classic Burger"));
    }

    @Test
    @WithMockUser
    void getById_returns404WhenNotFound() throws Exception {
        when(menuItemService.findById(99L))
                .thenThrow(new ResourceNotFoundException("Menu item not found: 99"));

        mockMvc.perform(get("/api/catalog/items/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void create_returns201ForAdminWithoutImage() throws Exception {
        when(menuItemService.create(any(), isNull())).thenReturn(burger());

        mockMvc.perform(multipart("/api/catalog/items")
                        .param("name", "Classic Burger")
                        .param("description", "Beef patty")
                        .param("price", "9.99")
                        .param("categoryId", "1"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Classic Burger"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void create_returns201WithImage() throws Exception {
        MenuItem item = MenuItem.builder().id(1L).name("Burger")
                .price(new BigDecimal("9.99"))
                .category(Category.builder().id(1L).name("Burgers").build())
                .available(true)
                .imageUrl("http://localhost:9000/ember-media/uuid.jpg").build();
        when(menuItemService.create(any(), any())).thenReturn(item);

        MockMultipartFile image = new MockMultipartFile(
                "image", "photo.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[100]);

        mockMvc.perform(multipart("/api/catalog/items")
                        .file(image)
                        .param("name", "Burger")
                        .param("price", "9.99")
                        .param("categoryId", "1"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.imageUrl").value("http://localhost:9000/ember-media/uuid.jpg"));
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void create_returns403ForNonAdmin() throws Exception {
        mockMvc.perform(multipart("/api/catalog/items")
                        .param("name", "Burger")
                        .param("price", "9.99")
                        .param("categoryId", "1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void update_returns200ForAdmin() throws Exception {
        when(menuItemService.update(eq(1L), any(), isNull())).thenReturn(burger());

        mockMvc.perform(multipart("/api/catalog/items/1")
                        .with(req -> { req.setMethod("PUT"); return req; })
                        .param("name", "Classic Burger")
                        .param("price", "9.99")
                        .param("categoryId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Classic Burger"));
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void update_returns403ForNonAdmin() throws Exception {
        mockMvc.perform(multipart("/api/catalog/items/1")
                        .with(req -> { req.setMethod("PUT"); return req; })
                        .param("name", "Burger")
                        .param("price", "9.99")
                        .param("categoryId", "1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void toggleAvailability_returns200ForAdmin() throws Exception {
        MenuItem toggled = burger();
        toggled.setAvailable(false);
        when(menuItemService.toggleAvailability(1L)).thenReturn(toggled);

        mockMvc.perform(patch("/api/catalog/items/1/availability"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false));
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void toggleAvailability_returns403ForNonAdmin() throws Exception {
        mockMvc.perform(patch("/api/catalog/items/1/availability"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void delete_returns204ForAdmin() throws Exception {
        mockMvc.perform(delete("/api/catalog/items/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void delete_returns403ForNonAdmin() throws Exception {
        mockMvc.perform(delete("/api/catalog/items/1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void delete_returns404WhenNotFound() throws Exception {
        doThrow(new ResourceNotFoundException("Menu item not found: 99"))
                .when(menuItemService).delete(99L);

        mockMvc.perform(delete("/api/catalog/items/99"))
                .andExpect(status().isNotFound());
    }
}
