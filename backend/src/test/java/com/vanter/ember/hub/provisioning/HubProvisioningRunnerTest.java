package com.vanter.ember.hub.provisioning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.vanter.ember.hub.config.HubProperties;
import com.vanter.ember.hub.license.HardwareFingerprintService;
import com.vanter.ember.hub.license.HubState;
import com.vanter.ember.hub.license.HubStateStore;
import com.vanter.ember.identity.model.Role;
import com.vanter.ember.identity.model.User;
import com.vanter.ember.identity.repository.UserRepository;
import com.vanter.ember.restaurant.model.Restaurant;
import com.vanter.ember.restaurant.repository.RestaurantRepository;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

class HubProvisioningRunnerTest {

    @TempDir Path tempDir;

    private HubProperties properties;
    private HubStateStore stateStore;
    private HardwareFingerprintService fingerprintService;
    private RestaurantRepository restaurantRepository;
    private UserRepository userRepository;
    private PlatformTransactionManager transactionManager;
    private UUID restaurantId;
    private HttpServer stubServer;

    @AfterEach
    void tearDown() {
        if (stubServer != null) {
            stubServer.stop(0);
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        restaurantId = UUID.randomUUID();
        Path licenseFile = tempDir.resolve("license.key");
        Files.writeString(licenseFile, "irrelevant-for-this-test.signature");

        properties = new HubProperties(
                tempDir, tempDir, licenseFile, tempDir.resolve("pub.der"), tempDir.resolve("state.json"),
                5432, 8080, "http://localhost:9999/hub-activations");
        stateStore = new HubStateStore(properties.stateFile());
        stateStore.save(new HubState("fp-1", restaurantId, Instant.now()));

        fingerprintService = mock(HardwareFingerprintService.class);
        when(fingerprintService.currentFingerprint()).thenReturn("fp-1");
        restaurantRepository = mock(RestaurantRepository.class);
        userRepository = mock(UserRepository.class);
        transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
    }

    @Test
    void run_restaurantAlreadyExists_skipsActivationCall() throws Exception {
        when(restaurantRepository.existsById(restaurantId)).thenReturn(true);
        HubProvisioningRunner runner = new HubProvisioningRunner(
                properties, stateStore, fingerprintService, restaurantRepository, userRepository,
                transactionManager);

        runner.run(new DefaultApplicationArguments());

        verify(restaurantRepository, never()).insertWithId(any(), anyString(), anyString());
        verify(userRepository, never()).save(any());
    }

    @Test
    void run_blankActivationUrl_throwsProvisioningException() {
        HubProperties propertiesWithoutUrl = new HubProperties(
                tempDir, tempDir, properties.licenseFile(), tempDir.resolve("pub.der"), properties.stateFile(),
                5432, 8080, "");
        when(restaurantRepository.existsById(restaurantId)).thenReturn(false);
        HubProvisioningRunner runner = new HubProvisioningRunner(
                propertiesWithoutUrl, stateStore, fingerprintService, restaurantRepository, userRepository,
                transactionManager);

        assertThatThrownBy(() -> runner.run(new DefaultApplicationArguments()))
                .isInstanceOf(HubProvisioningException.class)
                .hasMessageContaining("EMBER_HUB_ACTIVATION_URL");
    }

    @Test
    void run_unreachableActivationUrl_throwsProvisioningException() {
        when(restaurantRepository.existsById(restaurantId)).thenReturn(false);
        HubProvisioningRunner runner = new HubProvisioningRunner(
                properties, stateStore, fingerprintService, restaurantRepository, userRepository,
                transactionManager);

        assertThatThrownBy(() -> runner.run(new DefaultApplicationArguments()))
                .isInstanceOf(HubProvisioningException.class)
                .hasMessageContaining("conexión a internet");
    }

    @Test
    void run_successfulActivation_savesRestaurantAndAdminUser() throws Exception {
        String responseJson = "{"
                + "\"name\":\"Tenant Grill\","
                + "\"slug\":\"tenant-grill\","
                + "\"adminName\":\"Owner Admin\","
                + "\"adminEmail\":\"owner@tenant-grill.local\","
                + "\"adminPasswordHash\":\"bcrypt-hash\""
                + "}";

        stubServer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        stubServer.createContext("/hub-activations", (HttpExchange exchange) -> {
            byte[] bytes = responseJson.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (var os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        stubServer.start();
        int port = stubServer.getAddress().getPort();

        HubProperties propertiesWithStubUrl = new HubProperties(
                tempDir, tempDir, properties.licenseFile(), tempDir.resolve("pub.der"), properties.stateFile(),
                5432, 8080, "http://localhost:" + port + "/hub-activations");
        when(restaurantRepository.existsById(restaurantId)).thenReturn(false);
        Restaurant insertedRestaurant = Restaurant.builder()
                .id(restaurantId)
                .name("Tenant Grill")
                .slug("tenant-grill")
                .build();
        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(insertedRestaurant));
        HubProvisioningRunner runner = new HubProvisioningRunner(
                propertiesWithStubUrl, stateStore, fingerprintService, restaurantRepository, userRepository,
                transactionManager);

        runner.run(new DefaultApplicationArguments());

        verify(restaurantRepository).insertWithId(eq(restaurantId), eq("Tenant Grill"), eq("tenant-grill"));

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getRestaurantId()).isSameAs(insertedRestaurant);
        assertThat(savedUser.getName()).isEqualTo("Owner Admin");
        assertThat(savedUser.getEmail()).isEqualTo("owner@tenant-grill.local");
        assertThat(savedUser.getPasswordHash()).isEqualTo("bcrypt-hash");
        assertThat(savedUser.getRole()).isEqualTo(Role.ADMIN);
    }
}
