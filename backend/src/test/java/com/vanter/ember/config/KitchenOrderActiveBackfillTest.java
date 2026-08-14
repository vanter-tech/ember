package com.vanter.ember.config;

import com.vanter.ember.kitchen.model.KitchenOrder;
import com.vanter.ember.kitchen.repository.KitchenOrderRepository;
import com.vanter.ember.session.model.Session;
import com.vanter.ember.session.model.SessionStatus;
import com.vanter.ember.session.repository.SessionRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.UUID;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the {@code active} backfill for kitchen orders written before the field existed — the
 * fix for stale, long-closed-session tickets lingering on the live KDS display.
 */
@SpringBootTest
class KitchenOrderActiveBackfillTest {

    @Autowired KitchenOrderActiveBackfill backfill;
    @Autowired MongoTemplate mongoTemplate;
    @Autowired SessionRepository sessionRepository;
    @Autowired KitchenOrderRepository kitchenOrderRepository;

    @BeforeEach
    void setUp() {
        sessionRepository.deleteAll();
        kitchenOrderRepository.deleteAll();
        mongoTemplate.dropCollection(KitchenOrderActiveBackfill.AUDIT_COLLECTION);
    }

    private Session session(SessionStatus status) {
        return sessionRepository.save(Session.builder()
                .tenantId(UUID.randomUUID()).tableId(UUID.randomUUID()).waiterId("waiter@test.com")
                .status(status).maxParticipants(4).createdAt(LocalDateTime.now()).build());
    }

    /** A kitchen order as it was written before this fix introduced {@code active}. */
    private KitchenOrder legacyKitchenOrder(String sessionId) {
        KitchenOrder saved = kitchenOrderRepository.save(KitchenOrder.builder()
                .sessionId(sessionId).tableNumber(5)
                .createdAt(LocalDateTime.now()).items(new ArrayList<>()).build());
        // KitchenOrder.active defaults to true via @Builder.Default — strip it back out so the
        // document matches what a pre-fix write actually looked like (field absent).
        mongoTemplate.updateFirst(
                new Query(Criteria.where("_id").is(saved.getId())),
                new Update().unset("active"),
                KitchenOrder.class);
        return saved;
    }

    private boolean activeOf(KitchenOrder order) {
        return mongoTemplate.findById(order.getId(), KitchenOrder.class).isActive();
    }

    @Test
    void backfill_activatesOrdersWhoseSessionIsStillOpen() {
        Session open = session(SessionStatus.OPEN);
        KitchenOrder order = legacyKitchenOrder(open.getId());

        KitchenOrderActiveBackfill.Result result = backfill.backfill();

        assertThat(result.activated()).isEqualTo(1);
        assertThat(result.retired()).isZero();
        assertThat(activeOf(order)).isTrue();
    }

    @Test
    void backfill_retiresOrdersWhoseSessionIsClosed() {
        Session closed = session(SessionStatus.CLOSED);
        KitchenOrder order = legacyKitchenOrder(closed.getId());

        KitchenOrderActiveBackfill.Result result = backfill.backfill();

        assertThat(result.retired()).isEqualTo(1);
        assertThat(result.activated()).isZero();
        assertThat(activeOf(order)).isFalse();
    }

    @Test
    void backfill_retiresOrdersWhoseSessionNoLongerExists() {
        KitchenOrder orphan = legacyKitchenOrder("deleted-session-id");

        KitchenOrderActiveBackfill.Result result = backfill.backfill();

        assertThat(result.unresolved()).isEqualTo(1);
        assertThat(result.retired()).isEqualTo(1);
        assertThat(activeOf(orphan)).isFalse();
    }

    @Test
    void backfill_doesNotTouchDocumentsThatAlreadyHaveActive() {
        Session closed = session(SessionStatus.CLOSED);
        KitchenOrder alreadySet = kitchenOrderRepository.save(KitchenOrder.builder()
                .sessionId(closed.getId()).tableNumber(5).active(true)
                .createdAt(LocalDateTime.now()).items(new ArrayList<>()).build());

        KitchenOrderActiveBackfill.Result result = backfill.backfill();

        assertThat(result.changedNothing()).isTrue();
        assertThat(activeOf(alreadySet)).isTrue();
    }

    @Test
    void backfill_isIdempotentAndAuditsOnlyRunsThatChangedDocuments() {
        legacyKitchenOrder(session(SessionStatus.OPEN).getId());

        KitchenOrderActiveBackfill.Result first = backfill.backfill();
        KitchenOrderActiveBackfill.Result second = backfill.backfill();

        assertThat(first.activated()).isEqualTo(1);
        assertThat(second.changedNothing()).isTrue();
        assertThat(mongoTemplate.getCollection(KitchenOrderActiveBackfill.AUDIT_COLLECTION)
                .countDocuments(new Document("changeId", KitchenOrderActiveBackfill.CHANGE_ID)))
                .isEqualTo(1);
    }
}
