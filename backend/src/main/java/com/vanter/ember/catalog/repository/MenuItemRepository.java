package com.vanter.ember.catalog.repository;

import com.vanter.ember.catalog.model.MenuItem;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MenuItemRepository extends JpaRepository<MenuItem, Long> {

    List<MenuItem> findByCategoryId(Long categoryId);

    Page<MenuItem> findByCategoryId(Long categoryId, Pageable pageable);

    List<MenuItem> findByAvailableTrue();

    Integer countByCategoryId(Long categoryId);

    /**
     * Resolves the catalogue rows behind a set of ordered line items, with the category joined in so
     * product analytics can group by it outside the session. The {@code tenantId} predicate is
     * redundant with the {@code @TenantId} filter and kept deliberately, like the analytics queries
     * in {@code BillRepository}: here a context slip would relabel one tenant's dishes with another's.
     */
    @Query(
            """
            select mi
            from MenuItem mi
            join fetch mi.category
            where mi.tenantId = :tenantId
              and mi.id in :ids
            """)
    List<MenuItem> findByTenantIdAndIdInWithCategory(
            @Param("tenantId") UUID tenantId, @Param("ids") Collection<Long> ids);
}
