package com.vanter.ember.config;

import com.vanter.ember.kitchen.model.KitchenOrder;
import com.vanter.ember.restaurant.model.Restaurant;
import com.vanter.ember.session.model.Session;
import com.vanter.ember.settings.model.DiningTables;
import com.vanter.ember.settings.repository.DiningTableRepository;
import com.vanter.ember.restaurant.repository.RestaurantRepository;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

/**
 * Backfills {@code tenantId} on {@code sessions}/{@code kitchen_orders} documents written before
 * task-2.17 made the field mandatory. It is the MongoDB counterpart of Flyway's {@code V2} — Mongo
 * has no migration runner here, so this runs at startup instead.
 *
 * <p>Ownership is derived per document rather than guessed: a session belongs to the restaurant
 * that owns its dining table, and a kitchen order belongs to its session's tenant. Documents whose
 * evidence is gone (deleted table, deleted session) are left untenanted unless the deployment has
 * exactly one restaurant, in which case that one is used — the same rule {@code V2} applies on the
 * SQL side. Leftovers are reported at ERROR but never abort startup: an untenanted document is
 * invisible to every tenant-scoped query, so the failure mode is missing data, not leaked data.
 *
 * <p>Re-running is safe: only documents still missing a {@code tenantId} are touched.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MongoTenantBackfill implements ApplicationRunner {

    static final String AUDIT_COLLECTION = "mongo_migrations";
    static final String CHANGE_ID = "task-2.18-mongo-tenant-backfill";

    private final MongoTemplate mongoTemplate;
    private final RestaurantRepository restaurantRepository;
    private final DiningTableRepository diningTableRepository;

    /** Outcome of one backfill pass, in documents. */
    public record Result(
            long sessionsUpdated,
            long kitchenOrdersUpdated,
            long unresolvedSessions,
            long unresolvedKitchenOrders) {

        public boolean changedNothing() {
            return sessionsUpdated == 0 && kitchenOrdersUpdated == 0;
        }
    }

    @Override
    public void run(ApplicationArguments args) {
        backfill();
    }

    public Result backfill() {
        if (countUntenanted(Session.class) == 0 && countUntenanted(KitchenOrder.class) == 0) {
            return new Result(0, 0, 0, 0);
        }

        long sessionsUpdated = backfillSessions();
        long kitchenOrdersUpdated = backfillKitchenOrders();

        List<Restaurant> restaurants = restaurantRepository.findAll();
        if (restaurants.size() == 1) {
            UUID soleTenant = restaurants.get(0).getId();
            sessionsUpdated += assignRemaining(Session.class, soleTenant);
            kitchenOrdersUpdated += assignRemaining(KitchenOrder.class, soleTenant);
        }

        Result result = new Result(
                sessionsUpdated,
                kitchenOrdersUpdated,
                countUntenanted(Session.class),
                countUntenanted(KitchenOrder.class));

        report(result);
        if (!result.changedNothing()) {
            recordAudit(result);
        }
        return result;
    }

    /**
     * Stamps each session with the tenant that owns its dining table, one bulk update per
     * restaurant.
     */
    private long backfillSessions() {
        long updated = 0;
        for (Restaurant restaurant : restaurantRepository.findAll()) {
            List<UUID> tableIds = tableIdsOf(restaurant.getId());
            if (tableIds.isEmpty()) {
                continue;
            }
            updated += mongoTemplate.updateMulti(
                    new Query(untenanted().and("tableId").in(tableIds)),
                    new Update().set("tenantId", restaurant.getId()),
                    Session.class).getModifiedCount();
        }
        return updated;
    }

    /** Kitchen orders inherit the tenant of the session they were confirmed from. */
    private long backfillKitchenOrders() {
        long updated = 0;
        for (KitchenOrder order : mongoTemplate.find(new Query(untenanted()), KitchenOrder.class)) {
            if (order.getSessionId() == null) {
                continue;
            }
            Session session = mongoTemplate.findById(order.getSessionId(), Session.class);
            if (session == null || session.getTenantId() == null) {
                continue;
            }
            updated += mongoTemplate.updateFirst(
                    new Query(Criteria.where("_id").is(order.getId())),
                    new Update().set("tenantId", session.getTenantId()),
                    KitchenOrder.class).getModifiedCount();
        }
        return updated;
    }

    /**
     * Reads a restaurant's dining tables. {@code DiningTables} is a {@code @TenantId} entity, so the
     * tenant has to be bound for the query to see anything at all; whatever was bound before is
     * restored, since a caller may run this inside a request.
     */
    private List<UUID> tableIdsOf(UUID tenantId) {
        UUID previous = TenantContextHolder.getTenantId();
        TenantContextHolder.setTenantId(tenantId);
        try {
            return diningTableRepository.findAll().stream().map(DiningTables::getId).toList();
        } finally {
            if (previous != null) {
                TenantContextHolder.setTenantId(previous);
            } else {
                TenantContextHolder.clear();
            }
        }
    }

    private long assignRemaining(Class<?> documentType, UUID tenantId) {
        return mongoTemplate.updateMulti(
                new Query(untenanted()),
                new Update().set("tenantId", tenantId),
                documentType).getModifiedCount();
    }

    private long countUntenanted(Class<?> documentType) {
        return mongoTemplate.count(new Query(untenanted()), documentType);
    }

    private Criteria untenanted() {
        return Criteria.where("tenantId").is(null);
    }

    private void report(Result result) {
        log.info("{}: tenanted {} session(s) and {} kitchen order(s)",
                CHANGE_ID, result.sessionsUpdated(), result.kitchenOrdersUpdated());

        if (result.unresolvedSessions() > 0 || result.unresolvedKitchenOrders() > 0) {
            log.error("{}: {} session(s) and {} kitchen order(s) could not be resolved to a "
                            + "restaurant and stay invisible to every tenant. Their dining table or "
                            + "session no longer exists, and more than one restaurant is registered, "
                            + "so ownership cannot be inferred — assign `tenantId` by hand.",
                    CHANGE_ID, result.unresolvedSessions(), result.unresolvedKitchenOrders());
        }
    }

    private void recordAudit(Result result) {
        mongoTemplate.insert(new Document()
                .append("changeId", CHANGE_ID)
                .append("appliedAt", Date.from(Instant.now()))
                .append("sessionsUpdated", result.sessionsUpdated())
                .append("kitchenOrdersUpdated", result.kitchenOrdersUpdated())
                .append("unresolvedSessions", result.unresolvedSessions())
                .append("unresolvedKitchenOrders", result.unresolvedKitchenOrders()),
                AUDIT_COLLECTION);
    }
}
