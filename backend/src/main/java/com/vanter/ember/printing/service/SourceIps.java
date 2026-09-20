package com.vanter.ember.printing.service;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;

/**
 * Normalizes a client address so a browser and a print agent running on the same PC compare
 * equal. On the Hub's own PC the browser can arrive as {@code 127.0.0.1}, {@code ::1} or the PC's
 * own LAN IP while the agent connects through {@code localhost}, so every address that belongs to
 * this machine collapses to one key ({@link #LOCAL}). Any other address is kept, canonicalized
 * (IPv4-mapped IPv6 and IPv6 spellings included).
 */
public final class SourceIps {

    public static final String LOCAL = "local";

    private SourceIps() {
    }

    /** @return the comparison key, or {@code null} for a blank/unparseable value. */
    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            InetAddress address = InetAddress.getByName(raw.trim());
            if (address.isLoopbackAddress() || isThisMachine(address)) {
                return LOCAL;
            }
            return address.getHostAddress();
        } catch (UnknownHostException e) {
            return null;
        }
    }

    private static boolean isThisMachine(InetAddress address) {
        try {
            return NetworkInterface.getByInetAddress(address) != null;
        } catch (SocketException e) {
            return false;
        }
    }
}
