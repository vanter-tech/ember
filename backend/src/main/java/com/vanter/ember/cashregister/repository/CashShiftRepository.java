package com.vanter.ember.cashregister.repository;

import com.vanter.ember.cashregister.model.CashShift;
import com.vanter.ember.cashregister.model.CashShiftStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CashShiftRepository extends JpaRepository<CashShift, Long> {

    Optional<CashShift> findByTenantIdAndStatus(UUID tenantId, CashShiftStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from CashShift s where s.id = :id")
    Optional<CashShift> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select s from CashShift s
            where s.tenantId = :tenantId
              and s.status = com.vanter.ember.cashregister.model.CashShiftStatus.OPEN
            """)
    Optional<CashShift> findOpenForUpdate(@Param("tenantId") UUID tenantId);

    @Query("select coalesce(max(s.shiftNumber), 0) from CashShift s where s.tenantId = :tenantId")
    int findMaxShiftNumber(@Param("tenantId") UUID tenantId);

    Page<CashShift> findByTenantIdAndOpenedAtBetweenOrderByOpenedAtDesc(
            UUID tenantId, LocalDateTime from, LocalDateTime to, Pageable pageable);

    List<CashShift> findByTenantIdAndStatusAndClosedAtBetween(
            UUID tenantId, CashShiftStatus status, LocalDateTime from, LocalDateTime to);
}
