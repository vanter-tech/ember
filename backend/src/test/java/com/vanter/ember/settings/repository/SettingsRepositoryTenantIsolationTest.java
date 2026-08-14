package com.vanter.ember.settings.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.vanter.ember.config.AbstractTenantIsolationTest;
import com.vanter.ember.settings.model.RestaurantSettings;
import com.vanter.ember.settings.model.SettingsPayload;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class SettingsRepositoryTenantIsolationTest extends AbstractTenantIsolationTest {

    @Autowired SettingsRepository settingsRepository;

    @Override
    protected void deleteAll() {
        settingsRepository.deleteAll();
    }

    private RestaurantSettings settingsSavedFor(UUID tenantId, String businessName) {
        SettingsPayload payload = new SettingsPayload();
        payload.getBranding().setBusinessName(businessName);

        RestaurantSettings settings = new RestaurantSettings();
        settings.setPayload(payload);

        return readAs(tenantId, () -> settingsRepository.save(settings));
    }

    @Test
    void save_stampsTheBoundTenantAsRestaurantId() {
        RestaurantSettings saved = settingsSavedFor(TENANT_A, "Ember A");

        assertThat(saved.getRestaurantId()).isEqualTo(TENANT_A);
    }

    @Test
    void findByRestaurantId_ignoresAnotherTenantsExplicitId() {
        settingsSavedFor(TENANT_A, "Ember A");

        assertThat(readAs(TENANT_B, () -> settingsRepository.findByRestaurantId(TENANT_A))).isEmpty();
        assertThat(readAs(TENANT_A, () -> settingsRepository.findByRestaurantId(TENANT_A))).isPresent();
    }

    @Test
    void findAll_onlyReturnsTheBoundTenantsSettings() {
        settingsSavedFor(TENANT_A, "Ember A");
        settingsSavedFor(TENANT_B, "Ember B");

        assertThat(readAs(TENANT_B, () -> settingsRepository.findAll()))
                .singleElement()
                .satisfies(
                        settings ->
                                assertThat(settings.getPayload().getBranding().getBusinessName())
                                        .isEqualTo("Ember B"));
    }

    @Test
    void findById_doesNotLeakAnotherTenantsSettings() {
        UUID id = settingsSavedFor(TENANT_A, "Ember A").getId();

        assertThat(readAs(TENANT_B, () -> settingsRepository.findById(id))).isEmpty();
        assertThat(readAs(TENANT_A, () -> settingsRepository.findById(id))).isPresent();
    }
}
