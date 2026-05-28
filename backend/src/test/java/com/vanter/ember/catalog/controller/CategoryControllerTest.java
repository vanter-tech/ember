package com.vanter.ember.catalog.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vanter.ember.catalog.model.dto.CategoryRequest;
import com.vanter.ember.catalog.model.dto.CategoryResponse;
import com.vanter.ember.catalog.service.CategoryService;
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
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CategoryController.class)
@Import({SecurityConfig.class, CorsConfig.class})
class CategoryControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean CategoryService categoryService;
    @MockBean JwtService jwtService;
    @MockBean UserDetailsService userDetailsService;

    @Test
    @WithMockUser
    void getAll_returns200WithList() throws Exception {
        when(categoryService.findAll()).thenReturn(
                List.of(CategoryResponse.builder().id(1L).name("Burgers").build()));

        mockMvc.perform(get("/catalog/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Burgers"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void create_returns201ForAdmin() throws Exception {
        when(categoryService.create("Burgers")).thenReturn(
                CategoryResponse.builder().id(1L).name("Burgers").build());

        CategoryRequest req = new CategoryRequest();
        req.setName("Burgers");

        mockMvc.perform(post("/catalog/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Burgers"));
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void create_returns403ForNonAdmin() throws Exception {
        CategoryRequest req = new CategoryRequest();
        req.setName("Burgers");

        mockMvc.perform(post("/catalog/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void update_returns200ForAdmin() throws Exception {
        when(categoryService.update(eq(1L), any())).thenReturn(
                CategoryResponse.builder().id(1L).name("Sandwiches").build());

        CategoryRequest req = new CategoryRequest();
        req.setName("Sandwiches");

        mockMvc.perform(put("/catalog/categories/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Sandwiches"));
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void update_returns403ForNonAdmin() throws Exception {
        CategoryRequest req = new CategoryRequest();
        req.setName("Sandwiches");

        mockMvc.perform(put("/catalog/categories/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void delete_returns204ForAdmin() throws Exception {
        mockMvc.perform(delete("/catalog/categories/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void delete_returns403ForNonAdmin() throws Exception {
        mockMvc.perform(delete("/catalog/categories/1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void create_returns400ForBlankName() throws Exception {
        CategoryRequest req = new CategoryRequest();
        req.setName("");

        mockMvc.perform(post("/catalog/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getById_returns404WhenNotFound() throws Exception {
        when(categoryService.findById(99L))
                .thenThrow(new ResourceNotFoundException("Category not found: 99"));

        mockMvc.perform(get("/catalog/categories/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void delete_returns404WhenNotFound() throws Exception {
        doThrow(new ResourceNotFoundException("Category not found: 99"))
                .when(categoryService).delete(99L);

        mockMvc.perform(delete("/catalog/categories/99"))
                .andExpect(status().isNotFound());
    }
}
