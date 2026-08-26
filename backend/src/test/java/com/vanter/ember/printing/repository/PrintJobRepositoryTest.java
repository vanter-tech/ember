package com.vanter.ember.printing.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.vanter.ember.config.TenantContextHolder;
import com.vanter.ember.config.TenantIdentifierResolver;
import com.vanter.ember.printing.model.PrintJob;
import com.vanter.ember.printing.model.PrintJobSourceType;
import com.vanter.ember.printing.model.PrintJobStatus;
import com.vanter.ember.printing.model.PrinterRole;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Reproduces a real bug found during PRINT-07's manual physical printer verification
 * (2026-08-26): {@code PrintJob.tenantId} is a Hibernate {@code @TenantId} field, generated
 * in-memory only when the entity's INSERT actually executes — at flush time, not at {@code
 * persist()}/{@code save()} time. {@code PrintingEventListener.createAndDispatch} used to call
 * plain {@code save(job)} then immediately read {@code job.getTenantId()} inside {@code
 * PrintDispatchService.dispatch} to look up the tenant's printers — with no flush in between,
 * that read saw {@code null}, so the very first (synchronous) dispatch attempt for every kitchen
 * ticket always found zero printers and stayed {@code PENDING}, even with an active, correctly
 * configured, currently-connected printer. Switching to {@code saveAndFlush(job)} fixes it.
 *
 * <p>{@code PrintingEventListenerTest} mocks {@code PrintJobRepository} entirely, so it can't
 * observe this — a mock's {@code save()} just returns its argument unmodified regardless of
 * flush semantics. Only a real Hibernate session, as here, exhibits the timing gap.
 */
@DataJpaTest
@Import(TenantIdentifierResolver.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PrintJobRepositoryTest {

    @Autowired PrintJobRepository printJobRepository;
    @Autowired PlatformTransactionManager transactionManager;

    private static final UUID TENANT_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

    @AfterEach
    void purgeCommittedRows() {
        TenantContextHolder.setTenantId(TENANT_ID);
        try {
            printJobRepository.deleteAll(printJobRepository.findAll());
        } finally {
            TenantContextHolder.clear();
        }
    }

    private PrintJob newJob() {
        return PrintJob.builder()
                .id(UUID.randomUUID())
                .role(PrinterRole.KITCHEN)
                .sourceType(PrintJobSourceType.KITCHEN_TICKET)
                .sourceId("session-1")
                .payload("Mesa 5\n- Hamburguesa\n")
                .status(PrintJobStatus.PENDING)
                .attempts(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    // Both tests below open ONE explicit transaction spanning save+read, via TransactionTemplate
    // — not @Transactional on the test method, which @DataJpaTest would otherwise roll back
    // automatically, and not the class-level NOT_SUPPORTED either, which would let
    // JpaRepository's own internal @Transactional on save()/saveAndFlush() open-and-commit its
    // OWN auto-flushing transaction. Production's PrintingEventListener.createAndDispatch runs
    // inside an ALREADY-OPEN surrounding transaction (the waiter's HTTP request), so save()
    // there JOINS it instead of committing on its own — that's the surrounding transaction this
    // TransactionTemplate call reproduces.

    @Test
    void save_withinAnOpenSurroundingTransaction_tenantIdNotYetVisibleInMemory() {
        TenantContextHolder.setTenantId(TENANT_ID);
        try {
            UUID tenantIdSeenBeforeCommit = new TransactionTemplate(transactionManager)
                    .execute(status -> printJobRepository.save(newJob()).getTenantId());

            assertThat(tenantIdSeenBeforeCommit).isNull();
        } finally {
            TenantContextHolder.clear();
        }
    }

    @Test
    void saveAndFlush_withinAnOpenSurroundingTransaction_tenantIdImmediatelyVisibleInMemory() {
        TenantContextHolder.setTenantId(TENANT_ID);
        try {
            UUID tenantIdSeenBeforeCommit = new TransactionTemplate(transactionManager)
                    .execute(status -> printJobRepository.saveAndFlush(newJob()).getTenantId());

            assertThat(tenantIdSeenBeforeCommit).isEqualTo(TENANT_ID);
        } finally {
            TenantContextHolder.clear();
        }
    }
}
