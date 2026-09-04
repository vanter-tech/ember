package com.vanter.ember.printing.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vanter.ember.config.CorsConfig;
import com.vanter.ember.config.SecurityConfig;
import com.vanter.ember.config.TenantContextHolder;
import com.vanter.ember.identity.repository.UserRepository;
import com.vanter.ember.identity.service.JwtService;
import com.vanter.ember.printing.exception.BillNotPaidException;
import com.vanter.ember.printing.model.PrintJob;
import com.vanter.ember.printing.model.PrintJobStatus;
import com.vanter.ember.printing.service.BillReceiptPrintService;
import com.vanter.ember.restaurant.repository.RestaurantRepository;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(BillReceiptController.class)
@Import({SecurityConfig.class, CorsConfig.class})
class BillReceiptControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean BillReceiptPrintService billReceiptPrintService;
    @MockBean JwtService jwtService;
    @MockBean UserDetailsService userDetailsService;
    @MockBean UserRepository userRepository;
    @MockBean RestaurantRepository restaurantRepository;

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    @WithMockUser(roles = "WAITER")
    void printReceipt_200_withJobId() throws Exception {
        PrintJob job = PrintJob.builder()
                .id(UUID.fromString("00000000-0000-0000-0000-000000000001"))
                .status(PrintJobStatus.PENDING)
                .build();
        when(billReceiptPrintService.enqueue(eq(42L))).thenReturn(job);

        mockMvc.perform(post("/printing/bills/42/receipt").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value("00000000-0000-0000-0000-000000000001"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @WithMockUser(roles = "WAITER")
    void printReceipt_409_withCode_whenBillNotPaid() throws Exception {
        when(billReceiptPrintService.enqueue(eq(42L))).thenThrow(new BillNotPaidException(42L));

        mockMvc.perform(post("/printing/bills/42/receipt").with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BILL_NOT_PAID"));
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void printReceipt_403_forCustomer() throws Exception {
        mockMvc.perform(post("/printing/bills/42/receipt").with(csrf()))
                .andExpect(status().isForbidden());
    }
}
