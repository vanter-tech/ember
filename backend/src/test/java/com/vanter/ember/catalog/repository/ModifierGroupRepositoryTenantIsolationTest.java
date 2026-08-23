package com.vanter.ember.catalog.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.vanter.ember.catalog.model.ModifierGroup;
import com.vanter.ember.catalog.model.SelectionType;
import com.vanter.ember.config.AbstractTenantIsolationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ModifierGroupRepositoryTenantIsolationTest extends AbstractTenantIsolationTest {

    @Autowired ModifierGroupRepository modifierGroupRepository;

    @Override
    protected void deleteAll() {
        modifierGroupRepository.deleteAll();
    }

    private ModifierGroup group(String name) {
        return ModifierGroup.builder()
                .name(name)
                .selectionType(SelectionType.SINGLE_REQUIRED)
                .minSelections(1)
                .maxSelections(1)
                .active(true)
                .build();
    }

    @Test
    void save_stampsTheBoundTenant() {
        ModifierGroup saved = readAs(TENANT_A, () -> modifierGroupRepository.save(group("Término de cocción")));

        assertThat(saved.getTenantId()).isEqualTo(TENANT_A);
    }

    @Test
    void findAll_onlyReturnsTheBoundTenantsGroups() {
        asTenant(TENANT_A, () -> modifierGroupRepository.save(group("Término de cocción")));

        assertThat(readAs(TENANT_B, () -> modifierGroupRepository.findAll())).isEmpty();
        assertThat(readAs(TENANT_A, () -> modifierGroupRepository.findAll())).hasSize(1);
    }

    @Test
    void findById_doesNotLeakAnotherTenantsGroup() {
        Long id = readAs(TENANT_A, () -> modifierGroupRepository.save(group("Extras")).getId());

        assertThat(readAs(TENANT_B, () -> modifierGroupRepository.findById(id))).isEmpty();
        assertThat(readAs(TENANT_A, () -> modifierGroupRepository.findById(id))).isPresent();
    }
}
