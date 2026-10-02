package com.vanter.ember.billing.repository;

import com.vanter.ember.billing.model.Refund;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefundRepository extends JpaRepository<Refund, Long> {

    List<Refund> findByPaymentId(Long paymentId);

    /** Refunds of several payments (a shift's), oldest first — the close-out view's audit rows. */
    @Query("select r from Refund r where r.payment.id in :paymentIds order by r.createdAt asc, r.id asc")
    List<Refund> findByPaymentIdIn(@Param("paymentIds") Collection<Long> paymentIds);

    @Query("select coalesce(sum(r.amount), 0) from Refund r where r.payment.id = :paymentId")
    BigDecimal sumByPaymentId(@Param("paymentId") Long paymentId);

    /** Total refunded in a window — the deduction {@code AnalyticsService#getSummary} nets against revenue. */
    @Query("""
            select coalesce(sum(r.amount), 0) from Refund r
            where r.tenantId = :tenantId
              and r.createdAt >= :from
              and r.createdAt <= :to
            """)
    BigDecimal sumRefundsInWindow(
            @Param("tenantId") UUID tenantId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    /** Same total as {@link #sumRefundsInWindow}, split by calendar day for the sales-series chart. */
    @Query("""
            select new com.vanter.ember.billing.repository.RefundDailyAmount(
                year(r.createdAt), month(r.createdAt), day(r.createdAt), sum(r.amount))
            from Refund r
            where r.tenantId = :tenantId
              and r.createdAt >= :from
              and r.createdAt <= :to
            group by year(r.createdAt), month(r.createdAt), day(r.createdAt)
            order by year(r.createdAt), month(r.createdAt), day(r.createdAt)
            """)
    List<RefundDailyAmount> findRefundsByDay(
            @Param("tenantId") UUID tenantId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);
}
