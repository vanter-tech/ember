package com.vanter.ember.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vanter.ember.billing.model.Bill;
import com.vanter.ember.billing.model.BillStatus;
import com.vanter.ember.billing.model.Payment;
import com.vanter.ember.billing.model.PaymentMethod;
import com.vanter.ember.billing.model.PaymentStatus;
import com.vanter.ember.billing.model.SplitMethod;
import com.vanter.ember.billing.repository.BillRepository;
import com.vanter.ember.billing.repository.PaymentRepository;
import com.vanter.ember.config.TenantContextHolder;
import com.vanter.ember.identity.model.Role;
import com.vanter.ember.identity.model.User;
import com.vanter.ember.identity.service.JwtService;
import com.vanter.ember.identity.repository.UserRepository;
import com.vanter.ember.restaurant.model.Restaurant;
import com.vanter.ember.restaurant.model.RestaurantPlan;
import com.vanter.ember.restaurant.repository.RestaurantRepository;
import com.vanter.ember.session.model.Session;
import com.vanter.ember.session.model.SessionActivity;
import com.vanter.ember.session.model.SessionStatus;
import com.vanter.ember.session.repository.SessionRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Full stack (real JWT, transactions, JSON, events) for the admin "close this stuck table" action:
 * a table left open for days with consumption that nobody can charge or close.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminForceCloseTableIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JwtService jwtService;
    @Autowired UserRepository userRepository;
    @Autowired RestaurantRepository restaurantRepository;
    @Autowired SessionRepository sessionRepository;
    @Autowired BillRepository billRepository;
    @Autowired PaymentRepository paymentRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private static final String PASSWORD = "password123";

    private Restaurant restaurant;
    private String adminToken;
    private String waiterToken;

    @BeforeEach
    void setUp() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        restaurant = restaurantRepository.save(Restaurant.builder()
                .name("Force Close " + suffix).slug("force-close-" + suffix)
                .plan(RestaurantPlan.STARTER).build());
        TenantContextHolder.setTenantId(restaurant.getId());
        User admin = userRepository.save(User.builder().name("Admin").email("admin-" + suffix + "@fc.com")
                .restaurantId(restaurant).passwordHash(passwordEncoder.encode(PASSWORD)).role(Role.ADMIN).build());
        User waiter = userRepository.save(User.builder().name("Waiter").email("waiter-" + suffix + "@fc.com")
                .restaurantId(restaurant).passwordHash(passwordEncoder.encode(PASSWORD)).role(Role.WAITER).build());
        adminToken = tokenFor(admin);
        waiterToken = tokenFor(waiter);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    /**
     * Same claims AuthService puts in a real login token. Minted directly instead of POST /auth/login:
     * that endpoint has a per-IP sliding-window limit shared by the whole test JVM, and enough logins
     * across integration classes made other tests (E2EOrderFlowTest) receive 429.
     */
    private String tokenFor(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", user.getRole().name());
        claims.put("userId", user.getId());
        claims.put("ver", user.getTokenVersion());
        claims.put("rid", restaurant.getId());
        return jwtService.generateToken(user.getEmail(), claims);
    }

    private Session stuckSession() {
        return sessionRepository.save(Session.builder()
                .id(UUID.randomUUID().toString()).tenantId(restaurant.getId()).tableId(UUID.randomUUID())
                .waiterId("someone-else@fc.com").status(SessionStatus.OPEN).maxParticipants(4)
                .createdAt(LocalDateTime.now().minusDays(12)).build());
    }

    private Bill billFor(Session session, BillStatus status) {
        return billRepository.save(Bill.builder()
                .sessionId(session.getId()).total(new BigDecimal("407.00"))
                .splitMethod(SplitMethod.EQUAL_PARTS).status(status)
                .createdAt(LocalDateTime.now().minusDays(12)).build());
    }

    private String forceClose(String token, String sessionId, String body, int expectedStatus) throws Exception {
        String response = mockMvc.perform(post("/billing/sessions/" + sessionId + "/force-close")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString();
        // The request filter clears the tenant context when it finishes; Bill/Payment are tenant-scoped,
        // so bind it again for the assertions the test makes on this thread.
        TenantContextHolder.setTenantId(restaurant.getId());
        return response;
    }

    @Test
    void closesAStuckTable_voidingItsOpenBill_andLeavingAnAuditEntry() throws Exception {
        Session session = stuckSession();
        Bill bill = billFor(session, BillStatus.OPEN);

        forceClose(adminToken, session.getId(), "{\"reason\":\"el cliente se fue sin pagar\"}", 204);

        Session closed = sessionRepository.findByIdAndTenantId(session.getId(), restaurant.getId()).orElseThrow();
        assertThat(closed.getStatus()).isEqualTo(SessionStatus.CLOSED);
        SessionActivity entry = closed.getActivityLog().get(closed.getActivityLog().size() - 1);
        assertThat(entry.getType()).isEqualTo(SessionActivity.Type.CLOSED_BY_ADMIN);
        assertThat(entry.getNote()).isEqualTo("el cliente se fue sin pagar");
        Bill voided = billRepository.findById(bill.getId()).orElseThrow();
        assertThat(voided.getStatus()).isEqualTo(BillStatus.VOIDED);
        assertThat(voided.getVoidReason()).contains("el cliente se fue sin pagar");
    }

    @Test
    void refusesATableThatAlreadyHasAConfirmedPayment_andLeavesEverythingAsItWas() throws Exception {
        Session session = stuckSession();
        Bill bill = billFor(session, BillStatus.OPEN);
        paymentRepository.save(Payment.builder()
                .bill(bill).participantName("Jose").amount(new BigDecimal("100.00"))
                .method(PaymentMethod.PHYSICAL).status(PaymentStatus.CONFIRMED)
                .cashShiftId(1L).processedBy("w").createdAt(LocalDateTime.now()).build());

        forceClose(adminToken, session.getId(), "{\"reason\":\"x\"}", 409);

        assertThat(sessionRepository.findByIdAndTenantId(session.getId(), restaurant.getId()).orElseThrow()
                .getStatus()).isEqualTo(SessionStatus.OPEN);
        assertThat(billRepository.findById(bill.getId()).orElseThrow().getStatus()).isEqualTo(BillStatus.OPEN);
    }

    @Test
    void aWaiterCannotForceCloseATable() throws Exception {
        Session session = stuckSession();

        forceClose(waiterToken, session.getId(), "{\"reason\":\"x\"}", 403);

        assertThat(sessionRepository.findByIdAndTenantId(session.getId(), restaurant.getId()).orElseThrow()
                .getStatus()).isEqualTo(SessionStatus.OPEN);
    }

    @Test
    void anAdminCannotCloseAnotherRestaurantsTable() throws Exception {
        Restaurant other = restaurantRepository.save(Restaurant.builder()
                .name("Other").slug("other-" + UUID.randomUUID().toString().substring(0, 8))
                .plan(RestaurantPlan.STARTER).build());
        Session foreign = sessionRepository.save(Session.builder()
                .id(UUID.randomUUID().toString()).tenantId(other.getId()).tableId(UUID.randomUUID())
                .status(SessionStatus.OPEN).maxParticipants(4).createdAt(LocalDateTime.now()).build());

        forceClose(adminToken, foreign.getId(), "{\"reason\":\"x\"}", 404);

        assertThat(sessionRepository.findByIdAndTenantId(foreign.getId(), other.getId()).orElseThrow()
                .getStatus()).isEqualTo(SessionStatus.OPEN);
    }

    @Test
    void closingATableTwiceIsRejected() throws Exception {
        Session session = stuckSession();

        forceClose(adminToken, session.getId(), "{\"reason\":\"primera vez\"}", 204);
        forceClose(adminToken, session.getId(), "{\"reason\":\"segunda vez\"}", 409);
    }
}
