package com.vanter.ember.hub.license;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import oshi.SystemInfo;
import oshi.hardware.HardwareAbstractionLayer;

/**
 * Derives a stable per-machine fingerprint from CPU + motherboard serials via OSHI. Windows-only
 * for v1 (spec §2.2) — OSHI itself is cross-platform, but nothing here has been tried on
 * Mac/Linux and the rest of the Hub packaging route wouldn't run there anyway.
 */
public class HardwareFingerprintService {

    public String currentFingerprint() {
        SystemInfo systemInfo = new SystemInfo();
        HardwareAbstractionLayer hardware = systemInfo.getHardware();
        String cpuId = hardware.getProcessor().getProcessorIdentifier().getProcessorID();
        String boardSerial = hardware.getComputerSystem().getBaseboard().getSerialNumber();
        return sha256Hex(cpuId + "|" + boardSerial);
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
