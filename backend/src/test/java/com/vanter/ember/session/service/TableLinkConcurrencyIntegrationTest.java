package com.vanter.ember.session.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.vanter.ember.config.TenantContextHolder;
import com.vanter.ember.restaurant.model.Restaurant;
import com.vanter.ember.restaurant.repository.RestaurantRepository;
import com.vanter.ember.session.model.Session;
import com.vanter.ember.session.model.SessionStatus;
import com.vanter.ember.session.repository.SessionRepository;
import com.vanter.ember.settings.model.DiningTables;
import com.vanter.ember.settings.repository.DiningTableRepository;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = "ember.ratelimit.enabled=false")
class TableLinkConcurrencyIntegrationTest {

    @Autowired SessionService sessionService;
    @Autowired SessionRepository sessionRepository;
    @Autowired DiningTableRepository diningTableRepository;
    @Autowired RestaurantRepository restaurantRepository;

    private UUID tenantId;
    private UUID m1;
    private UUID m2;
    private UUID m3;
    private String s1;
    private String s2;

    @BeforeEach
    void setUp() {
        sessionRepository.deleteAll();
        diningTableRepository.deleteAll();
        restaurantRepository.deleteAll();
        Restaurant restaurant = restaurantRepository.save(Restaurant.builder()
                .name("Merge Test").slug("merge-test-" + UUID.randomUUID()).build());
        tenantId = restaurant.getId();
        TenantContextHolder.setTenantId(tenantId);
        m1 = table(1);
        m2 = table(2);
        m3 = table(3);
        s1 = sessionService.createSession(m1, "w1@test.com", 4, null).getId();
        s2 = sessionService.createSession(m2, "w2@test.com", 4, null).getId();
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    private UUID table(int number) {
        return diningTableRepository.save(DiningTables.builder()
                .restaurantId(tenantId).tableNumber(number).isActive(true).build()).getId();
    }

    private boolean succeeded(Callable<Object> action, CountDownLatch start) throws Exception {
        TenantContextHolder.setTenantId(tenantId);
        try {
            start.await();
            action.call();
            return true;
        } catch (IllegalStateException occupied) {
            return false;
        } finally {
            TenantContextHolder.clear();
        }
    }

    private long runConcurrently(Callable<Object> a, Callable<Object> b) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Future<Boolean> fa = pool.submit(() -> succeeded(a, start));
        Future<Boolean> fb = pool.submit(() -> succeeded(b, start));
        start.countDown();
        long wins = List.of(fa.get(), fb.get()).stream().filter(w -> w).count();
        pool.shutdown();
        return wins;
    }

    @Test
    void twoWaitersLinkingTheSameFreeTableToDifferentSessions_exactlyOneWins() throws Exception {
        long wins = runConcurrently(
                () -> sessionService.linkTable(s1, "w1@test.com", m3),
                () -> sessionService.linkTable(s2, "w2@test.com", m3));

        assertThat(wins).isEqualTo(1);
        long sessionsHoldingM3 = sessionRepository.findByTenantIdAndStatus(tenantId, SessionStatus.OPEN).stream()
                .filter(s -> s.getLinkedTables().stream().anyMatch(l -> l.getTableId().equals(m3))).count();
        assertThat(sessionsHoldingM3).isEqualTo(1);
    }

    @Test
    void linkingATableWhileAnotherWaiterSeatsAPartyThere_exactlyOneWins() throws Exception {
        long wins = runConcurrently(
                () -> sessionService.linkTable(s1, "w1@test.com", m3),
                () -> sessionService.createSession(m3, "w2@test.com", 2, null));

        assertThat(wins).isEqualTo(1);
    }

    @Test
    void afterTheSessionCloses_theLinkedTableIsFreeAgain() {
        sessionService.linkTable(s1, "w1@test.com", m3);
        Session family = sessionRepository.findById(s1).orElseThrow();
        family.setStatus(SessionStatus.CLOSED);
        sessionRepository.save(family);

        Session reseated = sessionService.createSession(m3, "w2@test.com", 2, null);

        assertThat(reseated.getTableId()).isEqualTo(m3);
    }
}
