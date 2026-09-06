package com.vanter.ember.identity.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * The set of built-in customer-home banner presets. The client renders each key as a
 * CSS gradient (see {@code frontend/src/lib/bannerPresets.ts}); the server only needs to
 * store and validate the choice. Persisted as {@link #name()} (JPA {@code EnumType.STRING});
 * exchanged over JSON in lower case.
 */
public enum BannerKey {
    EMBER,
    SUNSET,
    FOREST,
    OCEAN,
    MIDNIGHT,
    MONO;

    @JsonValue
    public String toJson() {
        return name().toLowerCase();
    }

    @JsonCreator
    public static BannerKey fromJson(String value) {
        if (value == null) {
            return null;
        }
        return BannerKey.valueOf(value.trim().toUpperCase());
    }
}
