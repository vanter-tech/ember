package com.vanter.ember.catalog.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.vanter.ember.catalog.model.Category;
import com.vanter.ember.catalog.model.MenuItem;
import com.vanter.ember.config.AbstractTenantIsolationTest;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

class MenuItemRepositoryTenantIsolationTest extends AbstractTenantIsolationTest {

    @Autowired MenuItemRepository menuItemRepository;
    @Autowired CategoryRepository categoryRepository;

    @Override
    protected void deleteAll() {
        menuItemRepository.deleteAll();
        categoryRepository.deleteAll();
    }

    private Category categoryOf(UUID tenantId) {
        return readAs(tenantId, () -> categoryRepository.save(Category.builder().name("Burgers").build()));
    }

    private MenuItem itemSavedFor(UUID tenantId, String name, boolean available) {
        Category category = categoryOf(tenantId);
        return readAs(
                tenantId,
                () ->
                        menuItemRepository.save(
                                MenuItem.builder()
                                        .name(name)
                                        .price(new BigDecimal("9.99"))
                                        .category(category)
                                        .available(available)
                                        .build()));
    }

    @Test
    void save_stampsTheBoundTenant() {
        MenuItem saved = itemSavedFor(TENANT_A, "Classic Burger", true);

        assertThat(saved.getTenantId()).isEqualTo(TENANT_A);
    }

    @Test
    void findAll_onlyReturnsTheBoundTenantsItems() {
        itemSavedFor(TENANT_A, "Classic Burger", true);

        assertThat(readAs(TENANT_B, () -> menuItemRepository.findAll())).isEmpty();
        assertThat(readAs(TENANT_A, () -> menuItemRepository.findAll())).hasSize(1);
    }

    @Test
    void findByCategoryId_doesNotReachAnotherTenantsItems() {
        MenuItem tenantAItem = itemSavedFor(TENANT_A, "Classic Burger", true);
        Long categoryId = tenantAItem.getCategory().getId();

        assertThat(readAs(TENANT_B, () -> menuItemRepository.findByCategoryId(categoryId))).isEmpty();
        assertThat(readAs(TENANT_B, () -> menuItemRepository.countByCategoryId(categoryId))).isZero();
        assertThat(readAs(TENANT_A, () -> menuItemRepository.findByCategoryId(categoryId))).hasSize(1);
    }

    @Test
    void findByCategoryId_paginated_doesNotReachAnotherTenantsItems() {
        MenuItem tenantAItem = itemSavedFor(TENANT_A, "Classic Burger", true);
        Long categoryId = tenantAItem.getCategory().getId();
        PageRequest pageable = PageRequest.of(0, 10);

        assertThat(readAs(TENANT_B, () -> menuItemRepository.findByCategoryId(categoryId, pageable))
                .getContent()).isEmpty();
        assertThat(readAs(TENANT_A, () -> menuItemRepository.findByCategoryId(categoryId, pageable))
                .getContent()).hasSize(1);
    }

    @Test
    void findByAvailableTrue_doesNotReachAnotherTenantsItems() {
        itemSavedFor(TENANT_A, "Classic Burger", true);
        itemSavedFor(TENANT_B, "Veggie Burger", true);

        assertThat(readAs(TENANT_B, () -> menuItemRepository.findByAvailableTrue()))
                .singleElement()
                .satisfies(item -> assertThat(item.getName()).isEqualTo("Veggie Burger"));
    }

    @Test
    void findById_doesNotLeakAnotherTenantsItem() {
        Long id = itemSavedFor(TENANT_A, "Classic Burger", true).getId();

        assertThat(readAs(TENANT_B, () -> menuItemRepository.findById(id))).isEmpty();
        assertThat(readAs(TENANT_A, () -> menuItemRepository.findById(id))).isPresent();
    }
}
