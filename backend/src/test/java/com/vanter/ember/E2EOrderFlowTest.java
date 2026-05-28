package com.vanter.ember;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vanter.ember.billing.dto.CalculateBillRequest;
import com.vanter.ember.billing.dto.PhysicalPaymentRequest;
import com.vanter.ember.billing.dto.SplitBillRequest;
import com.vanter.ember.billing.model.SplitMethod;
import com.vanter.ember.billing.repository.BillRepository;
import com.vanter.ember.billing.repository.BillSplitRepository;
import com.vanter.ember.catalog.model.Category;
import com.vanter.ember.catalog.model.dto.MenuItemRequest;
import com.vanter.ember.catalog.repository.CategoryRepository;
import com.vanter.ember.catalog.repository.MenuItemRepository;
import com.vanter.ember.catalog.service.MenuItemService;
import com.vanter.ember.catalog.service.RestaurantTableService;
import com.vanter.ember.catalog.model.dto.RestaurantTableRequest;
import com.vanter.ember.catalog.model.TableStatus;
import com.vanter.ember.identity.model.Role;
import com.vanter.ember.identity.model.User;
import com.vanter.ember.identity.model.dto.LoginRequest;
import com.vanter.ember.identity.repository.UserRepository;
import com.vanter.ember.kitchen.model.KitchenOrder;
import com.vanter.ember.kitchen.repository.KitchenOrderRepository;
import com.vanter.ember.session.dto.AddItemRequest;
import com.vanter.ember.session.dto.CreateSessionRequest;
import com.vanter.ember.session.dto.JoinSessionRequest;
import com.vanter.ember.session.model.OrderItemStatus;
import com.vanter.ember.session.model.SessionStatus;
import com.vanter.ember.session.repository.SessionRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class E2EOrderFlowTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired CategoryRepository categoryRepository;
    @Autowired MenuItemService menuItemService;
    @Autowired MenuItemRepository menuItemRepository;
    @Autowired RestaurantTableService tableService;
    @Autowired SessionRepository sessionRepository;
    @Autowired KitchenOrderRepository kitchenOrderRepository;
    @Autowired BillRepository billRepository;
    @Autowired BillSplitRepository billSplitRepository;

    private String waiterToken;
    private String customerToken;
    private String kitchenToken;
    private Long tableId;
    private Long menuItemId;

    @BeforeEach
    void setUp() throws Exception {
        billSplitRepository.deleteAll();
        billRepository.deleteAll();
        kitchenOrderRepository.deleteAll();
        sessionRepository.deleteAll();
        menuItemRepository.deleteAll();
        categoryRepository.deleteAll();
        userRepository.deleteAll();

        String password = "password123";

        User waiter = userRepository.save(User.builder()
                .name("Waiter").email("waiter@e2e.com")
                .passwordHash(passwordEncoder.encode(password)).role(Role.WAITER).build());
        User customer = userRepository.save(User.builder()
                .name("Alice").email("customer@e2e.com")
                .passwordHash(passwordEncoder.encode(password)).role(Role.CUSTOMER).build());
        userRepository.save(User.builder()
                .name("Kitchen").email("kitchen@e2e.com")
                .passwordHash(passwordEncoder.encode(password)).role(Role.KITCHEN).build());

        waiterToken = login("waiter@e2e.com", password);
        customerToken = login("customer@e2e.com", password);
        kitchenToken = login("kitchen@e2e.com", password);

        RestaurantTableRequest tableReq = new RestaurantTableRequest();
        tableReq.setNumber(7);
        tableReq.setCapacity(4);
        tableId = tableService.create(tableReq).getId();

        Category category = categoryRepository.save(
                Category.builder().name("E2E-Food").build());
        MenuItemRequest itemReq = new MenuItemRequest();
        itemReq.setName("E2E Burger");
        itemReq.setPrice(new BigDecimal("12.00"));
        itemReq.setCategoryId(category.getId());
        menuItemId = menuItemService.create(itemReq, null).getId();
    }

    private String login(String email, String password) throws Exception {
        LoginRequest req = new LoginRequest();
        req.setEmail(email);
        req.setPassword(password);
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("token").asText();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    @Test
    void fullFlow_registerOrderAndPay_closesSessionAndReleasesTable() throws Exception {

        // 1 — Waiter creates session
        MvcResult sessionResult = mockMvc.perform(post("/sessions")
                        .header("Authorization", bearer(waiterToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateSessionRequest(tableId, 4))))
                .andExpect(status().isCreated())
                .andReturn();
        String sessionId = objectMapper.readTree(
                sessionResult.getResponse().getContentAsString()).get("id").asText();

        // 2 — Waiter gets QR token
        MvcResult qrResult = mockMvc.perform(get("/sessions/" + sessionId + "/qr")
                        .header("Authorization", bearer(waiterToken)))
                .andExpect(status().isOk())
                .andReturn();
        String qrToken = objectMapper.readTree(
                qrResult.getResponse().getContentAsString()).get("qrToken").asText();

        // 3 — Customer joins session
        mockMvc.perform(post("/sessions/" + sessionId + "/join")
                        .header("Authorization", bearer(customerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new JoinSessionRequest(qrToken, "Alice"))))
                .andExpect(status().isOk());

        // 4 — Customer adds an item
        MvcResult addItemResult = mockMvc.perform(post("/sessions/" + sessionId + "/items")
                        .header("Authorization", bearer(customerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddItemRequest(menuItemId))))
                .andExpect(status().isOk())
                .andReturn();
        String orderItemId = objectMapper.readTree(
                addItemResult.getResponse().getContentAsString())
                .get("items").get(0).get("id").asText();

        // 5 — Kitchen finds the order and drives item to DELIVERED
        KitchenOrder kitchenOrder = kitchenOrderRepository.findBySessionId(sessionId).orElseThrow();
        String kitchenOrderId = kitchenOrder.getId();

        mockMvc.perform(patch("/kitchen/orders/" + kitchenOrderId + "/items/" + orderItemId + "/status")
                        .header("Authorization", bearer(kitchenToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PREPARING\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/kitchen/orders/" + kitchenOrderId + "/items/" + orderItemId + "/status")
                        .header("Authorization", bearer(kitchenToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"READY\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/kitchen/orders/" + kitchenOrderId + "/items/" + orderItemId + "/status")
                        .header("Authorization", bearer(kitchenToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DELIVERED\"}"))
                .andExpect(status().isOk());

        // 6 — Waiter calculates bill
        MvcResult billResult = mockMvc.perform(post("/api/billing/sessions/" + sessionId + "/bill")
                        .header("Authorization", bearer(waiterToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CalculateBillRequest(SplitMethod.BY_CONSUMPTION))))
                .andExpect(status().isCreated())
                .andReturn();
        long billId = objectMapper.readTree(
                billResult.getResponse().getContentAsString()).get("id").asLong();

        assertThat(objectMapper.readTree(
                billResult.getResponse().getContentAsString())
                .get("total").decimalValue()).isEqualByComparingTo("12.00");

        // 7 — Waiter splits by consumption
        mockMvc.perform(post("/api/billing/bills/" + billId + "/split")
                        .header("Authorization", bearer(waiterToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new SplitBillRequest(SplitMethod.BY_CONSUMPTION, null))))
                .andExpect(status().isOk());

        // 8 — Waiter registers Alice's physical payment
        PhysicalPaymentRequest payReq = new PhysicalPaymentRequest(
                billId, "Alice", new BigDecimal("12.00"));
        mockMvc.perform(post("/api/billing/payments/physical")
                        .header("Authorization", bearer(waiterToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payReq)))
                .andExpect(status().isCreated());

        // 9 — Verify session is CLOSED and table is AVAILABLE
        assertThat(sessionRepository.findById(sessionId))
                .isPresent()
                .hasValueSatisfying(s -> assertThat(s.getStatus()).isEqualTo(SessionStatus.CLOSED));

        assertThat(tableService.findById(tableId).getStatus()).isEqualTo(TableStatus.AVAILABLE);
    }
}
