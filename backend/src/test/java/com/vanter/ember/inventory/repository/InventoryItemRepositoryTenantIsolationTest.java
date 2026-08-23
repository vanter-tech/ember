package com.vanter.ember.inventory.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.vanter.ember.config.AbstractTenantIsolationTest;
import com.vanter.ember.inventory.model.InventoryItem;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class InventoryItemRepositoryTenantIsolationTest extends AbstractTenantIsolationTest {

    @Autowired InventoryItemRepository inventoryItemRepository;

    @Override
    protected void deleteAll() {
        inventoryItemRepository.deleteAll();
    }

    private InventoryItem itemFor(Long menuItemId) {
        return InventoryItem.builder()
                .menuItemId(menuItemId)
                .unit("kg")
                .currentStock(new BigDecimal("10.000"))
                .lowStockThreshold(new BigDecimal("2.000"))
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    void save_stampsTheBoundTenant() {
        InventoryItem saved = readAs(TENANT_A, () -> inventoryItemRepository.save(itemFor(1L)));

        assertThat(saved.getTenantId()).isEqualTo(TENANT_A);
    }

    @Test
    void findByMenuItemId_doesNotLeakAnotherTenantsRow() {
        asTenant(TENANT_A, () -> inventoryItemRepository.save(itemFor(1L)));

        assertThat(readAs(TENANT_B, () -> inventoryItemRepository.findByMenuItemId(1L))).isEmpty();
        assertThat(readAs(TENANT_A, () -> inventoryItemRepository.findByMenuItemId(1L))).isPresent();
    }

    @Test
    void applyClampedDelta_neverGoesNegative() {
        Long id = readAs(TENANT_A, () -> inventoryItemRepository.save(itemFor(2L)).getId());

        asTenant(TENANT_A, () -> inventoryItemRepository.applyClampedDelta(
                id, new BigDecimal("-999"), LocalDateTime.now()));

        InventoryItem reloaded = readAs(TENANT_A, () -> inventoryItemRepository.findById(id).orElseThrow());
        assertThat(reloaded.getCurrentStock()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void applyClampedDelta_isTenantScoped() {
        Long id = readAs(TENANT_A, () -> inventoryItemRepository.save(itemFor(3L)).getId());

        asTenant(TENANT_B, () -> inventoryItemRepository.applyClampedDelta(
                id, new BigDecimal("-5"), LocalDateTime.now()));

        InventoryItem reloaded = readAs(TENANT_A, () -> inventoryItemRepository.findById(id).orElseThrow());
        assertThat(reloaded.getCurrentStock()).isEqualByComparingTo(new BigDecimal("10.000"));
    }
}
