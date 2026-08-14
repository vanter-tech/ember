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
}
