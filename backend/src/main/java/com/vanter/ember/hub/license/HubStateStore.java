package com.vanter.ember.hub.license;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Persists {@link HubState} as JSON plus a {@code mac} field (HMAC-SHA256 keyed from the state's own
 * fingerprint). It raises the bar from "edit it in Notepad" to "read the code and forge the MAC" —
 * it is not a secret-proof seal, since any local key is extractable. Fail-closed: a state that
 * fails verification (or predates the MAC) loads with an expired heartbeat, so writes stay blocked
 * until a signed cloud heartbeat succeeds; it never loads as trusted.
 */
public class HubStateStore {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(SerializationFeature.INDENT_OUTPUT)
            // Instants are stored as seconds.nanos decimals; a double would lose the nanos and
            // break the MAC on a value that was never tampered with.
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);

    private static final String MAC_FIELD = "mac";
    private static final byte[] PEPPER = "ember-hub-state-v1".getBytes(StandardCharsets.UTF_8);

    private final Path stateFile;

    public HubStateStore(Path stateFile) {
        this.stateFile = stateFile;
    }

    public Optional<HubState> load() {
        if (!Files.exists(stateFile)) {
            return Optional.empty();
        }
        try {
            ObjectNode node = (ObjectNode) MAPPER.readTree(stateFile.toFile());
            String mac = node.has(MAC_FIELD) ? node.remove(MAC_FIELD).asText() : null;
            HubState state = MAPPER.treeToValue(node, HubState.class);
            if (mac == null) {
                // Pre-MAC file (legacy install, or the MAC was stripped): keep the identity, drop
                // the heartbeat so it has to be re-earned from the cloud.
                return Optional.of(state.withLastHeartbeatAt(Instant.EPOCH));
            }
            if (!macMatches(state, mac)) {
                return Optional.of(new HubState(state.hardwareFingerprint(), state.restaurantId(),
                        Instant.EPOCH, Instant.EPOCH, state.lastSeenAt(), state.migratedSince()));
            }
            return Optional.of(state);
        } catch (IOException | ClassCastException | NullPointerException e) {
            throw new IllegalStateException("No se pudo leer " + stateFile, e);
        }
    }

    public void save(HubState state) {
        try {
            Path parent = stateFile.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            ObjectNode node = MAPPER.valueToTree(state);
            node.put(MAC_FIELD, mac(state));
            MAPPER.writeValue(stateFile.toFile(), node);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo escribir " + stateFile, e);
        }
    }

    private static boolean macMatches(HubState state, String presented) {
        return MessageDigest.isEqual(
                mac(state).getBytes(StandardCharsets.UTF_8), presented.getBytes(StandardCharsets.UTF_8));
    }

    private static String mac(HubState state) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            sha.update(PEPPER);
            byte[] key = sha.digest(String.valueOf(state.hardwareFingerprint()).getBytes(StandardCharsets.UTF_8));
            Mac hmac = Mac.getInstance("HmacSHA256");
            hmac.init(new SecretKeySpec(key, "HmacSHA256"));
            return Base64.getEncoder().encodeToString(
                    hmac.doFinal(canonical(state).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 not available", e);
        }
    }

    /**
     * The exact text the MAC is computed over. {@code migratedSince} is appended ONLY when set:
     * every hub-state.json written before that field existed carries a MAC over the five original
     * fields, and it must keep verifying.
     */
    static String canonical(HubState state) {
        String canonical = String.join("|",
                String.valueOf(state.hardwareFingerprint()),
                String.valueOf(state.restaurantId()),
                field(state.lastHeartbeatAt()),
                field(state.suspendedSince()),
                field(state.lastSeenAt()));
        if (state.migratedSince() != null) {
            canonical += "|" + field(state.migratedSince());
        }
        return canonical;
    }

    private static String field(Instant value) {
        return value == null ? "" : value.toString();
    }
}
