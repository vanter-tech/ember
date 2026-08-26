package com.vanter.ember.hub.license;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * (De)serializes and RSA-signs {@code license.key}: {@code base64(payloadJson).base64(signature)},
 * signature algorithm SHA256withRSA over the raw payload bytes.
 */
public class LicenseKeyParser {

    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    public LicenseKey parseAndVerify(String licenseKeyContents, PublicKey publicKey)
            throws InvalidLicenseException {
        String[] parts = licenseKeyContents.strip().split("\\.");
        if (parts.length != 2) {
            throw new InvalidLicenseException("El formato de license.key no es válido.");
        }

        byte[] payloadBytes;
        byte[] signatureBytes;
        try {
            payloadBytes = Base64.getDecoder().decode(parts[0]);
            signatureBytes = Base64.getDecoder().decode(parts[1]);
        } catch (IllegalArgumentException e) {
            throw new InvalidLicenseException("El formato de license.key no es válido.", e);
        }

        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initVerify(publicKey);
            signature.update(payloadBytes);
            if (!signature.verify(signatureBytes)) {
                throw new InvalidLicenseException("La firma de license.key no es válida.");
            }
        } catch (GeneralSecurityException e) {
            throw new InvalidLicenseException("No se pudo verificar la firma de license.key.", e);
        }

        try {
            LicensePayload payload = MAPPER.readValue(payloadBytes, LicensePayload.class);
            return new LicenseKey(payload.restaurantId(), payload.issuedAt());
        } catch (IOException e) {
            throw new InvalidLicenseException("El contenido de license.key no es válido.", e);
        }
    }

    /** Admin-side helper: produces the `license.key` file contents for a given restaurant. */
    public static String sign(LicenseKey key, PrivateKey privateKey) throws GeneralSecurityException, IOException {
        byte[] payloadBytes = MAPPER.writeValueAsBytes(new LicensePayload(key.restaurantId(), key.issuedAt()));
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(privateKey);
        signature.update(payloadBytes);
        byte[] signatureBytes = signature.sign();
        return Base64.getEncoder().encodeToString(payloadBytes)
                + "." + Base64.getEncoder().encodeToString(signatureBytes);
    }

    public static PublicKey loadPublicKey(Path publicKeyFile) throws InvalidLicenseException {
        try {
            byte[] keyBytes = Files.readAllBytes(publicKeyFile);
            X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
            return KeyFactory.getInstance("RSA").generatePublic(spec);
        } catch (IOException | GeneralSecurityException e) {
            throw new InvalidLicenseException(
                    "No se pudo leer la clave pública en " + publicKeyFile + ".", e);
        }
    }

    private record LicensePayload(UUID restaurantId, Instant issuedAt) {}
}
