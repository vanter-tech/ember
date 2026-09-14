package com.vanter.ember.settings.service;
import com.vanter.ember.restaurant.model.RestaurantPlan;
import com.vanter.ember.restaurant.service.PlanGateService;
import com.vanter.ember.settings.model.DiningTables;
import com.vanter.ember.settings.model.RestaurantSettings;
import com.vanter.ember.settings.model.SettingsPayload;
import com.vanter.ember.settings.repository.DiningTableRepository;
import com.vanter.ember.settings.repository.SettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SettingService {

    private final SettingsRepository settingsRepository;
    private final DiningTableRepository diningTableRepository;
    private final PlanGateService planGateService;

    public RestaurantSettings getSettings(UUID restaurantId) {
        return settingsRepository.findByRestaurantId(restaurantId)
                .orElseGet(() -> creatorDefaultSettings(restaurantId));
    }

    private RestaurantSettings creatorDefaultSettings(UUID restaurantId) {
        RestaurantSettings defaultSettings = new RestaurantSettings();
        defaultSettings.setRestaurantId(restaurantId);

        SettingsPayload payload = new SettingsPayload();
        payload.getBilling().setCurrencySymbol("$");
        payload.getBilling().setTaxRate(0.0);
        payload.getHardware().setAutoPrintTickets(false);

        defaultSettings.setPayload(payload);

        return settingsRepository.save(defaultSettings);
    }

    @Transactional
    public void updateSettings(UUID restaurantId, SettingsPayload payload) {
        planGateService.requireTableCapacity(restaurantId, payload.getSpace().getTotalTables());

        RestaurantSettings currentSettings = getSettings(restaurantId);

        if (!payload.getBranding().equals(currentSettings.getPayload().getBranding())) {
            planGateService.requirePlanAtLeast(restaurantId, RestaurantPlan.STARTER, "branding");
        }

        currentSettings.setPayload(payload);
        settingsRepository.save(currentSettings);

        int requestedTables = payload.getSpace().getTotalTables();
        syncDiningTables(restaurantId, requestedTables);

    }

    private void syncDiningTables(UUID restaurantId, int requestedTables) {

        long currentActiveTables = diningTableRepository.countByRestaurantIdAndIsActiveTrue(restaurantId);
        int difference = requestedTables - (int) currentActiveTables;

        if (difference == 0) return;

        if(difference > 0) {
            Integer maxTableNum = diningTableRepository.findMaxTableNumberByRestaurantId(restaurantId);
            int startingNumber = (maxTableNum != null) ? maxTableNum + 1 : 1;

            for( int i = 0; i < difference; i++) {

                DiningTables newTable = DiningTables.builder()
                        .restaurantId(restaurantId)
                        .tableNumber(startingNumber + i)
                        .isActive(true)
                        .build();

                diningTableRepository.save(newTable);

            }
        }else {
            int tabletoRemove = Math.abs(difference);

            List<DiningTables> tablesToDeactive = diningTableRepository
                    .findByRestaurantIdAndIsActiveTrueOrderByTableNumberDesc(
                            restaurantId,
                            PageRequest.of(0, tabletoRemove)
                    );

            for(DiningTables table : tablesToDeactive) {
                table.setIsActive(false);
                diningTableRepository.save(table);
            }
        }

    }


}
