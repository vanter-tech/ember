package com.vanter.ember.hub.provisioning;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vanter.ember.hub.config.HubProperties;
import com.vanter.ember.hub.license.HardwareFingerprintService;
import com.vanter.ember.hub.license.HubState;
import com.vanter.ember.hub.license.HubStateStore;
import com.vanter.ember.identity.model.Role;
import com.vanter.ember.identity.model.User;
import com.vanter.ember.identity.repository.UserRepository;
import com.vanter.ember.restaurant.model.Restaurant;
import com.vanter.ember.restaurant.repository.RestaurantRepository;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.time.Duration;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Runs once per boot, after Spring's own context is fully up (but before {@code
 * ApplicationReadyEvent} — see {@code HubDashboard}/{@code HubTrayIcon}, neither shows "ready"
 * until this finishes) — see spec `docs/superpowers/specs/2026-08-25-hub-license-activation-design.md`
 * §5.1. First boot ever: calls {@code POST /hub-activations} once and seeds {@code Restaurant} +
 * the admin {@code User} locally, reusing the SAME id as the license's {@code restaurantId}
 * (Hibernate's {@code GenerationType.UUID} respects a pre-assigned id — confirmed during design).
 * Every later boot: a no-op, zero network calls, since the restaurant already exists locally.
 */
@Component
@Profile("hub")
public class HubProvisioningRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(HubProvisioningRunner.class);
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final HubProperties properties;
    private final HubStateStore stateStore;
    private final HardwareFingerprintService fingerprintService;
    private final RestaurantRepository restaurantRepository;
    private final UserRepository userRepository;
    private final TransactionTemplate transactionTemplate;

    public HubProvisioningRunner(
            HubProperties properties,
            HubStateStore stateStore,
            HardwareFingerprintService fingerprintService,
            RestaurantRepository restaurantRepository,
            UserRepository userRepository,
            PlatformTransactionManager transactionManager) {
        this.properties = properties;
        this.stateStore = stateStore;
        this.fingerprintService = fingerprintService;
        this.restaurantRepository = restaurantRepository;
        this.userRepository = userRepository;
        // Programmatic transaction demarcation (TransactionTemplate), not @Transactional: run()
        // calls seedRestaurantAndAdmin() on `this`, and Spring AOP's @Transactional relies on a
        // proxy that self-invocation bypasses entirely — an @Transactional annotation on a method
        // called via `this.method(...)` from elsewhere in the same class silently runs with no
        // transaction at all. TransactionTemplate demarcates explicitly, so it works regardless of
        // how the method is invoked.
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        HubState state = stateStore.load()
                .orElseThrow(() -> new IllegalStateException(
                        "hub-state.json missing after startup license validation"));

        if (restaurantRepository.existsById(state.restaurantId())) {
            log.info("Restaurant {} already provisioned locally, skipping activation call.",
                    state.restaurantId());
            return;
        }
        if (properties.activationUrl().isBlank()) {
            throw new HubProvisioningException(
                    "No se configuró EMBER_HUB_ACTIVATION_URL; no se puede activar por primera vez.");
        }

        ActivationResponseBody body = callActivationEndpoint(state);

        Restaurant restaurant = seedRestaurantAndAdmin(state.restaurantId(), body);

        log.info("Provisioned restaurant {} ({}) locally.", restaurant.getId(), restaurant.getSlug());
    }

    /**
     * Inserts the Restaurant (with the license's pre-assigned id, via a native insert — see
     * {@link RestaurantRepository#insertWithId}, since Hibernate's {@code GenerationType.UUID}
     * generator rejects a caller-supplied id) and the admin User atomically. If the User save
     * fails (e.g. duplicate email), the Restaurant insert rolls back too, so {@link #run} sees no
     * restaurant on the next boot and retries activation from scratch instead of getting stuck
     * behind an orphaned Restaurant that the {@code existsById} guard above would otherwise hide.
     */
    Restaurant seedRestaurantAndAdmin(UUID restaurantId, ActivationResponseBody body) {
        return transactionTemplate.execute(status -> {
            restaurantRepository.insertWithId(restaurantId, body.name(), body.slug());
            Restaurant restaurant = restaurantRepository.findById(restaurantId).orElseThrow(() ->
                    new IllegalStateException(
                            "Restaurant " + restaurantId + " not found immediately after insert"));

            userRepository.save(User.builder()
                    .restaurantId(restaurant)
                    .name(body.adminName())
                    .email(body.adminEmail())
                    .passwordHash(body.adminPasswordHash())
                    .role(Role.ADMIN)
                    .build());

            return restaurant;
        });
    }

    private ActivationResponseBody callActivationEndpoint(HubState state) throws IOException {
        String licenseKeyContents;
        try {
            licenseKeyContents = Files.readString(properties.licenseFile());
        } catch (IOException e) {
            throw new HubProvisioningException("No se pudo leer license.key para activar.", e);
        }
        String fingerprint = fingerprintService.currentFingerprint();

        String requestBody;
        try {
            requestBody = MAPPER.writeValueAsString(
                    new ActivationRequestBody(licenseKeyContents, fingerprint));
        } catch (IOException e) {
            throw new HubProvisioningException("No se pudo preparar la solicitud de activación.", e);
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(properties.activationUrl()))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(15))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        HttpResponse<String> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new HubProvisioningException(
                    "No se pudo conectar para activar Ember Hub. Verifica tu conexión a internet "
                            + "e intenta de nuevo.", e);
        }

        if (response.statusCode() != 200) {
            throw new HubProvisioningException(
                    "La activación falló (HTTP " + response.statusCode() + "): " + response.body());
        }

        try {
            return MAPPER.readValue(response.body(), ActivationResponseBody.class);
        } catch (IOException e) {
            log.error("Respuesta de activación con formato inválido", e);
            throw new HubProvisioningException("La respuesta de activación no es válida.", e);
        }
    }

    private record ActivationRequestBody(String licenseKey, String hardwareFingerprint) {}

    private record ActivationResponseBody(
            String name, String slug, String adminName, String adminEmail, String adminPasswordHash) {}
}
