package com.vanter.ember.cashregister.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class CashShiftDeadlineFieldsTest {

    @Test
    void effectiveDeadline_prefersProlongedUntilWhenPresent() {
        LocalDateTime expiresAt = LocalDateTime.of(2026, 8, 31, 1, 0);
        CashShift shift = CashShift.builder().expiresAt(expiresAt).build();
        assertThat(shift.effectiveDeadline()).isEqualTo(expiresAt);

        shift.setProlongedUntil(expiresAt.plusHours(1));
        assertThat(shift.effectiveDeadline()).isEqualTo(expiresAt.plusHours(1));
    }

    @Test
    void businessDay_isOpenedAtDate() {
        CashShift shift = CashShift.builder()
                .openedAt(LocalDateTime.of(2026, 8, 31, 23, 30)).build();
        assertThat(shift.businessDay()).isEqualTo(LocalDate.of(2026, 8, 31));
    }

    @Test
    void prolongCount_defaultsToZero() {
        assertThat(CashShift.builder().build().getProlongCount()).isZero();
    }
}
