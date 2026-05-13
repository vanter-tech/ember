package com.vanter.ember.catalog.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vanter.ember.catalog.model.RestaurantTable;
import com.vanter.ember.catalog.model.TableStatus;
import com.vanter.ember.catalog.model.dto.RestaurantTableRequest;
import com.vanter.ember.catalog.service.RestaurantTableService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RestaurantTableController.class)
@Import({SecurityConfig.class, CorsConfig.class})
class RestaurantTableControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean RestaurantTableService tableService;
    @MockBean JwtService jwtService;
    @MockBean UserDetailsService userDetailsService;

    private RestaurantTable table1() {
        return RestaurantTable.builder()
                .id(1L).number(1).capacity(4).status(TableStatus.AVAILABLE).build();
    }

    private RestaurantTableRequest tableRequest() {
        RestaurantTableRequest req = new RestaurantTableRequest();
        req.setNumber(1);
        req.setCapacity(4);
        return req;
    }

    @Test
    @WithMockUser
    void getAll_returns200WithList() throws Exception {
        when(tableService.findAll()).thenReturn(List.of(table1()));

        mockMvc.perform(get("/api/catalog/tables"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].number").value(1));
    }

    @Test
    @WithMockUser
    void getById_returns200() throws Exception {
        when(tableService.findById(1L)).thenReturn(table1());

        mockMvc.perform(get("/api/catalog/tables/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.capacity").value(4));
    }

    @Test
    @WithMockUser
    void getById_returns404WhenNotFound() throws Exception {
        when(tableService.findById(99L))
                .thenThrow(new ResourceNotFoundException("Table not found: 99"));

        mockMvc.perform(get("/api/catalog/tables/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void create_returns201ForAdmin() throws Exception {
        when(tableService.create(any())).thenReturn(table1());

        mockMvc.perform(post("/api/catalog/tables")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(tableRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.number").value(1));
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void create_returns403ForNonAdmin() throws Exception {
        mockMvc.perform(post("/api/catalog/tables")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(tableRequest())))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void update_returns200ForAdmin() throws Exception {
        when(tableService.updateCapacity(eq(1L), eq(6))).thenReturn(
                RestaurantTable.builder().id(1L).number(1).capacity(6)
                        .status(TableStatus.AVAILABLE).build());

        RestaurantTableRequest req = new RestaurantTableRequest();
        req.setNumber(1);
        req.setCapacity(6);

        mockMvc.perform(put("/api/catalog/tables/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.capacity").value(6));
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void update_returns403ForNonAdmin() throws Exception {
        mockMvc.perform(put("/api/catalog/tables/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(tableRequest())))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "WAITER")
    void patchStatus_setOccupied_returns200ForWaiter() throws Exception {
        RestaurantTable occupied = RestaurantTable.builder()
                .id(1L).number(1).capacity(4).status(TableStatus.OCCUPIED).build();
        when(tableService.setOccupied(1L)).thenReturn(occupied);

        mockMvc.perform(patch("/api/catalog/tables/1/status")
                        .param("status", "OCCUPIED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OCCUPIED"));
    }

    @Test
    @WithMockUser(roles = "WAITER")
    void patchStatus_setAvailable_returns200ForWaiter() throws Exception {
        when(tableService.setAvailable(1L)).thenReturn(table1());

        mockMvc.perform(patch("/api/catalog/tables/1/status")
                        .param("status", "AVAILABLE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AVAILABLE"));
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void patchStatus_returns403ForCustomer() throws Exception {
        mockMvc.perform(patch("/api/catalog/tables/1/status")
                        .param("status", "OCCUPIED"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void delete_returns204ForAdmin() throws Exception {
        mockMvc.perform(delete("/api/catalog/tables/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void delete_returns403ForNonAdmin() throws Exception {
        mockMvc.perform(delete("/api/catalog/tables/1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "WAITER")
    void patchStatus_returns409WhenAlreadyOccupied() throws Exception {
        when(tableService.setOccupied(1L))
                .thenThrow(new IllegalStateException("Table 1 is already occupied"));

        mockMvc.perform(patch("/api/catalog/tables/1/status")
                        .param("status", "OCCUPIED"))
                .andExpect(status().isConflict());
    }
}
