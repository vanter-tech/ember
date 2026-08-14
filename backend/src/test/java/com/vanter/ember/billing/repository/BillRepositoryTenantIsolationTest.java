package com.vanter.ember.billing.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.vanter.ember.billing.model.Bill;
import com.vanter.ember.billing.model.BillStatus;
import com.vanter.ember.billing.model.SplitMethod;
import com.vanter.ember.config.AbstractTenantIsolationTest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class BillRepositoryTenantIsolationTest extends AbstractTenantIsolationTest {

    @Autowired BillRepository billRepository;

    @Override
    protected void deleteAll() {
        billRepository.deleteAll();
    }

    private Bill billSavedFor(UUID tenantId, String sessionId) {
        return readAs(
                tenantId,
                () ->
                        billRepository.saveAndFlush(
                                Bill.builder()
                                        .sessionId(sessionId)
                                        .total(new BigDecimal("45.00"))
                                        .splitMethod(SplitMethod.BY_CONSUMPTION)
                                        .status(BillStatus.OPEN)
                                        .createdAt(LocalDateTime.now())
                                        .build()));
    }

    @Test
    void save_stampsTheBoundTenant() {
        Bill saved = billSavedFor(TENANT_A, "sess-1");

        assertThat(saved.getTenantId()).isEqualTo(TENANT_A);
    }

    @Test
    void findBySessionId_doesNotResolveAnotherTenantsBill() {
        billSavedFor(TENANT_A, "sess-1");

        assertThat(readAs(TENANT_B, () -> billRepository.findBySessionId("sess-1"))).isEmpty();
        assertThat(readAs(TENANT_A, () -> billRepository.findBySessionId("sess-1"))).isPresent();
    }

    @Test
    void findByStatus_onlyReturnsTheBoundTenantsBills() {
        billSavedFor(TENANT_A, "sess-1");
        billSavedFor(TENANT_B, "sess-2");

        List<Bill> openForB = readAs(TENANT_B, () -> billRepository.findByStatus(BillStatus.OPEN));

        assertThat(openForB).hasSize(1);
        assertThat(openForB.get(0).getSessionId()).isEqualTo("sess-2");
    }

    @Test
    void findById_doesNotLeakAnotherTenantsBill() {
        Long id = billSavedFor(TENANT_A, "sess-1").getId();

        assertThat(readAs(TENANT_B, () -> billRepository.findById(id))).isEmpty();
        assertThat(readAs(TENANT_A, () -> billRepository.findById(id))).isPresent();
    }

    @Test
    void sameSessionId_isAllowedInTwoTenants() {
        billSavedFor(TENANT_A, "sess-shared");
        billSavedFor(TENANT_B, "sess-shared");

        assertThat(readAs(TENANT_A, () -> billRepository.findAll())).hasSize(1);
        assertThat(readAs(TENANT_B, () -> billRepository.findAll())).hasSize(1);
    }

    @Test
    void findActivityWindow_onlyAggregatesTheBoundTenantsBills() {
        billSavedFor(TENANT_A, "sess-1");
        billSavedFor(TENANT_B, "sess-2");
        billSavedFor(TENANT_B, "sess-3");

        var windowForB = readAs(TENANT_B, () -> billRepository.findActivityWindow(TENANT_B));

        assertThat(windowForB.billCount()).isEqualTo(2L);
        assertThat(windowForB.firstBillAt()).isNotNull();
        assertThat(windowForB.lastBillAt()).isNotNull();
    }

    @Test
    void findActivityWindow_isEmptyForATenantWithNoBills() {
        billSavedFor(TENANT_A, "sess-1");

        var windowForB = readAs(TENANT_B, () -> billRepository.findActivityWindow(TENANT_B));

        assertThat(windowForB.billCount()).isZero();
        assertThat(windowForB.firstBillAt()).isNull();
        assertThat(windowForB.lastBillAt()).isNull();
    }

    private Bill billSavedFor(
            UUID tenantId, String sessionId, BillStatus status, String total, LocalDateTime createdAt) {
        return readAs(
                tenantId,
                () ->
                        billRepository.saveAndFlush(
                                Bill.builder()
                                        .sessionId(sessionId)
                                        .total(new BigDecimal(total))
                                        .splitMethod(SplitMethod.BY_CONSUMPTION)
                                        .status(status)
                                        .createdAt(createdAt)
                                        .build()));
    }

    @Test
    void findSalesTotals_onlyAggregatesTheBoundTenantsBills() {
        LocalDateTime when = LocalDateTime.of(2026, 8, 10, 20, 0);
        billSavedFor(TENANT_A, "sess-1", BillStatus.PAID, "100.00", when);
        billSavedFor(TENANT_B, "sess-2", BillStatus.PAID, "30.00", when);
        billSavedFor(TENANT_B, "sess-3", BillStatus.PAID, "20.00", when);

        var totalsForB = readAs(
                TENANT_B,
                () -> billRepository.findSalesTotals(
                        TENANT_B, when.minusDays(1), when.plusDays(1)));

        assertThat(totalsForB.billCount()).isEqualTo(2L);
        assertThat(totalsForB.salesTotal()).isEqualByComparingTo("50.00");
    }

    @Test
    void findSalesTotals_ignoresOpenBillsAndBillsOutsideTheWindow() {
        LocalDateTime inWindow = LocalDateTime.of(2026, 8, 10, 20, 0);
        billSavedFor(TENANT_A, "sess-paid", BillStatus.PAID, "40.00", inWindow);
        billSavedFor(TENANT_A, "sess-open", BillStatus.OPEN, "999.00", inWindow);
        billSavedFor(TENANT_A, "sess-old", BillStatus.PAID, "999.00", inWindow.minusMonths(2));

        var totals = readAs(
                TENANT_A,
                () -> billRepository.findSalesTotals(
                        TENANT_A, inWindow.minusDays(1), inWindow.plusDays(1)));

        assertThat(totals.billCount()).isEqualTo(1L);
        assertThat(totals.salesTotal()).isEqualByComparingTo("40.00");
    }

    @Test
    void findSalesTotals_isEmptyForATenantWithNoPaidBills() {
        LocalDateTime when = LocalDateTime.of(2026, 8, 10, 20, 0);
        billSavedFor(TENANT_A, "sess-1", BillStatus.PAID, "40.00", when);

        var totalsForB = readAs(
                TENANT_B,
                () -> billRepository.findSalesTotals(
                        TENANT_B, when.minusDays(1), when.plusDays(1)));

        assertThat(totalsForB.billCount()).isZero();
        assertThat(totalsForB.salesTotal()).isNull();
    }

    @Test
    void findPaidBillsByDay_groupsTheBoundTenantsPaidBillsPerCalendarDay() {
        LocalDateTime firstDay = LocalDateTime.of(2026, 8, 10, 20, 0);
        billSavedFor(TENANT_A, "sess-noise", BillStatus.PAID, "999.00", firstDay);
        billSavedFor(TENANT_B, "sess-1", BillStatus.PAID, "30.00", firstDay);
        billSavedFor(TENANT_B, "sess-2", BillStatus.PAID, "20.00", firstDay.plusHours(2));
        billSavedFor(TENANT_B, "sess-3", BillStatus.PAID, "10.00", firstDay.plusDays(2));
        billSavedFor(TENANT_B, "sess-open", BillStatus.OPEN, "999.00", firstDay);

        List<BillDailyOrders> daysForB = readAs(
                TENANT_B,
                () -> billRepository.findPaidBillsByDay(
                        TENANT_B, firstDay.minusDays(1), firstDay.plusDays(3)));

        assertThat(daysForB).hasSize(2);
        assertThat(daysForB.get(0).date()).isEqualTo(LocalDate.of(2026, 8, 10));
        assertThat(daysForB.get(0).billCount()).isEqualTo(2L);
        assertThat(daysForB.get(1).date()).isEqualTo(LocalDate.of(2026, 8, 12));
        assertThat(daysForB.get(1).billCount()).isEqualTo(1L);
    }

    @Test
    void findPaidBillsByDay_isEmptyOutsideTheWindow() {
        LocalDateTime when = LocalDateTime.of(2026, 8, 10, 20, 0);
        billSavedFor(TENANT_A, "sess-1", BillStatus.PAID, "40.00", when);

        assertThat(readAs(
                        TENANT_A,
                        () -> billRepository.findPaidBillsByDay(
                                TENANT_A, when.minusDays(3), when.minusDays(2))))
                .isEmpty();
    }

    @Test
    void delete_cannotReachAnotherTenantsBill() {
        billSavedFor(TENANT_A, "sess-1");

        asTenant(TENANT_B, () -> billRepository.deleteAll());

        assertThat(readAs(TENANT_A, () -> billRepository.findAll())).hasSize(1);
    }
}
