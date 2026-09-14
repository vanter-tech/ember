package com.vanter.ember.settings.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vanter.ember.restaurant.exception.PlanLimitExceededException;
import com.vanter.ember.restaurant.model.RestaurantPlan;
import com.vanter.ember.restaurant.service.PlanGateService;
import com.vanter.ember.settings.model.RestaurantSettings;
import com.vanter.ember.settings.model.SettingsPayload;
import com.vanter.ember.settings.repository.DiningTableRepository;
import com.vanter.ember.settings.repository.SettingsRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SettingServiceTest {

    @Mock SettingsRepository settingsRepository;
    @Mock DiningTableRepository diningTableRepository;
    @Mock PlanGateService planGateService;
    @InjectMocks SettingService settingService;

    private static final UUID TENANT_ID = UUID.randomUUID();

    private SettingsPayload payloadWithTables(int totalTables) {
        SettingsPayload payload = new SettingsPayload();
        payload.getSpace().setTotalTables(totalTables);
        return payload;
    }

    @Test
    void updateSettings_blockedWhenRequestedTablesExceedThePlanLimit() {
        SettingsPayload payload = payloadWithTables(5);
        doThrow(new PlanLimitExceededException("tables", RestaurantPlan.FREE, 1))
                .when(planGateService).requireTableCapacity(TENANT_ID, 5);

        assertThatThrownBy(() -> settingService.updateSettings(TENANT_ID, payload))
                .isInstanceOf(PlanLimitExceededException.class);

        verify(settingsRepository, never()).save(any());
    }

    @Test
    void updateSettings_allowedWhenRequestedTablesAreWithinThePlanLimit() {
        SettingsPayload payload = payloadWithTables(1);
        RestaurantSettings current = new RestaurantSettings();
        current.setRestaurantId(TENANT_ID);
        current.setPayload(new SettingsPayload());
        when(settingsRepository.findByRestaurantId(TENANT_ID)).thenReturn(Optional.of(current));
        when(diningTableRepository.countByRestaurantIdAndIsActiveTrue(TENANT_ID)).thenReturn(0L);

        settingService.updateSettings(TENANT_ID, payload);

        verify(settingsRepository).save(current);
    }

    @Test
    void updateSettings_blockedWhenBrandingChangedAndPlanBelowStarter() {
        SettingsPayload newPayload = payloadWithTables(1);
        newPayload.getBranding().setBusinessName("New Name");
        RestaurantSettings current = new RestaurantSettings();
        current.setRestaurantId(TENANT_ID);
        current.setPayload(new SettingsPayload());
        when(settingsRepository.findByRestaurantId(TENANT_ID)).thenReturn(Optional.of(current));
        doThrow(new PlanLimitExceededException("branding", RestaurantPlan.STARTER, RestaurantPlan.FREE))
                .when(planGateService).requirePlanAtLeast(TENANT_ID, RestaurantPlan.STARTER, "branding");

        assertThatThrownBy(() -> settingService.updateSettings(TENANT_ID, newPayload))
                .isInstanceOf(PlanLimitExceededException.class);

        verify(settingsRepository, never()).save(any());
    }

    @Test
    void updateSettings_allowedWhenBrandingUnchanged() {
        SettingsPayload newPayload = payloadWithTables(1);
        RestaurantSettings current = new RestaurantSettings();
        current.setRestaurantId(TENANT_ID);
        current.setPayload(payloadWithTables(1));
        when(settingsRepository.findByRestaurantId(TENANT_ID)).thenReturn(Optional.of(current));
        when(diningTableRepository.countByRestaurantIdAndIsActiveTrue(TENANT_ID)).thenReturn(1L);

        settingService.updateSettings(TENANT_ID, newPayload);

        verify(planGateService, never()).requirePlanAtLeast(TENANT_ID, RestaurantPlan.STARTER, "branding");
        verify(settingsRepository).save(current);
    }
}
