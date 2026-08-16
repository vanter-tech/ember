package com.vanter.ember.config;

import com.vanter.ember.kitchen.model.KitchenOrder;
import com.vanter.ember.session.model.Session;
import com.vanter.ember.session.model.SessionStatus;
import java.time.Instant;
import java.util.Date;
import java.util.List;
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
 * Backfills {@code active} on {@code kitchen_orders} documents written before the field existed.
 * A missing field can't be trusted to default correctly either way: treating it as inactive would
 * also hide tickets from currently-open sessions that predate this change, and treating it as
 * active would keep showing the stale, long-closed-session tickets the field exists to hide. So
 * each document's true state is resolved from its session instead of guessed.
 *
 * <p>Re-running is safe: only documents still missing {@code active} are touched.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KitchenOrderActiveBackfill implements ApplicationRunner {

    static final String AUDIT_COLLECTION = "mongo_migrations";
    static final String CHANGE_ID = "kitchen-order-active-backfill";

    private final MongoTemplate mongoTemplate;

    /** Outcome of one backfill pass, in documents. */
    public record Result(long activated, long retired, long unresolved) {

        public boolean changedNothing() {
            return activated == 0 && retired == 0;
        }
    }

    @Override
    public void run(ApplicationArguments args) {
        backfill();
    }

    public Result backfill() {
        List<KitchenOrder> missingActive = mongoTemplate.find(new Query(missingActive()), KitchenOrder.class);
        if (missingActive.isEmpty()) {
            return new Result(0, 0, 0);
        }

        long activated = 0;
        long retired = 0;
        long unresolved = 0;

        for (KitchenOrder order : missingActive) {
            Session session = order.getSessionId() == null
                    ? null
                    : mongoTemplate.findById(order.getSessionId(), Session.class);
            if (session == null) {
                unresolved++;
            }

            boolean active = session != null && session.getStatus() != SessionStatus.CLOSED;
            mongoTemplate.updateFirst(
                    new Query(Criteria.where("_id").is(order.getId())),
                    new Update().set("active", active),
                    KitchenOrder.class);

            if (active) {
                activated++;
            } else {
                retired++;
            }
        }

        Result result = new Result(activated, retired, unresolved);
        report(result);
        recordAudit(result);
        return result;
    }

    private Criteria missingActive() {
        return Criteria.where("active").exists(false);
    }

    private void report(Result result) {
        log.info("{}: activated {} kitchen order(s), retired {} kitchen order(s) "
                        + "({} with no resolvable session, retired by default)",
                CHANGE_ID, result.activated(), result.retired(), result.unresolved());
    }

    private void recordAudit(Result result) {
        mongoTemplate.insert(new Document()
                .append("changeId", CHANGE_ID)
                .append("appliedAt", Date.from(Instant.now()))
                .append("activated", result.activated())
                .append("retired", result.retired())
                .append("unresolved", result.unresolved()),
                AUDIT_COLLECTION);
    }
}
