package com.vanter.ember.billing.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.vanter.ember.billing.model.Bill;
import com.vanter.ember.billing.model.BillStatus;
import com.vanter.ember.billing.model.SplitMethod;
import com.vanter.ember.config.AbstractTenantIsolationTest;
import java.math.BigDecimal;
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

    @Test
    void delete_cannotReachAnotherTenantsBill() {
        billSavedFor(TENANT_A, "sess-1");

        asTenant(TENANT_B, () -> billRepository.deleteAll());

        assertThat(readAs(TENANT_A, () -> billRepository.findAll())).hasSize(1);
    }
}
