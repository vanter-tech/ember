package com.vanter.ember.settings.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.vanter.ember.config.AbstractTenantIsolationTest;
import com.vanter.ember.settings.model.DiningTables;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class DiningTableRepositoryTenantIsolationTest extends AbstractTenantIsolationTest {

    @Autowired DiningTableRepository diningTableRepository;

    @Override
    protected void deleteAll() {
        diningTableRepository.deleteAll();
    }

    private DiningTables tableSavedFor(UUID tenantId, int tableNumber) {
        return readAs(
                tenantId,
                () ->
                        diningTableRepository.save(
                                DiningTables.builder().tableNumber(tableNumber).isActive(true).build()));
    }

    @Test
    void save_stampsTheBoundTenantAsRestaurantId() {
        DiningTables saved = tableSavedFor(TENANT_A, 1);

        assertThat(saved.getRestaurantId()).isEqualTo(TENANT_A);
    }

    @Test
    void countByRestaurantId_ignoresAnotherTenantsExplicitId() {
        tableSavedFor(TENANT_A, 1);
        tableSavedFor(TENANT_A, 2);

        assertThat(readAs(TENANT_B, () -> diningTableRepository.countByRestaurantIdAndIsActiveTrue(TENANT_A)))
                .isZero();
        assertThat(readAs(TENANT_A, () -> diningTableRepository.countByRestaurantIdAndIsActiveTrue(TENANT_A)))
                .isEqualTo(2);
    }

    @Test
    void findByRestaurantId_doesNotReturnAnotherTenantsTables() {
        tableSavedFor(TENANT_A, 1);
        tableSavedFor(TENANT_B, 7);

        assertThat(
                        readAs(
                                TENANT_B,
                                () ->
                                        diningTableRepository
                                                .findByRestaurantIdAndIsActiveTrueOrderByTableNumberAsc(TENANT_A)))
                .isEmpty();
        assertThat(
                        readAs(
                                TENANT_B,
                                () ->
                                        diningTableRepository
                                                .findByRestaurantIdAndIsActiveTrueOrderByTableNumberAsc(TENANT_B)))
                .singleElement()
                .satisfies(table -> assertThat(table.getTableNumber()).isEqualTo(7));
    }

    @Test
    void findMaxTableNumber_doesNotSeeAnotherTenantsNumbering() {
        tableSavedFor(TENANT_A, 12);
        tableSavedFor(TENANT_B, 3);

        assertThat(readAs(TENANT_B, () -> diningTableRepository.findMaxTableNumberByRestaurantId(TENANT_A)))
                .isNull();
        assertThat(readAs(TENANT_B, () -> diningTableRepository.findMaxTableNumberByRestaurantId(TENANT_B)))
                .isEqualTo(3);
    }

    @Test
    void findById_doesNotLeakAnotherTenantsTable() {
        UUID id = tableSavedFor(TENANT_A, 1).getId();

        assertThat(readAs(TENANT_B, () -> diningTableRepository.findById(id))).isEmpty();
        assertThat(readAs(TENANT_A, () -> diningTableRepository.findById(id))).isPresent();
    }

    @Test
    void findAll_onlyReturnsTheBoundTenantsTables() {
        tableSavedFor(TENANT_A, 1);
        tableSavedFor(TENANT_B, 1);

        assertThat(readAs(TENANT_A, () -> diningTableRepository.findAll())).hasSize(1);
        assertThat(readAs(TENANT_B, () -> diningTableRepository.findAll())).hasSize(1);
    }
}
