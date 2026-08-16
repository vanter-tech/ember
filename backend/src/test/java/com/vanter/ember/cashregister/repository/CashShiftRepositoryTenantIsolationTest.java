package com.vanter.ember.cashregister.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.vanter.ember.cashregister.model.CashMovement;
import com.vanter.ember.cashregister.model.CashMovementType;
import com.vanter.ember.cashregister.model.CashShift;
import com.vanter.ember.cashregister.model.CashShiftStatus;
import com.vanter.ember.config.AbstractTenantIsolationTest;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class CashShiftRepositoryTenantIsolationTest extends AbstractTenantIsolationTest {

    @Autowired CashShiftRepository cashShiftRepository;
    @Autowired CashMovementRepository cashMovementRepository;

    @Override
    protected void deleteAll() {
        cashMovementRepository.deleteAll();
        cashShiftRepository.deleteAll();
    }

    private CashShift openShiftFor(UUID tenantId, int shiftNumber) {
        return readAs(
                tenantId,
                () -> cashShiftRepository.save(
                        CashShift.builder()
                                .shiftNumber(shiftNumber)
                                .status(CashShiftStatus.OPEN)
                                .openingFloat(new BigDecimal("100.00"))
                                .openedBy("user-1")
                                .openedAt(LocalDateTime.now())
                                .build()));
    }

    @Test
    void save_stampsTheBoundTenant() {
        CashShift saved = openShiftFor(TENANT_A, 1);

        assertThat(saved.getTenantId()).isEqualTo(TENANT_A);
    }

    @Test
    void findByTenantIdAndStatus_doesNotLeakAnotherTenantsOpenShift() {
        openShiftFor(TENANT_A, 1);

        assertThat(readAs(TENANT_B,
                () -> cashShiftRepository.findByTenantIdAndStatus(TENANT_B, CashShiftStatus.OPEN)))
                .isEmpty();
        assertThat(readAs(TENANT_A,
                () -> cashShiftRepository.findByTenantIdAndStatus(TENANT_A, CashShiftStatus.OPEN)))
                .isPresent();
    }

    @Test
    void findMaxShiftNumber_isZeroWhenTenantHasNoShiftsYet() {
        assertThat(readAs(TENANT_B, () -> cashShiftRepository.findMaxShiftNumber(TENANT_B)))
                .isZero();
    }

    @Test
    void findMaxShiftNumber_ignoresAnotherTenantsShifts() {
        openShiftFor(TENANT_A, 5);

        assertThat(readAs(TENANT_B, () -> cashShiftRepository.findMaxShiftNumber(TENANT_B)))
                .isZero();
        assertThat(readAs(TENANT_A, () -> cashShiftRepository.findMaxShiftNumber(TENANT_A)))
                .isEqualTo(5);
    }

    private CashMovement movementOn(CashShift shift, UUID tenantId, CashMovementType type, String amount) {
        return readAs(
                tenantId,
                () -> cashMovementRepository.save(
                        CashMovement.builder()
                                .cashShiftId(shift.getId())
                                .type(type)
                                .amount(new BigDecimal(amount))
                                .reason("test movement")
                                .createdBy("user-1")
                                .createdAt(LocalDateTime.now())
                                .build()));
    }

    @Test
    void sumCashInAndSumCashOut_aggregateOnlyThatShiftsMovements() {
        CashShift shift = openShiftFor(TENANT_A, 1);
        movementOn(shift, TENANT_A, CashMovementType.CASH_IN, "20.00");
        movementOn(shift, TENANT_A, CashMovementType.CASH_IN, "5.00");
        movementOn(shift, TENANT_A, CashMovementType.CASH_OUT, "8.00");

        assertThat(readAs(TENANT_A, () -> cashMovementRepository.sumCashIn(shift.getId())))
                .isEqualByComparingTo("25.00");
        assertThat(readAs(TENANT_A, () -> cashMovementRepository.sumCashOut(shift.getId())))
                .isEqualByComparingTo("8.00");
    }

    @Test
    void findByCashShiftIdOrderByCreatedAtAsc_returnsOldestFirst() {
        CashShift shift = openShiftFor(TENANT_A, 1);
        LocalDateTime now = LocalDateTime.now();
        readAs(TENANT_A, () -> cashMovementRepository.save(CashMovement.builder()
                .cashShiftId(shift.getId()).type(CashMovementType.CASH_IN)
                .amount(new BigDecimal("5.00")).reason("second").createdBy("user-1")
                .createdAt(now.plusMinutes(5)).build()));
        readAs(TENANT_A, () -> cashMovementRepository.save(CashMovement.builder()
                .cashShiftId(shift.getId()).type(CashMovementType.CASH_IN)
                .amount(new BigDecimal("5.00")).reason("first").createdBy("user-1")
                .createdAt(now).build()));

        List<CashMovement> movements = readAs(TENANT_A,
                () -> cashMovementRepository.findByCashShiftIdOrderByCreatedAtAsc(shift.getId()));

        assertThat(movements).extracting(CashMovement::getReason).containsExactly("first", "second");
    }
}
