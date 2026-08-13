package com.vanter.ember.catalog.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.vanter.ember.catalog.model.Category;
import com.vanter.ember.config.AbstractTenantIsolationTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class CategoryRepositoryTenantIsolationTest extends AbstractTenantIsolationTest {

    @Autowired CategoryRepository categoryRepository;

    @Override
    protected void deleteAll() {
        categoryRepository.deleteAll();
    }

    private Category category(String name) {
        return Category.builder().name(name).build();
    }

    @Test
    void save_stampsTheBoundTenant() {
        Category saved = readAs(TENANT_A, () -> categoryRepository.save(category("Burgers")));

        assertThat(saved.getTenantId()).isEqualTo(TENANT_A);
    }

    @Test
    void findAll_onlyReturnsTheBoundTenantsCategories() {
        asTenant(TENANT_A, () -> categoryRepository.save(category("Burgers")));

        assertThat(readAs(TENANT_B, () -> categoryRepository.findAll())).isEmpty();
        assertThat(readAs(TENANT_A, () -> categoryRepository.findAll())).hasSize(1);
    }

    @Test
    void findByName_doesNotResolveAnotherTenantsCategory() {
        asTenant(TENANT_A, () -> categoryRepository.save(category("Drinks")));

        assertThat(readAs(TENANT_B, () -> categoryRepository.findByName("Drinks"))).isEmpty();
        assertThat(readAs(TENANT_B, () -> categoryRepository.existsByName("Drinks"))).isFalse();
    }

    @Test
    void findById_doesNotLeakAnotherTenantsCategory() {
        Long id = readAs(TENANT_A, () -> categoryRepository.save(category("Sides")).getId());

        assertThat(readAs(TENANT_B, () -> categoryRepository.findById(id))).isEmpty();
        assertThat(readAs(TENANT_A, () -> categoryRepository.findById(id))).isPresent();
    }

    @Test
    void count_ignoresOtherTenantsRows() {
        asTenant(TENANT_A, () -> categoryRepository.save(category("Burgers")));
        asTenant(TENANT_A, () -> categoryRepository.save(category("Drinks")));
        asTenant(TENANT_B, () -> categoryRepository.save(category("Desserts")));

        assertThat(readAs(TENANT_A, () -> categoryRepository.count())).isEqualTo(2);
        assertThat(readAs(TENANT_B, () -> categoryRepository.count())).isEqualTo(1);
    }

    @Test
    void sameCategoryName_isAllowedInTwoTenants() {
        asTenant(TENANT_A, () -> categoryRepository.saveAndFlush(category("Pizzas")));
        asTenant(TENANT_B, () -> categoryRepository.saveAndFlush(category("Pizzas")));

        List<Category> tenantB = readAs(TENANT_B, () -> categoryRepository.findAll());
        assertThat(tenantB).hasSize(1);
        assertThat(tenantB.get(0).getTenantId()).isEqualTo(TENANT_B);
    }

    @Test
    void delete_cannotReachAnotherTenantsCategory() {
        asTenant(TENANT_A, () -> categoryRepository.save(category("Burgers")));

        asTenant(TENANT_B, () -> categoryRepository.deleteAll());

        assertThat(readAs(TENANT_A, () -> categoryRepository.findAll())).hasSize(1);
    }
}
