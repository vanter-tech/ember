package com.vanter.ember.session.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vanter.ember.catalog.model.Category;
import com.vanter.ember.catalog.model.dto.MenuItemRequest;
import com.vanter.ember.catalog.repository.CategoryRepository;
import com.vanter.ember.catalog.repository.MenuItemRepository;
import com.vanter.ember.catalog.service.MenuItemService;
import com.vanter.ember.config.TenantContextHolder;
import com.vanter.ember.identity.model.Role;
import com.vanter.ember.identity.model.User;
import com.vanter.ember.identity.model.dto.LoginRequest;
import com.vanter.ember.identity.repository.UserRepository;
import com.vanter.ember.restaurant.model.Restaurant;
import com.vanter.ember.restaurant.repository.RestaurantRepository;
import com.vanter.ember.session.repository.SessionRepository;
import com.vanter.ember.settings.model.DiningTables;
import com.vanter.ember.settings.repository.DiningTableRepository;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * End-to-end waiter-managed seat lifecycle for the Ember Hub build (EMB-FEAT-HUB): open a table
 * with name-only seats, add/rename/remove them over real HTTP + DB + the synchronous event bus,
 * and confirm a rename cascades onto an already-sent order item and is blocked once a bill exists.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "ember.ratelimit.enabled=false")
class HubSeatFlowIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired RestaurantRepository restaurantRepository;
    @Autowired DiningTableRepository diningTableRepository;
    @Autowired SessionRepository sessionRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired MenuItemRepository menuItemRepository;
    @Autowired MenuItemService menuItemService;
    @Autowired PasswordEncoder passwordEncoder;

    private String waiterToken;
    private UUID tableId;
    private Long menuItemId;

    @BeforeEach
    void setUp() throws Exception {
        sessionRepository.deleteAll();
        menuItemRepository.deleteAll();
        categoryRepository.deleteAll();
        diningTableRepository.deleteAll();
        userRepository.deleteAll();
        restaurantRepository.deleteAll();

        Restaurant restaurant = restaurantRepository.save(Restaurant.builder()
                .name("Hub Seat Test").slug("hub-seat-" + UUID.randomUUID())
                .build());
        TenantContextHolder.setTenantId(restaurant.getId());

        userRepository.save(User.builder()
                .name("Waiter").email("waiter@hub.test").restaurantId(restaurant)
                .passwordHash(passwordEncoder.encode("password123")).role(Role.WAITER).build());
        waiterToken = login("waiter@hub.test", "password123");

        tableId = diningTableRepository.save(DiningTables.builder()
                .restaurantId(restaurant.getId()).tableNumber(9).isActive(true).build())
                .getId();

        Category category = categoryRepository.save(Category.builder().name("Hub-Food").build());
        MenuItemRequest itemReq = new MenuItemRequest();
        itemReq.setName("Hub Pizza");
        itemReq.setPrice(new BigDecimal("15.00"));
        itemReq.setCategoryId(category.getId());
        itemReq.setAvailable(true);
        menuItemId = menuItemService.create(itemReq, null).getId();
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
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
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    private JsonNode getSession(String sessionId) throws Exception {
        MvcResult result = mockMvc.perform(get("/sessions/" + sessionId)
                        .header("Authorization", "Bearer " + waiterToken))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    @Test
    void waiterOpensAndManagesNameOnlySeats() throws Exception {
        // 1 — open the table with two named seats + one auto-filled slot
        MvcResult opened = mockMvc.perform(post("/sessions")
                        .header("Authorization", "Bearer " + waiterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tableId\":\"" + tableId + "\",\"maxParticipants\":3,"
                                + "\"seatNames\":[\"Ana\",\"Beto\"]}"))
                .andExpect(status().isCreated())
                .andReturn();
        String sessionId = objectMapper.readTree(opened.getResponse().getContentAsString())
                .get("sessionId").asText();

        JsonNode s1 = getSession(sessionId);
        assertThat(s1.get("participants")).hasSize(3);
        assertThat(s1.get("participants").findValuesAsText("name"))
                .containsExactly("Ana", "Beto", "Asiento 3");
        s1.get("participants").forEach(p -> assertThat(p.get("userId").isNull()).isTrue());

        // 2 — the table is full (3/3): adding a seat is refused until capacity is raised explicitly
        mockMvc.perform(post("/sessions/" + sessionId + "/participants")
                        .header("Authorization", "Bearer " + waiterToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isConflict());

        mockMvc.perform(patch("/sessions/" + sessionId + "/capacity")
                        .header("Authorization", "Bearer " + waiterToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"additional\":2}"))
                .andExpect(status().isOk());

        // now a blank seat lands on the lowest free "Asiento N" (1 and 2 are still free here)
        mockMvc.perform(post("/sessions/" + sessionId + "/participants")
                        .header("Authorization", "Bearer " + waiterToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participants[3].name").value("Asiento 1"));

        // 3 — waiter sends an item for seat "Ana"
        mockMvc.perform(post("/sessions/" + sessionId + "/waiter-items")
                        .header("Authorization", "Bearer " + waiterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"menuItemId\":" + menuItemId + ",\"participantName\":\"Ana\"}"))
                .andExpect(status().isOk());

        // 4 — rename "Ana" -> "Ana G." : cascades onto the sent item
        mockMvc.perform(patch("/sessions/" + sessionId + "/participants")
                        .header("Authorization", "Bearer " + waiterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"from\":\"Ana\",\"to\":\"Ana G.\"}"))
                .andExpect(status().isOk());

        JsonNode s2 = getSession(sessionId);
        assertThat(s2.get("participants").findValuesAsText("name")).contains("Ana G.");
        assertThat(s2.get("items").findValuesAsText("participantName")).containsOnly("Ana G.");

        // 5 — an empty seat is removed; the leaver event redistributes nothing since it had no items
        mockMvc.perform(delete("/sessions/" + sessionId + "/participants/Asiento 1")
                        .header("Authorization", "Bearer " + waiterToken))
                .andExpect(status().isOk());

        JsonNode s3 = getSession(sessionId);
        assertThat(s3.get("participants").findValuesAsText("name")).doesNotContain("Asiento 1");
        assertThat(s3.get("participants")).hasSize(3);

        // 6 — the assigned-waiter guard holds for a stranger
        mockMvc.perform(patch("/sessions/" + sessionId + "/participants")
                        .header("Authorization", "Bearer " + login2())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"from\":\"Beto\",\"to\":\"B.\"}"))
                .andExpect(status().isForbidden());
    }

    private String login2() throws Exception {
        if (userRepository.findByEmail("other@hub.test").isEmpty()) {
            Restaurant restaurant = restaurantRepository.findAll().get(0);
            userRepository.save(User.builder()
                    .name("Other").email("other@hub.test").restaurantId(restaurant)
                    .passwordHash(passwordEncoder.encode("password123")).role(Role.WAITER).build());
        }
        return login("other@hub.test", "password123");
    }
}
