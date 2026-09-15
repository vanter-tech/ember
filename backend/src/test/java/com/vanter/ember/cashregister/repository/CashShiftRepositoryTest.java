package com.vanter.ember.cashregister.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.vanter.ember.cashregister.model.CashShift;
import com.vanter.ember.cashregister.model.CashShiftStatus;
import com.vanter.ember.cashregister.model.DenominationCount;
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

/**
 * {@code @Transactional(NOT_SUPPORTED)} — same reasoning as {@code KitchenOrderRepositoryTest}:
 * {@link CashShift} carries {@code @TenantId}, and disabling the shared transaction means each
 * repository call is its own transaction/session, so a {@code save()} followed by a
 * {@code findById()} is a genuine round-trip through the database, not a Hibernate session-cache
 * hit — exactly what's needed to prove the JSON breakdown columns actually persist and reload.
 */
@DataJpaTest
@Import(TenantIdentifierResolver.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CashShiftRepositoryTest {

    private static final UUID TENANT_ID = UUID.randomUUID();

    @Autowired CashShiftRepository cashShiftRepository;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(TENANT_ID);
        cashShiftRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void save_roundTripsTheDenominationBreakdownAndCloseNotesThroughJson() {
        CashShift shift = CashShift.builder()
                .shiftNumber(1).status(CashShiftStatus.OPEN)
                .openingFloat(new BigDecimal("100.00")).openedBy("user-1")
                .openedAt(LocalDateTime.now())
                .openingBreakdown(List.of(new DenominationCount("bill_100", 1)))
                .build();
        CashShift saved = cashShiftRepository.save(shift);

        CashShift reloaded = cashShiftRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getOpeningBreakdown()).containsExactly(new DenominationCount("bill_100", 1));
        assertThat(reloaded.getClosingBreakdown()).isNull();
        assertThat(reloaded.getCloseNotes()).isNull();

        reloaded.setStatus(CashShiftStatus.CLOSED);
        reloaded.setClosingBreakdown(List.of(new DenominationCount("bill_50", 2), new DenominationCount("coin_10", 1)));
        reloaded.setCloseNotes("Faltaron C$10, probablemente una propina no registrada.");
        cashShiftRepository.save(reloaded);

        CashShift closed = cashShiftRepository.findById(saved.getId()).orElseThrow();
        assertThat(closed.getClosingBreakdown()).containsExactly(
                new DenominationCount("bill_50", 2), new DenominationCount("coin_10", 1));
        assertThat(closed.getCloseNotes()).isEqualTo("Faltaron C$10, probablemente una propina no registrada.");
    }
}
