package com.vanter.ember.cashregister.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.vanter.ember.cashregister.model.CashDrawerEvent;
import com.vanter.ember.cashregister.model.CashDrawerEventStatus;
import com.vanter.ember.cashregister.model.CashDrawerEventType;
import com.vanter.ember.config.TenantContextHolder;
import com.vanter.ember.config.TenantIdentifierResolver;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** NOT_SUPPORTED for the same reason as {@code CashShiftRepositoryTest}: real round-trips with {@code @TenantId}. */
@DataJpaTest
@Import(TenantIdentifierResolver.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CashDrawerEventRepositoryTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID OTHER_TENANT = UUID.randomUUID();

    @Autowired CashDrawerEventRepository repository;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(TENANT);
        repository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    private CashDrawerEvent event(CashDrawerEventStatus status, Long shiftId, LocalDateTime createdAt) {
        return CashDrawerEvent.builder().id(UUID.randomUUID()).type(CashDrawerEventType.CASH_SALE)
                .status(status).cashShiftId(shiftId).amount(new BigDecimal("10.00")).createdAt(createdAt).build();
    }

    @Test
    void findForPanel_returnsAllPendingPlusTheShiftsEvents_newestFirst() {
        repository.save(event(CashDrawerEventStatus.PENDING, 1L, LocalDateTime.now().minusMinutes(3)));   // old shift, still pending
        repository.save(event(CashDrawerEventStatus.RECEIVED, 2L, LocalDateTime.now().minusMinutes(2)));  // current shift
        repository.save(event(CashDrawerEventStatus.RECEIVED, 1L, LocalDateTime.now().minusMinutes(1)));  // old shift, done -> excluded

        List<CashDrawerEvent> result = repository.findForPanel(TENANT, 2L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getCashShiftId()).isEqualTo(2L);
    }

    @Test
    void findForPanel_withoutAnOpenShift_returnsOnlyPending() {
        repository.save(event(CashDrawerEventStatus.PENDING, 1L, LocalDateTime.now()));
        repository.save(event(CashDrawerEventStatus.RECEIVED, 1L, LocalDateTime.now()));

        assertThat(repository.findForPanel(TENANT, null)).hasSize(1);
    }

    @Test
    void findForPanel_neverLeaksAnotherTenantsEvents() {
        TenantContextHolder.setTenantId(OTHER_TENANT);
        repository.save(event(CashDrawerEventStatus.PENDING, 9L, LocalDateTime.now()));
        TenantContextHolder.setTenantId(TENANT);

        assertThat(repository.findForPanel(TENANT, 9L)).isEmpty();
    }
}
