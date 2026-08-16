package com.vanter.ember.cashregister.repository;

import com.vanter.ember.cashregister.model.CashMovement;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CashMovementRepository extends JpaRepository<CashMovement, Long> {

    List<CashMovement> findByCashShiftIdOrderByCreatedAtAsc(Long cashShiftId);

    @Query("""
            select coalesce(sum(m.amount), 0) from CashMovement m
            where m.cashShiftId = :cashShiftId
              and m.type = com.vanter.ember.cashregister.model.CashMovementType.CASH_IN
            """)
    BigDecimal sumCashIn(@Param("cashShiftId") Long cashShiftId);

    @Query("""
            select coalesce(sum(m.amount), 0) from CashMovement m
            where m.cashShiftId = :cashShiftId
              and m.type = com.vanter.ember.cashregister.model.CashMovementType.CASH_OUT
            """)
    BigDecimal sumCashOut(@Param("cashShiftId") Long cashShiftId);
}
