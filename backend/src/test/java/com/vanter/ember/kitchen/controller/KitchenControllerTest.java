package com.vanter.ember.kitchen.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vanter.ember.config.CorsConfig;
import com.vanter.ember.config.SecurityConfig;
import com.vanter.ember.identity.service.JwtService;
import com.vanter.ember.kitchen.dto.UpdateItemStatusRequest;
import com.vanter.ember.kitchen.model.KitchenItem;
import com.vanter.ember.kitchen.model.KitchenOrder;
import com.vanter.ember.kitchen.service.KitchenService;
import com.vanter.ember.session.model.OrderItemStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(KitchenController.class)
@Import({SecurityConfig.class, CorsConfig.class})
class KitchenControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean KitchenService kitchenService;
    @MockBean JwtService jwtService;
    @MockBean UserDetailsService userDetailsService;

    private KitchenOrder sampleOrder() {
        KitchenItem item = KitchenItem.builder()
                .itemId("order-item-1").name("Tacos").participantName("Alice")
                .status(OrderItemStatus.PENDING).updatedAt(LocalDateTime.now()).build();
        return KitchenOrder.builder()
                .id("ko-1").sessionId("sess-1").tableNumber(5)
                .items(new ArrayList<>(List.of(item))).build();
    }

    // --- GET /api/kitchen/orders ---

    @Test
    @WithMockUser(roles = "KITCHEN")
    void getAllOrders_returnsListForKitchen() throws Exception {
        when(kitchenService.findAll()).thenReturn(List.of(sampleOrder()));

        mockMvc.perform(get("/api/kitchen/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("ko-1"))
                .andExpect(jsonPath("$[0].sessionId").value("sess-1"))
                .andExpect(jsonPath("$[0].tableNumber").value(5));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getAllOrders_returnsListForAdmin() throws Exception {
        when(kitchenService.findAll()).thenReturn(List.of(sampleOrder()));

        mockMvc.perform(get("/api/kitchen/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("ko-1"));
    }

    @Test
    @WithMockUser(roles = "WAITER")
    void getAllOrders_forbiddenForWaiter() throws Exception {
        mockMvc.perform(get("/api/kitchen/orders"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getAllOrders_unauthenticatedReturns403() throws Exception {
        mockMvc.perform(get("/api/kitchen/orders"))
                .andExpect(status().isForbidden());
    }

    // --- GET /api/kitchen/orders/{sessionId} ---

    @Test
    @WithMockUser(roles = "KITCHEN")
    void getOrderBySessionId_returnsOrderForKitchen() throws Exception {
        when(kitchenService.findBySessionId("sess-1")).thenReturn(sampleOrder());

        mockMvc.perform(get("/api/kitchen/orders/sess-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("ko-1"))
                .andExpect(jsonPath("$.sessionId").value("sess-1"));
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void getOrderBySessionId_forbiddenForCustomer() throws Exception {
        mockMvc.perform(get("/api/kitchen/orders/sess-1"))
                .andExpect(status().isForbidden());
    }

    // --- PATCH /api/kitchen/orders/{orderId}/items/{itemId}/status ---

    @Test
    @WithMockUser(roles = "KITCHEN")
    void updateItemStatus_returnsUpdatedOrderForKitchen() throws Exception {
        KitchenOrder updated = sampleOrder();
        updated.getItems().get(0).setStatus(OrderItemStatus.PREPARING);
        when(kitchenService.updateItemStatus("ko-1", "order-item-1", OrderItemStatus.PREPARING))
                .thenReturn(updated);

        mockMvc.perform(patch("/api/kitchen/orders/ko-1/items/order-item-1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateItemStatusRequest(OrderItemStatus.PREPARING))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].status").value("PREPARING"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateItemStatus_forbiddenForAdmin() throws Exception {
        mockMvc.perform(patch("/api/kitchen/orders/ko-1/items/order-item-1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateItemStatusRequest(OrderItemStatus.PREPARING))))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "WAITER")
    void updateItemStatus_forbiddenForWaiter() throws Exception {
        mockMvc.perform(patch("/api/kitchen/orders/ko-1/items/order-item-1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateItemStatusRequest(OrderItemStatus.PREPARING))))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateItemStatus_unauthenticatedReturns403() throws Exception {
        mockMvc.perform(patch("/api/kitchen/orders/ko-1/items/order-item-1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateItemStatusRequest(OrderItemStatus.PREPARING))))
                .andExpect(status().isForbidden());
    }
}
