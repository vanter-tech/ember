package com.vanter.ember.billing.repository;

import com.vanter.ember.billing.model.Payment;
import com.vanter.ember.billing.model.PaymentStatus;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByBillId(Long billId);

    List<Payment> findByStatus(PaymentStatus status);

    /**
     * Money actually collected by the given tenant between {@code from} and {@code to} (both
     * inclusive). Only {@code CONFIRMED} payments count — a {@code PENDING} digital payment is a
     * gateway round-trip that may never land, and counting it would overstate revenue.
     *
     * <p>Returns null when the window holds no confirmed payments. The {@code tenantId} predicate is
     * redundant with Hibernate's {@code @TenantId} filter and kept deliberately: analytics reads are
     * the one place a silent tenant-context slip would surface as plausible-looking numbers rather
     * than an error.
     */
    @Query(
            """
            select sum(p.amount)
            from Payment p
            where p.tenantId = :tenantId
              and p.status = com.vanter.ember.billing.model.PaymentStatus.CONFIRMED
              and p.createdAt >= :from
              and p.createdAt <= :to
            """)
    BigDecimal sumConfirmedRevenue(
            @Param("tenantId") UUID tenantId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    /**
     * The same confirmed revenue as {@link #sumConfirmedRevenue}, split into one row per calendar
     * day that actually saw a payment — days with no revenue are simply absent, and the caller
     * zero-fills them. Ordered oldest-first. Carries the same deliberate {@code tenantId} predicate.
     */
    @Query(
            """
            select new com.vanter.ember.billing.repository.PaymentDailyRevenue(
                year(p.createdAt), month(p.createdAt), day(p.createdAt), sum(p.amount))
            from Payment p
            where p.tenantId = :tenantId
              and p.status = com.vanter.ember.billing.model.PaymentStatus.CONFIRMED
              and p.createdAt >= :from
              and p.createdAt <= :to
            group by year(p.createdAt), month(p.createdAt), day(p.createdAt)
            order by year(p.createdAt), month(p.createdAt), day(p.createdAt)
            """)
    List<PaymentDailyRevenue> findConfirmedRevenueByDay(
            @Param("tenantId") UUID tenantId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    /**
     * Confirmed PHYSICAL payments attributed to one shift — the sales half of that shift's
     * expected-cash figure at close (see {@code CashShiftService#closeShift}).
     */
    @Query("""
            select coalesce(sum(p.amount), 0) from Payment p
            where p.tenantId = :tenantId
              and p.cashShiftId = :cashShiftId
              and p.method = com.vanter.ember.billing.model.PaymentMethod.PHYSICAL
              and p.status = com.vanter.ember.billing.model.PaymentStatus.CONFIRMED
            """)
    BigDecimal sumConfirmedPhysicalForShift(
            @Param("tenantId") UUID tenantId, @Param("cashShiftId") Long cashShiftId);

    /**
     * Confirmed DIGITAL payments in a time window — DIGITAL payments carry no {@code
     * cashShiftId} (they're not physical cash), so a shift's digital-sales figure is windowed by
     * {@code openedAt}..{@code closedAt} instead of joined by id.
     */
    @Query("""
            select coalesce(sum(p.amount), 0) from Payment p
            where p.tenantId = :tenantId
              and p.method = com.vanter.ember.billing.model.PaymentMethod.DIGITAL
              and p.status = com.vanter.ember.billing.model.PaymentStatus.CONFIRMED
              and p.createdAt >= :from
              and p.createdAt <= :to
            """)
    BigDecimal sumConfirmedDigitalInWindow(
            @Param("tenantId") UUID tenantId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    /** Physical payments landed in one shift — the historical-dispute lookup surface for a refund. */
    List<Payment> findByCashShiftId(Long cashShiftId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.id = :id")
    Optional<Payment> findByIdForUpdate(@Param("id") Long id);

    /** Guards {@code BillingService#voidBill} — a bill with a confirmed payment must be refunded, not voided. */
    @Query("select count(p) > 0 from Payment p where p.bill.id = :billId and p.status = :status")
    boolean existsByBillIdAndStatus(@Param("billId") Long billId, @Param("status") PaymentStatus status);
}
