package com.vanter.ember.billing.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.vanter.ember.billing.model.Bill;
import com.vanter.ember.billing.model.BillSplit;
import com.vanter.ember.billing.model.BillSplitStatus;
import com.vanter.ember.billing.model.BillStatus;
import com.vanter.ember.billing.model.SplitMethod;
import com.vanter.ember.config.AbstractTenantIsolationTest;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class BillSplitRepositoryTenantIsolationTest extends AbstractTenantIsolationTest {

    @Autowired BillRepository billRepository;
    @Autowired BillSplitRepository billSplitRepository;

    @Override
    protected void deleteAll() {
        billSplitRepository.deleteAll();
        billRepository.deleteAll();
    }

    private Bill billOf(UUID tenantId) {
        return readAs(
                tenantId,
                () ->
                        billRepository.save(
                                Bill.builder()
                                        .sessionId("sess-" + tenantId)
                                        .total(new BigDecimal("60.00"))
                                        .splitMethod(SplitMethod.EQUAL_PARTS)
                                        .status(BillStatus.OPEN)
                                        .createdAt(LocalDateTime.now())
                                        .build()));
    }

    private BillSplit splitSavedFor(UUID tenantId, String participantName) {
        Bill bill = billOf(tenantId);
        return readAs(
                tenantId,
                () ->
                        billSplitRepository.save(
                                BillSplit.builder()
                                        .bill(bill)
                                        .participantName(participantName)
                                        .amount(new BigDecimal("30.00"))
                                        .status(BillSplitStatus.UNPAID)
                                        .build()));
    }

    @Test
    void save_stampsTheBoundTenant() {
        BillSplit saved = splitSavedFor(TENANT_A, "Alice");

        assertThat(saved.getTenantId()).isEqualTo(TENANT_A);
    }

    @Test
    void findByBillId_doesNotReachAnotherTenantsSplits() {
        BillSplit tenantASplit = splitSavedFor(TENANT_A, "Alice");
        Long billId = tenantASplit.getBill().getId();

        assertThat(readAs(TENANT_B, () -> billSplitRepository.findByBillId(billId))).isEmpty();
        assertThat(readAs(TENANT_A, () -> billSplitRepository.findByBillId(billId))).hasSize(1);
    }

    @Test
    void findByBillIdAndParticipantName_doesNotReachAnotherTenantsSplit() {
        BillSplit tenantASplit = splitSavedFor(TENANT_A, "Alice");
        Long billId = tenantASplit.getBill().getId();

        assertThat(
                        readAs(
                                TENANT_B,
                                () -> billSplitRepository.findByBillIdAndParticipantName(billId, "Alice")))
                .isEmpty();
        assertThat(
                        readAs(
                                TENANT_A,
                                () -> billSplitRepository.findByBillIdAndParticipantName(billId, "Alice")))
                .isPresent();
    }

    @Test
    void findAll_onlyReturnsTheBoundTenantsSplits() {
        splitSavedFor(TENANT_A, "Alice");
        splitSavedFor(TENANT_B, "Bob");

        assertThat(readAs(TENANT_B, () -> billSplitRepository.findAll()))
                .singleElement()
                .satisfies(split -> assertThat(split.getParticipantName()).isEqualTo("Bob"));
    }

    @Test
    void findById_doesNotLeakAnotherTenantsSplit() {
        Long id = splitSavedFor(TENANT_A, "Alice").getId();

        assertThat(readAs(TENANT_B, () -> billSplitRepository.findById(id))).isEmpty();
        assertThat(readAs(TENANT_A, () -> billSplitRepository.findById(id))).isPresent();
    }
}
