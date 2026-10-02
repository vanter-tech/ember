package com.vanter.ember.numbering;

import java.util.Locale;

/** Derives the readable prefix of a tenant's document codes from its slug. */
public final class DocumentCodes {

    private static final int PREFIX_LENGTH = 4;
    private static final String FALLBACK_PREFIX = "EMBR";

    private DocumentCodes() {
    }

    /** First 4 letters/digits of the slug, upper-case, separators dropped; a shorter slug keeps what it has. */
    public static String prefixFromSlug(String slug) {
        String clean = slug == null ? "" : slug.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        if (clean.isEmpty()) {
            return FALLBACK_PREFIX;
        }
        return clean.substring(0, Math.min(PREFIX_LENGTH, clean.length()));
    }
}
