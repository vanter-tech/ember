package com.vanter.ember.hub.backup;

import com.vanter.ember.EmberApplication;

/** Dependency-free {@code MAJOR.MINOR.PATCH[.HOTFIX]} comparison (no semver lib in the codebase). */
public final class HubVersion {

    private HubVersion() {}

    /** The running jar's {@code Implementation-Version}; {@code null} outside a packaged jar. */
    public static String current() {
        return EmberApplication.class.getPackage().getImplementationVersion();
    }

    /** True only when both parse and {@code candidate} is strictly greater. Unknown never blocks. */
    public static boolean isNewer(String candidate, String current) {
        int[] a = parse(candidate);
        int[] b = parse(current);
        if (a == null || b == null) {
            return false;
        }
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            int x = i < a.length ? a[i] : 0;
            int y = i < b.length ? b[i] : 0;
            if (x != y) {
                return x > y;
            }
        }
        return false;
    }

    private static int[] parse(String version) {
        if (version == null || version.isBlank()) {
            return null;
        }
        String[] parts = version.trim().split("\\.");
        int[] out = new int[parts.length];
        try {
            for (int i = 0; i < parts.length; i++) {
                out[i] = Integer.parseInt(parts[i]);
            }
        } catch (NumberFormatException e) {
            return null;
        }
        return out;
    }
}
