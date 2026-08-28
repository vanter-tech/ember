package com.vanter.ember.cashregister.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.vanter.ember.cashregister.model.CashShift;
import com.vanter.ember.cashregister.model.CashShiftStatus;
import com.vanter.ember.settings.model.SettingsPayload.BusinessHoursSettings;
import com.vanter.ember.settings.model.SettingsPayload.BusinessHoursSettings.DaySchedule;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class CashShiftDeadlineServiceTest {

    private final CashShiftDeadlineService service = new CashShiftDeadlineService();

    private BusinessHoursSettings hoursWith(DayOfWeek day, boolean closed, String closeTime) {
        DaySchedule d = new DaySchedule();
        d.setDay(day);
        d.setClosed(closed);
        d.setOpenTime("09:00");
        d.setCloseTime(closeTime);
        BusinessHoursSettings h = new BusinessHoursSettings();
        h.setSchedule(List.of(d));
        return h;
    }

    private CashShift shiftExpiring(LocalDateTime expiresAt, LocalDateTime prolongedUntil) {
        return CashShift.builder()
                .id(1L).status(CashShiftStatus.OPEN)
                .openedAt(expiresAt.minusHours(5))
                .expiresAt(expiresAt).prolongedUntil(prolongedUntil)
                .build();
    }

    @Test
    void computeExpiresAt_openDuringTrading_isCloseTimePlusTwoHours() {
        // Monday, close 23:00, opened 18:00 -> candidate 23:00 same day -> +2h -> 01:00 Tuesday
        LocalDateTime openedAt = LocalDateTime.of(2026, 8, 31, 18, 0); // a Monday
        LocalDateTime expiresAt =
                service.computeExpiresAt(openedAt, hoursWith(DayOfWeek.MONDAY, false, "23:00"));
        assertThat(expiresAt).isEqualTo(LocalDateTime.of(2026, 9, 1, 1, 0));
    }

    @Test
    void computeExpiresAt_openedAfterCloseTime_rollsToNextDay() {
        LocalDateTime openedAt = LocalDateTime.of(2026, 8, 31, 23, 30);
        LocalDateTime expiresAt =
                service.computeExpiresAt(openedAt, hoursWith(DayOfWeek.MONDAY, false, "23:00"));
        assertThat(expiresAt).isEqualTo(LocalDateTime.of(2026, 9, 2, 1, 0));
    }

    @Test
    void computeExpiresAt_overnightCloseTime_rollsForwardThenAddsGrace() {
        // close 02:00, opened 20:00 -> candidate 02:00 same day is before openedAt -> +1 day -> 02:00 next -> +2h
        LocalDateTime openedAt = LocalDateTime.of(2026, 8, 31, 20, 0);
        LocalDateTime expiresAt =
                service.computeExpiresAt(openedAt, hoursWith(DayOfWeek.MONDAY, false, "02:00"));
        assertThat(expiresAt).isEqualTo(LocalDateTime.of(2026, 9, 1, 4, 0));
    }

    @Test
    void computeExpiresAt_dayClosed_fallsBackToTwelveHours() {
        LocalDateTime openedAt = LocalDateTime.of(2026, 8, 31, 20, 0);
        LocalDateTime expiresAt =
                service.computeExpiresAt(openedAt, hoursWith(DayOfWeek.MONDAY, true, "23:00"));
        assertThat(expiresAt).isEqualTo(openedAt.plusHours(12));
    }

    @Test
    void computeExpiresAt_noScheduleEntry_fallsBackToTwelveHours() {
        LocalDateTime openedAt = LocalDateTime.of(2026, 8, 31, 20, 0);
        LocalDateTime expiresAt =
                service.computeExpiresAt(openedAt, hoursWith(DayOfWeek.SUNDAY, false, "23:00"));
        assertThat(expiresAt).isEqualTo(openedAt.plusHours(12));
    }

    @Test
    void computeExpiresAt_malformedCloseTime_fallsBackAndDoesNotThrow() {
        LocalDateTime openedAt = LocalDateTime.of(2026, 8, 31, 20, 0);
        assertThat(service.computeExpiresAt(openedAt, hoursWith(DayOfWeek.MONDAY, false, "25:99")))
                .isEqualTo(openedAt.plusHours(12));
        assertThat(service.computeExpiresAt(openedAt, hoursWith(DayOfWeek.MONDAY, false, "")))
                .isEqualTo(openedAt.plusHours(12));
        assertThat(service.computeExpiresAt(openedAt, hoursWith(DayOfWeek.MONDAY, false, null)))
                .isEqualTo(openedAt.plusHours(12));
    }

    @Test
    void isOverdue_trueOnlyWhenOpenAndPastEffectiveDeadline() {
        LocalDateTime deadline = LocalDateTime.of(2026, 8, 31, 1, 0);
        CashShift shift = shiftExpiring(deadline, null);
        assertThat(service.isOverdue(shift, deadline.minusMinutes(1))).isFalse();
        assertThat(service.isOverdue(shift, deadline.plusMinutes(1))).isTrue();

        CashShift prolonged = shiftExpiring(deadline, deadline.plusHours(1));
        assertThat(service.isOverdue(prolonged, deadline.plusMinutes(1))).isFalse();

        CashShift closed = shiftExpiring(deadline, null);
        closed.setStatus(CashShiftStatus.CLOSED);
        assertThat(service.isOverdue(closed, deadline.plusHours(9))).isFalse();
    }

    @Test
    void prolong_fromBeforeDeadline_addsOneHourToDeadline() {
        LocalDateTime deadline = LocalDateTime.of(2026, 8, 31, 1, 0);
        CashShift shift = shiftExpiring(deadline, null);
        assertThat(service.prolong(shift, deadline.minusMinutes(30)))
                .isEqualTo(deadline.plusHours(1));
    }

    @Test
    void prolong_fromAfterDeadline_addsOneHourToNow() {
        LocalDateTime deadline = LocalDateTime.of(2026, 8, 31, 1, 0);
        CashShift shift = shiftExpiring(deadline, null);
        LocalDateTime now = deadline.plusHours(3);
        assertThat(service.prolong(shift, now)).isEqualTo(now.plusHours(1));
    }
}
