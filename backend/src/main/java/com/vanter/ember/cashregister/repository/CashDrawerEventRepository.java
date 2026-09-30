package com.vanter.ember.cashregister.repository;

import com.vanter.ember.cashregister.model.CashDrawerEvent;
import com.vanter.ember.cashregister.model.CashDrawerEventStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CashDrawerEventRepository extends JpaRepository<CashDrawerEvent, UUID> {

    /** Every pending receipt (even from an earlier shift) plus all events of {@code shiftId}. */
    @Query("SELECT e FROM CashDrawerEvent e "
            + "WHERE e.status = com.vanter.ember.cashregister.model.CashDrawerEventStatus.PENDING "
            + "OR e.cashShiftId = :shiftId ORDER BY e.createdAt DESC")
    List<CashDrawerEvent> findForPanelByShift(@Param("shiftId") Long shiftId);

    /** Pending receipts only, used when the tenant has no open shift. */
    List<CashDrawerEvent> findByStatusOrderByCreatedAtDesc(CashDrawerEventStatus status);

    List<CashDrawerEvent> findByPaymentIdIn(java.util.Collection<Long> paymentIds);

    default List<CashDrawerEvent> findForPanel(UUID tenantId, Long shiftId) {
        // tenantId is enforced by Hibernate's @TenantId filter; kept in the signature so callers
        // (and tests) state which tenant they mean.
        return shiftId == null
                ? findByStatusOrderByCreatedAtDesc(CashDrawerEventStatus.PENDING)
                : findForPanelByShift(shiftId);
    }
}
