package com.vanter.ember.billing.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.vanter.ember.billing.model.SplitMethod;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record RequestBillingRequest(@NotNull SplitMethod splitMethod, @Min(1) Integer participantCount) {

    /**
     * EQUAL_PARTS divides the bill total by participantCount, so it must be present and >= 1.
     * Without this a null count was coerced to 0 in the controller and reached splitEqually, which
     * threw ArithmeticException (500) *after* the bill row had already been committed — leaving an
     * unrecoverable orphan bill that then blocked every later "calculate bill" for the session.
     * BY_CONSUMPTION ignores the field.
     */
    @JsonIgnore
    @AssertTrue(message = "participantCount is required and must be >= 1 for EQUAL_PARTS")
    public boolean isParticipantCountValidForMethod() {
        return splitMethod != SplitMethod.EQUAL_PARTS
                || (participantCount != null && participantCount >= 1);
    }
}
