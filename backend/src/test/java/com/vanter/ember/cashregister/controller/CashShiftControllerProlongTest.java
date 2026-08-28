package com.vanter.ember.cashregister.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vanter.ember.cashregister.dto.CashShiftResponse;
import com.vanter.ember.cashregister.model.CashShift;
import com.vanter.ember.cashregister.model.CashShiftStatus;
import com.vanter.ember.cashregister.service.CashShiftService;
import com.vanter.ember.config.CorsConfig;
import com.vanter.ember.config.SecurityConfig;
import com.vanter.ember.config.TenantContextHolder;
import com.vanter.ember.identity.model.Role;
import com.vanter.ember.identity.model.User;
import com.vanter.ember.identity.repository.UserRepository;
import com.vanter.ember.identity.service.JwtService;
import com.vanter.ember.restaurant.repository.RestaurantRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CashShiftController.class)
@Import({SecurityConfig.class, CorsConfig.class})
class CashShiftControllerProlongTest {

    @Autowired MockMvc mockMvc;
    @MockBean CashShiftService cashShiftService;
    @MockBean UserRepository userRepository;
    @MockBean JwtService jwtService;
    @MockBean UserDetailsService userDetailsService;
    @MockBean RestaurantRepository restaurantRepository;

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    private User sampleUser(String email) {
        return User.builder().id("user-1").email(email).name("Alice").role(Role.WAITER).build();
    }

    private CashShift sampleShift() {
        return CashShift.builder().id(5L).shiftNumber(1).status(CashShiftStatus.OPEN)
                .openingFloat(new BigDecimal("0.00")).openedBy("user-1")
                .openedAt(LocalDateTime.now().minusHours(2)).build();
    }

    @Test
    @WithMockUser(username = "waiter@ember.local", roles = "WAITER")
    void prolong_returnsOkAndResponseForWaiter() throws Exception {
        when(userRepository.findByEmail("waiter@ember.local"))
                .thenReturn(Optional.of(sampleUser("waiter@ember.local")));
        when(cashShiftService.prolongShift(eq(5L), eq("user-1"))).thenReturn(sampleShift());
        when(cashShiftService.toResponse(any(CashShift.class))).thenReturn(new CashShiftResponse(
                5L, 1, "OPEN", new BigDecimal("0.00"), "Alice",
                LocalDateTime.now().minusHours(2), null, null, null, null, null,
                null, null, null, null,
                LocalDateTime.now().plusHours(1), LocalDateTime.now().plusHours(1),
                false, LocalDate.now(), 1));

        mockMvc.perform(post("/cash-shifts/5/prolong"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prolongCount").value(1));
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void prolong_forbiddenForCustomer() throws Exception {
        mockMvc.perform(post("/cash-shifts/5/prolong"))
                .andExpect(status().isForbidden());
    }
}
