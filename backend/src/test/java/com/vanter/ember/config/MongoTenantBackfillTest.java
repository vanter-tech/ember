package com.vanter.ember.config;

import com.vanter.ember.kitchen.model.KitchenOrder;
import com.vanter.ember.identity.repository.UserRepository;
import com.vanter.ember.kitchen.repository.KitchenOrderRepository;
import com.vanter.ember.restaurant.model.Restaurant;
import com.vanter.ember.restaurant.repository.RestaurantRepository;
import com.vanter.ember.session.model.Session;
import com.vanter.ember.session.model.SessionStatus;
import com.vanter.ember.session.repository.SessionRepository;
import com.vanter.ember.settings.model.DiningTables;
import com.vanter.ember.settings.repository.DiningTableRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the task-2.18 backfill. It needs both persistence stores — tenant ownership is inferred
 * from JPA {@code DiningTables} rows and applied to Mongo documents — so this is a full-context
 * test rather than a slice.
 */
@SpringBootTest
class MongoTenantBackfillTest {

    @Autowired MongoTenantBackfill backfill;
    @Autowired MongoTemplate mongoTemplate;
    @Autowired SessionRepository sessionRepository;
    @Autowired KitchenOrderRepository kitchenOrderRepository;
    @Autowired RestaurantRepository restaurantRepository;
    @Autowired DiningTableRepository diningTableRepository;
    @Autowired UserRepository userRepository;

    private Restaurant restaurantA;
    private Restaurant restaurantB;

    @BeforeEach
    void setUp() {
        sessionRepository.deleteAll();
        kitchenOrderRepository.deleteAll();
        mongoTemplate.dropCollection(MongoTenantBackfill.AUDIT_COLLECTION);
        // `restaurants` is the only table the backfill counts, and `users` FK into it, so both are
        // purged: the sole-restaurant fallback must see exactly the restaurants this test created.
        userRepository.deleteAll();
        restaurantRepository.deleteAll();

        restaurantA = restaurantRepository.save(Restaurant.builder()
                .name("Tenant A").slug("tenant-a-" + UUID.randomUUID()).build());
        restaurantB = restaurantRepository.save(Restaurant.builder()
                .name("Tenant B").slug("tenant-b-" + UUID.randomUUID()).build());
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    /** Dining tables are {@code @TenantId}-filtered, so writes need the tenant bound. */
    private UUID tableFor(UUID tenantId, int tableNumber) {
        TenantContextHolder.setTenantId(tenantId);
        try {
            return diningTableRepository.save(DiningTables.builder()
                    .restaurantId(tenantId).tableNumber(tableNumber).isActive(true).build()).getId();
        } finally {
            TenantContextHolder.clear();
        }
    }

    /** A session as it was written before task-2.17 introduced {@code tenantId}. */
    private Session legacySession(UUID tableId) {
        return sessionRepository.save(Session.builder()
                .tableId(tableId).waiterId("waiter@test.com")
                .status(SessionStatus.OPEN).maxParticipants(4)
                .createdAt(LocalDateTime.now()).build());
    }

    private KitchenOrder legacyKitchenOrder(String sessionId) {
        return kitchenOrderRepository.save(KitchenOrder.builder()
                .sessionId(sessionId).tableNumber(5)
                .createdAt(LocalDateTime.now()).items(new ArrayList<>()).build());
    }

    private UUID tenantOf(Session session) {
        return mongoTemplate.findById(session.getId(), Session.class).getTenantId();
    }

    private UUID tenantOf(KitchenOrder order) {
        return mongoTemplate.findById(order.getId(), KitchenOrder.class).getTenantId();
    }

    @Test
    void backfill_assignsEachSessionTheTenantThatOwnsItsTable() {
        Session sessionA = legacySession(tableFor(restaurantA.getId(), 1));
        Session sessionB = legacySession(tableFor(restaurantB.getId(), 1));

        MongoTenantBackfill.Result result = backfill.backfill();

        assertThat(result.sessionsUpdated()).isEqualTo(2);
        assertThat(result.unresolvedSessions()).isZero();
        assertThat(tenantOf(sessionA)).isEqualTo(restaurantA.getId());
        assertThat(tenantOf(sessionB)).isEqualTo(restaurantB.getId());
    }

    @Test
    void backfill_makesKitchenOrdersInheritTheirSessionsTenant() {
        Session sessionB = legacySession(tableFor(restaurantB.getId(), 2));
        KitchenOrder order = legacyKitchenOrder(sessionB.getId());

        MongoTenantBackfill.Result result = backfill.backfill();

        assertThat(result.kitchenOrdersUpdated()).isEqualTo(1);
        assertThat(tenantOf(order)).isEqualTo(restaurantB.getId());
    }

    @Test
    void backfill_leavesDocumentsUnresolvedWhenOwnershipCannotBeInferred() {
        // the table this session was opened on is gone, and two restaurants exist
        Session orphan = legacySession(UUID.randomUUID());
        KitchenOrder orphanOrder = legacyKitchenOrder(orphan.getId());

        MongoTenantBackfill.Result result = backfill.backfill();

        assertThat(result.unresolvedSessions()).isEqualTo(1);
        assertThat(result.unresolvedKitchenOrders()).isEqualTo(1);
        assertThat(tenantOf(orphan)).isNull();
        assertThat(tenantOf(orphanOrder)).isNull();
    }

    @Test
    void backfill_fallsBackToTheSoleRestaurantForOrphans() {
        restaurantRepository.delete(restaurantB);
        Session orphan = legacySession(UUID.randomUUID());
        KitchenOrder orphanOrder = legacyKitchenOrder(orphan.getId());

        MongoTenantBackfill.Result result = backfill.backfill();

        assertThat(result.unresolvedSessions()).isZero();
        assertThat(result.unresolvedKitchenOrders()).isZero();
        assertThat(tenantOf(orphan)).isEqualTo(restaurantA.getId());
        assertThat(tenantOf(orphanOrder)).isEqualTo(restaurantA.getId());
    }

    @Test
    void backfill_doesNotTouchAlreadyTenantedDocuments() {
        UUID tableId = tableFor(restaurantA.getId(), 3);
        Session alreadyTenanted = sessionRepository.save(Session.builder()
                .tenantId(restaurantB.getId()).tableId(tableId).waiterId("waiter@test.com")
                .status(SessionStatus.OPEN).maxParticipants(4)
                .createdAt(LocalDateTime.now()).build());
        legacySession(tableId);

        MongoTenantBackfill.Result result = backfill.backfill();

        assertThat(result.sessionsUpdated()).isEqualTo(1);
        assertThat(tenantOf(alreadyTenanted)).isEqualTo(restaurantB.getId());
    }

    @Test
    void backfill_isIdempotentAndAuditsOnlyRunsThatChangedDocuments() {
        legacySession(tableFor(restaurantA.getId(), 4));

        MongoTenantBackfill.Result first = backfill.backfill();
        MongoTenantBackfill.Result second = backfill.backfill();

        assertThat(first.sessionsUpdated()).isEqualTo(1);
        assertThat(second.changedNothing()).isTrue();
        assertThat(mongoTemplate.getCollection(MongoTenantBackfill.AUDIT_COLLECTION)
                .countDocuments()).isEqualTo(1);
    }
}
