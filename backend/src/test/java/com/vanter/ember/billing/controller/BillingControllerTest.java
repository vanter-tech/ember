package com.vanter.ember.billing.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vanter.ember.billing.dto.CalculateBillRequest;
import com.vanter.ember.billing.dto.DigitalPaymentRequest;
import com.vanter.ember.billing.dto.PaymentResponse;
import com.vanter.ember.billing.dto.PhysicalPaymentRequest;
import com.vanter.ember.billing.dto.RefundPaymentRequest;
import com.vanter.ember.billing.dto.RefundResponse;
import com.vanter.ember.billing.dto.RequestBillingRequest;
import com.vanter.ember.billing.dto.SplitBillRequest;
import com.vanter.ember.billing.dto.VoidBillRequest;
import com.vanter.ember.billing.model.Bill;
import com.vanter.ember.billing.model.BillSplit;
import com.vanter.ember.billing.model.BillSplitStatus;
import com.vanter.ember.billing.model.BillStatus;
import com.vanter.ember.billing.model.Payment;
import com.vanter.ember.billing.model.PaymentMethod;
import com.vanter.ember.billing.model.PaymentStatus;
import com.vanter.ember.billing.model.Refund;
import com.vanter.ember.billing.model.SplitMethod;
import com.vanter.ember.billing.service.BillingService;
import com.vanter.ember.billing.service.PaymentService;
import com.vanter.ember.config.CorsConfig;
import com.vanter.ember.config.SecurityConfig;
import com.vanter.ember.identity.service.JwtService;
import com.vanter.ember.restaurant.repository.RestaurantRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BillingController.class)
@Import({SecurityConfig.class, CorsConfig.class})
class BillingControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean BillingService billingService;
    @MockBean PaymentService paymentService;
    @MockBean JwtService jwtService;
    @MockBean UserDetailsService userDetailsService;
    @MockBean RestaurantRepository restaurantRepository;

    private Bill sampleBill() {
        return Bill.builder()
                .id(1L).sessionId("sess-1").total(new BigDecimal("40.00"))
                .splitMethod(SplitMethod.BY_CONSUMPTION).status(BillStatus.OPEN)
                .createdAt(LocalDateTime.now()).build();
    }

    private BillSplit sampleSplit(Bill bill) {
        return BillSplit.builder()
                .id(10L).bill(bill).participantName("Alice")
                .amount(new BigDecimal("25.00")).status(BillSplitStatus.UNPAID).build();
    }

    private Payment samplePayment(Bill bill) {
        return Payment.builder()
                .id(20L).bill(bill).participantName("Alice")
                .amount(new BigDecimal("25.00")).method(PaymentMethod.PHYSICAL)
                .status(PaymentStatus.CONFIRMED).createdAt(LocalDateTime.now()).build();
    }

    // --- POST /billing/sessions/{sessionId}/request ---

    @Test
    @WithMockUser(roles = "WAITER")
    void requestBilling_returnsAcceptedForWaiter() throws Exception {
        RequestBillingRequest req = new RequestBillingRequest(SplitMethod.EQUAL_PARTS, 2);
        mockMvc.perform(post("/billing/sessions/sess-1/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isAccepted());
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void requestBilling_forbiddenForCustomer() throws Exception {
        RequestBillingRequest req = new RequestBillingRequest(SplitMethod.BY_CONSUMPTION, null);
        mockMvc.perform(post("/billing/sessions/sess-1/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "KITCHEN")
    void requestBilling_forbiddenForKitchen() throws Exception {
        RequestBillingRequest req = new RequestBillingRequest(SplitMethod.BY_CONSUMPTION, null);
        mockMvc.perform(post("/billing/sessions/sess-1/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    // --- POST /billing/sessions/{sessionId}/bill ---

    @Test
    @WithMockUser(roles = "WAITER")
    void calculateBill_returnsCreatedForWaiter() throws Exception {
        when(billingService.calculateBill(eq("sess-1"), any(SplitMethod.class)))
                .thenReturn(sampleBill());

        CalculateBillRequest req = new CalculateBillRequest(SplitMethod.BY_CONSUMPTION);
        mockMvc.perform(post("/billing/sessions/sess-1/bill")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.sessionId").value("sess-1"))
                .andExpect(jsonPath("$.total").value(40.00));
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void calculateBill_forbiddenForCustomer() throws Exception {
        CalculateBillRequest req = new CalculateBillRequest(SplitMethod.BY_CONSUMPTION);
        mockMvc.perform(post("/billing/sessions/sess-1/bill")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "KITCHEN")
    void calculateBill_forbiddenForKitchen() throws Exception {
        CalculateBillRequest req = new CalculateBillRequest(SplitMethod.BY_CONSUMPTION);
        mockMvc.perform(post("/billing/sessions/sess-1/bill")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    // --- POST /billing/bills/{id}/split ---

    @Test
    @WithMockUser(roles = "WAITER")
    void splitBill_returnsListOfSplitsForWaiter() throws Exception {
        Bill bill = sampleBill();
        when(billingService.splitByConsumption(1L)).thenReturn(List.of(sampleSplit(bill)));

        SplitBillRequest req = new SplitBillRequest(SplitMethod.BY_CONSUMPTION, null);
        mockMvc.perform(post("/billing/bills/1/split")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].participantName").value("Alice"))
                .andExpect(jsonPath("$[0].amount").value(25.00));
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void splitBill_forbiddenForCustomer() throws Exception {
        SplitBillRequest req = new SplitBillRequest(SplitMethod.BY_CONSUMPTION, null);
        mockMvc.perform(post("/billing/bills/1/split")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "WAITER")
    void splitBill_callsEqualSplitWhenMethodIsEqualParts() throws Exception {
        Bill bill = sampleBill();
        when(billingService.splitEqually(1L, 2)).thenReturn(List.of(sampleSplit(bill)));

        SplitBillRequest req = new SplitBillRequest(SplitMethod.EQUAL_PARTS, 2);
        mockMvc.perform(post("/billing/bills/1/split")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "KITCHEN")
    void splitBill_forbiddenForKitchen() throws Exception {
        SplitBillRequest req = new SplitBillRequest(SplitMethod.BY_CONSUMPTION, null);
        mockMvc.perform(post("/billing/bills/1/split")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    // --- POST /billing/payments/physical ---

    @Test
    @WithMockUser(username = "waiter@ember.local", roles = "WAITER")
    void registerPhysicalPayment_returnsCreatedForWaiter() throws Exception {
        Bill bill = sampleBill();
        when(paymentService.registerPhysicalPayment(anyLong(), anyString(), any(BigDecimal.class), anyString()))
                .thenReturn(samplePayment(bill));

        PhysicalPaymentRequest req = new PhysicalPaymentRequest(1L, "Alice", new BigDecimal("25.00"));
        mockMvc.perform(post("/billing/payments/physical")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.method").value("PHYSICAL"));
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void registerPhysicalPayment_forbiddenForCustomer() throws Exception {
        PhysicalPaymentRequest req = new PhysicalPaymentRequest(1L, "Alice", new BigDecimal("25.00"));
        mockMvc.perform(post("/billing/payments/physical")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    // --- POST /billing/payments/digital ---

    @Test
    @WithMockUser(username = "customer@ember.local", roles = "CUSTOMER")
    void initiateDigitalPayment_returnsCreatedForCustomer() throws Exception {
        Bill bill = sampleBill();
        Payment pending = Payment.builder()
                .id(21L).bill(bill).participantName("Alice")
                .amount(new BigDecimal("25.00")).method(PaymentMethod.DIGITAL)
                .status(PaymentStatus.PENDING).gatewayRef("STUB-abc")
                .createdAt(LocalDateTime.now()).build();
        when(paymentService.initiateDigitalPayment(anyLong(), anyString(), any(BigDecimal.class), anyString()))
                .thenReturn(pending);

        DigitalPaymentRequest req = new DigitalPaymentRequest(1L, "Alice", new BigDecimal("25.00"));
        mockMvc.perform(post("/billing/payments/digital")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.method").value("DIGITAL"))
                .andExpect(jsonPath("$.gatewayRef").value("STUB-abc"));
    }

    @Test
    @WithMockUser(username = "waiter@ember.local", roles = "WAITER")
    void initiateDigitalPayment_returnsCreatedForWaiter() throws Exception {
        Bill bill = sampleBill();
        Payment pending = Payment.builder()
                .id(21L).bill(bill).participantName("Alice")
                .amount(new BigDecimal("25.00")).method(PaymentMethod.DIGITAL)
                .status(PaymentStatus.PENDING).gatewayRef("STUB-abc")
                .createdAt(LocalDateTime.now()).build();
        when(paymentService.initiateDigitalPayment(anyLong(), anyString(), any(BigDecimal.class), anyString()))
                .thenReturn(pending);

        DigitalPaymentRequest req = new DigitalPaymentRequest(1L, "Alice", new BigDecimal("25.00"));
        mockMvc.perform(post("/billing/payments/digital")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(roles = "KITCHEN")
    void initiateDigitalPayment_forbiddenForKitchen() throws Exception {
        DigitalPaymentRequest req = new DigitalPaymentRequest(1L, "Alice", new BigDecimal("25.00"));
        mockMvc.perform(post("/billing/payments/digital")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    // --- POST /billing/payments/{id}/confirm ---

    @Test
    @WithMockUser(roles = "WAITER")
    void confirmDigitalPayment_returnsOkForWaiter() throws Exception {
        Bill bill = sampleBill();
        Payment confirmed = Payment.builder()
                .id(20L).bill(bill).participantName("Alice")
                .amount(new BigDecimal("25.00")).method(PaymentMethod.DIGITAL)
                .status(PaymentStatus.CONFIRMED).gatewayRef("STUB-abc")
                .createdAt(LocalDateTime.now()).build();
        when(paymentService.confirmDigitalPayment(20L)).thenReturn(confirmed);

        mockMvc.perform(post("/billing/payments/20/confirm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void confirmDigitalPayment_forbiddenForCustomer() throws Exception {
        mockMvc.perform(post("/billing/payments/20/confirm"))
                .andExpect(status().isForbidden());
    }

    private Refund sampleRefund(Payment payment) {
        return Refund.builder()
                .id(40L).payment(payment).amount(new BigDecimal("10.00"))
                .reason("customer dispute").refundedBy("user-1")
                .createdAt(LocalDateTime.now()).build();
    }

    // --- POST /billing/bills/{id}/void ---

    @Test
    @WithMockUser(username = "waiter@ember.local", roles = "WAITER")
    void voidBill_returnsOkForWaiter() throws Exception {
        Bill voided = sampleBill();
        voided.setStatus(BillStatus.VOIDED);
        when(billingService.voidBill(eq(1L), anyString(), anyString())).thenReturn(voided);

        VoidBillRequest req = new VoidBillRequest("wrong split method");
        mockMvc.perform(post("/billing/bills/1/void")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VOIDED"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void voidBill_forbiddenForAdmin() throws Exception {
        VoidBillRequest req = new VoidBillRequest("reason");
        mockMvc.perform(post("/billing/bills/1/void")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    // --- GET /billing/bills/{id}/payments ---

    @Test
    @WithMockUser(roles = "WAITER")
    void listPayments_returnsOkForWaiter() throws Exception {
        Bill bill = sampleBill();
        PaymentResponse response = new PaymentResponse(
                20L, 1L, "Alice", new BigDecimal("25.00"), "PHYSICAL", "CONFIRMED",
                LocalDateTime.now(), BigDecimal.ZERO, new BigDecimal("25.00"));
        when(paymentService.listPayments(1L)).thenReturn(List.of(response));

        mockMvc.perform(get("/billing/bills/1/payments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].participantName").value("Alice"))
                .andExpect(jsonPath("$[0].remaining").value(25.00));
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void listPayments_forbiddenForCustomer() throws Exception {
        mockMvc.perform(get("/billing/bills/1/payments"))
                .andExpect(status().isForbidden());
    }

    // --- POST /billing/payments/{id}/refund ---

    @Test
    @WithMockUser(username = "waiter@ember.local", roles = "WAITER")
    void refundPayment_returnsCreatedForWaiter() throws Exception {
        Bill bill = sampleBill();
        Payment payment = samplePayment(bill);
        when(paymentService.refundPayment(eq(20L), any(), anyString(), anyString()))
                .thenReturn(sampleRefund(payment));

        RefundPaymentRequest req = new RefundPaymentRequest(new BigDecimal("10.00"), "customer dispute");
        mockMvc.perform(post("/billing/payments/20/refund")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(10.00))
                .andExpect(jsonPath("$.reason").value("customer dispute"));
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void refundPayment_forbiddenForCustomer() throws Exception {
        RefundPaymentRequest req = new RefundPaymentRequest(new BigDecimal("10.00"), "reason");
        mockMvc.perform(post("/billing/payments/20/refund")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void refundPayment_forbiddenForAdmin() throws Exception {
        // ADMIN can discover a refundable payment (listPayments is hasAnyRole('WAITER','ADMIN')) but
        // never executes the refund itself — WAITER alone authorizes refunds and voids, matching
        // every other billing mutation (calculateBill/splitBill/registerPhysicalPayment).
        RefundPaymentRequest req = new RefundPaymentRequest(new BigDecimal("10.00"), "reason");
        mockMvc.perform(post("/billing/payments/20/refund")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    // --- GET /billing/payments/{id}/refunds ---

    @Test
    @WithMockUser(roles = "WAITER")
    void listRefunds_returnsOkForWaiter() throws Exception {
        RefundResponse response = new RefundResponse(
                40L, new BigDecimal("10.00"), "customer dispute", "Alice", LocalDateTime.now());
        when(paymentService.listRefunds(20L)).thenReturn(List.of(response));

        mockMvc.perform(get("/billing/payments/20/refunds"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].reason").value("customer dispute"));
    }

    @Test
    @WithMockUser(roles = "KITCHEN")
    void listRefunds_forbiddenForKitchen() throws Exception {
        mockMvc.perform(get("/billing/payments/20/refunds"))
                .andExpect(status().isForbidden());
    }
}
