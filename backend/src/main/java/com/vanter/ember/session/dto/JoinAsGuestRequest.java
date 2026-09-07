package com.vanter.ember.session.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;

/** Body for {@code POST /sessions/join-as-guest} — exactly one of {@code joinCode} / {@code qrToken}. */
public record JoinAsGuestRequest(String joinCode, String qrToken, @Size(max = 50) String name) {

    public boolean hasQrToken() {
        return qrToken != null && !qrToken.isBlank();
    }

    public boolean hasJoinCode() {
        return joinCode != null && !joinCode.isBlank();
    }

    /** {@code @Valid} → 400 (not the 409 an {@code IllegalArgumentException} would give). */
    @JsonIgnore
    @AssertTrue(message = "Provide exactly one of joinCode or qrToken")
    public boolean isExactlyOneIdentifier() {
        return hasJoinCode() ^ hasQrToken();
    }
}
