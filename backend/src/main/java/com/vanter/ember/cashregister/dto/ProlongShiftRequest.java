package com.vanter.ember.cashregister.dto;

import com.vanter.ember.cashregister.service.CashShiftDeadlineService;
import jakarta.validation.constraints.AssertTrue;

/**
 * How long to extend an open shift. The body is optional: without it (or with a null {@code
 * minutes}) the shift is extended by the default hour, which is what the periodic reminder modal
 * and clients that predate this field send. Only the fixed list of durations is accepted, so a
 * manual call cannot push a deadline out by days.
 */
public record ProlongShiftRequest(Integer minutes) {

    @AssertTrue(message = "minutes must be one of 30, 60, 120, 180 or 240")
    public boolean isMinutesAllowed() {
        return minutes == null || CashShiftDeadlineService.ALLOWED_PROLONG_MINUTES.contains(minutes);
    }
}
