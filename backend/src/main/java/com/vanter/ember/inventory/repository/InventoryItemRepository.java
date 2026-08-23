package com.vanter.ember.inventory.repository;

import com.vanter.ember.inventory.model.InventoryItem;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface InventoryItemRepository extends JpaRepository<InventoryItem, Long> {

    Optional<InventoryItem> findByMenuItemId(Long menuItemId);

    @Modifying
    @Transactional
    @Query("UPDATE InventoryItem i SET "
            + "i.currentStock = CASE WHEN i.currentStock + :delta > 0 THEN i.currentStock + :delta ELSE 0 END, "
            + "i.updatedAt = :now "
            + "WHERE i.id = :id")
    void applyClampedDelta(@Param("id") Long id, @Param("delta") BigDecimal delta, @Param("now") LocalDateTime now);
}
