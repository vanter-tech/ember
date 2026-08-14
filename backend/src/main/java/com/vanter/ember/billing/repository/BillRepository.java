package com.vanter.ember.billing.repository;

import com.vanter.ember.billing.model.Bill;
import com.vanter.ember.billing.model.BillStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BillRepository extends JpaRepository<Bill, Long> {

    Optional<Bill> findBySessionId(String sessionId);

    List<Bill> findByStatus(BillStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Bill b where b.id = :id")
    Optional<Bill> findByIdForUpdate(@Param("id") Long id);

    /**
     * Earliest/latest bill for the given tenant. The {@code tenantId} predicate is redundant with
     * Hibernate's {@code @TenantId} filter and kept deliberately: analytics reads are the one place
     * a silent tenant-context slip would surface as plausible-looking numbers rather than an error.
     */
    @Query(
            """
            select new com.vanter.ember.billing.repository.BillActivityWindow(
                min(b.createdAt), max(b.createdAt), count(b))
            from Bill b
            where b.tenantId = :tenantId
            """)
    BillActivityWindow findActivityWindow(@Param("tenantId") UUID tenantId);

    /**
     * How many bills the given tenant settled between {@code from} and {@code to} (both inclusive)
     * and what they summed to — the numerator/denominator behind the dashboard's average order
     * value. Only {@code PAID} bills count: an {@code OPEN} bill is a table still eating, not a
     * sale. Carries the same deliberate {@code tenantId} predicate as {@link #findActivityWindow}.
     */
    @Query(
            """
            select new com.vanter.ember.billing.repository.BillSalesTotals(
                count(b), sum(b.total))
            from Bill b
            where b.tenantId = :tenantId
              and b.status = com.vanter.ember.billing.model.BillStatus.PAID
              and b.createdAt >= :from
              and b.createdAt <= :to
            """)
    BillSalesTotals findSalesTotals(
            @Param("tenantId") UUID tenantId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    /**
     * The {@code PAID} bill count behind {@link #findSalesTotals}, split into one row per calendar
     * day that actually settled a bill — quiet days are absent and the caller zero-fills them.
     * Ordered oldest-first. Carries the same deliberate {@code tenantId} predicate.
     */
    @Query(
            """
            select new com.vanter.ember.billing.repository.BillDailyOrders(
                year(b.createdAt), month(b.createdAt), day(b.createdAt), count(b))
            from Bill b
            where b.tenantId = :tenantId
              and b.status = com.vanter.ember.billing.model.BillStatus.PAID
              and b.createdAt >= :from
              and b.createdAt <= :to
            group by year(b.createdAt), month(b.createdAt), day(b.createdAt)
            order by year(b.createdAt), month(b.createdAt), day(b.createdAt)
            """)
    List<BillDailyOrders> findPaidBillsByDay(
            @Param("tenantId") UUID tenantId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    /**
     * The sessions behind the {@code PAID} bills of {@link #findSalesTotals}. Product analytics has
     * to cross stores — the line items live on the Mongo {@code Session} — so this hands back the
     * settled session ids to look up there, keeping "a sale is a settled bill" the single rule every
     * analytics read shares. Carries the same deliberate {@code tenantId} predicate.
     */
    @Query(
            """
            select b.sessionId
            from Bill b
            where b.tenantId = :tenantId
              and b.status = com.vanter.ember.billing.model.BillStatus.PAID
              and b.createdAt >= :from
              and b.createdAt <= :to
            """)
    List<String> findPaidSessionIds(
            @Param("tenantId") UUID tenantId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    /**
     * The {@code PAID} bills behind {@link #findSalesTotals}, with the session id, total and
     * settlement instant table analytics needs to attribute revenue and turnovers to a table.
     * Carries the same deliberate {@code tenantId} predicate as {@link #findActivityWindow}.
     */
    @Query(
            """
            select new com.vanter.ember.billing.repository.PaidBillActivity(
                b.sessionId, b.total, b.createdAt)
            from Bill b
            where b.tenantId = :tenantId
              and b.status = com.vanter.ember.billing.model.BillStatus.PAID
              and b.createdAt >= :from
              and b.createdAt <= :to
            """)
    List<PaidBillActivity> findPaidBillActivity(
            @Param("tenantId") UUID tenantId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);
}
