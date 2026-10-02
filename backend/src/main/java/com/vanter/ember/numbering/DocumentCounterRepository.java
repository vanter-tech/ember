package com.vanter.ember.numbering;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentCounterRepository extends JpaRepository<DocumentCounter, DocumentCounter.Key> {

    /** Row lock held until the caller's transaction ends: concurrent issuers queue instead of reading the same number. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from DocumentCounter c where c.tenantId = :tenantId and c.series = :series")
    Optional<DocumentCounter> findForUpdate(@Param("tenantId") UUID tenantId, @Param("series") DocumentSeries series);
}
