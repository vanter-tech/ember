package com.vanter.ember.cashregister.service;

import com.vanter.ember.cashregister.model.CashShift;
import com.vanter.ember.cashregister.model.CashShiftStatus;
import com.vanter.ember.settings.model.SettingsPayload.BusinessHoursSettings;
import com.vanter.ember.settings.model.SettingsPayload.BusinessHoursSettings.DaySchedule;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import org.springframework.stereotype.Service;

/**
 * Pure, dependency-free deadline math for a {@link CashShift}. The caller supplies {@code now}
 * and the tenant's {@link BusinessHoursSettings}; this class never reads a clock or a repository,
 * so every branch is unit-testable in isolation. A malformed or missing {@code closeTime} must
 * degrade to the 12h fallback, never throw — a bad settings value must not block payments.
 */
@Service
public class CashShiftDeadlineService {

    public static final Duration GRACE = Duration.ofHours(2);
    public static final Duration CLOSED_DAY_FALLBACK = Duration.ofHours(12);
    public static final Duration PROLONG_STEP = Duration.ofHours(1);

    public LocalDateTime computeExpiresAt(LocalDateTime openedAt, BusinessHoursSettings hours) {
        LocalTime closeTime = resolveCloseTime(openedAt, hours);
        if (closeTime == null) {
            return openedAt.plus(CLOSED_DAY_FALLBACK);
        }
        LocalDateTime candidate = openedAt.toLocalDate().atTime(closeTime);
        if (!candidate.isAfter(openedAt)) {
            candidate = candidate.plusDays(1);
        }
        return candidate.plus(GRACE);
    }

    public boolean isOverdue(CashShift shift, LocalDateTime now) {
        if (shift.getStatus() != CashShiftStatus.OPEN) {
            return false;
        }
        LocalDateTime deadline = shift.effectiveDeadline();
        return deadline != null && now.isAfter(deadline);
    }

    public LocalDateTime prolong(CashShift shift, LocalDateTime now) {
        LocalDateTime base = shift.effectiveDeadline();
        if (base == null || now.isAfter(base)) {
            base = now;
        }
        return base.plus(PROLONG_STEP);
    }

    private LocalTime resolveCloseTime(LocalDateTime openedAt, BusinessHoursSettings hours) {
        if (hours == null || hours.getSchedule() == null) {
            return null;
        }
        DaySchedule day = hours.getSchedule().stream()
                .filter(d -> d.getDay() == openedAt.getDayOfWeek())
                .findFirst()
                .orElse(null);
        if (day == null || day.isClosed() || day.getCloseTime() == null || day.getCloseTime().isBlank()) {
            return null;
        }
        try {
            return LocalTime.parse(day.getCloseTime().trim());
        } catch (DateTimeParseException ex) {
            return null;
        }
    }
}
