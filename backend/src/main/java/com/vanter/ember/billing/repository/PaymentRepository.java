package com.vanter.ember.billing.repository;

import com.vanter.ember.billing.model.Payment;
import com.vanter.ember.billing.model.PaymentStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
