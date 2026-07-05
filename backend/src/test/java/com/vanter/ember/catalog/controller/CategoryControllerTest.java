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
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CategoryController.class)
@Import({SecurityConfig.class, CorsConfig.class})
class CategoryControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockitoBean CategoryService categoryService;
    @MockitoBean JwtService jwtService;
    @MockitoBean UserDetailsService userDetailsService;

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

        CategoryRequest req = new CategoryRequest();
        req.setName("Burgers");
        req.setDescription("Just a description template");

        when(categoryService.create(any(CategoryRequest.class))).thenReturn(
                CategoryResponse.builder().id(1L).name("Burgers").build()
        );

        mockMvc.perform(multipart("/catalog/categories")
                .param("name", "Burgers")
                .param("description", "Just a description template"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Burgers"));
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void create_returns403ForNonAdmin() throws Exception {
        CategoryRequest req = new CategoryRequest();
        req.setName("Burgers");

        mockMvc.perform(multipart("/catalog/categories")
                        .param("name", "Burgers")
                .param("description", "Just a description template"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void update_returns200ForAdmin() throws Exception {
        when(categoryService.update(any(),eq(1L) )).thenReturn(
                CategoryResponse.builder().id(1L).name("Sandwiches").build());

        CategoryRequest req = new CategoryRequest();
        req.setName("Sandwiches");

        mockMvc.perform(multipart("/catalog/categories/1")
                        .param("name", "Burgers")
                .param("description", "Sandwiches")
                                .with(request -> {
                                    request.setMethod("PUT");
                                    return request;
                                })
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Sandwiches"));
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void update_returns403ForNonAdmin() throws Exception {
        CategoryRequest req = new CategoryRequest();
        req.setName("Sandwiches");

        mockMvc.perform(multipart("/catalog/categories/1")
                        .param("Name", "Sandwiches")
                        .param("description", "Any other descriptions")
                        .with(request -> {
                            request.setMethod("PUT");
                            return request;
                        })
                )
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

        mockMvc.perform(multipart("/catalog/categories")
                        .param("Name", "")
                        .with(request -> {
                            request.setMethod("POST");
                            return request;
                        }))
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
