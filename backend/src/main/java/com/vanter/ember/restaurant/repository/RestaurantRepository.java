package com.vanter.ember.restaurant.repository;

import com.vanter.ember.restaurant.model.Restaurant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RestaurantRepository extends JpaRepository<Restaurant, UUID> {
    Optional<Restaurant> findBySlug(String slug);
    boolean existsBySlug(String slug);

    /**
     * Inserts a Restaurant with an explicit, caller-chosen id, bypassing Hibernate's
     * GenerationType.UUID generator — used ONLY by HubProvisioningRunner, which must reuse the
     * license's restaurantId exactly. save() cannot do this: with a non-null id already set,
     * Spring Data treats the entity as "not new" and calls merge() instead of persist(), which
     * throws (no existing row to merge into) — Hibernate's UuidGenerator does not implement
     * allowAssignedIdentifiers(), so a manually-assigned id is treated as belonging to a detached
     * entity, not a fresh one. A native insert is the only reliable way to force this specific id.
     */
    @Modifying
    @Query(value = "insert into restaurants (id, name, slug, plan, status, timezone, currency, created_at) "
            + "values (:id, :name, :slug, 'FREE', 'ACTIVE', 'UTC', 'USD', now())", nativeQuery = true)
    void insertWithId(@Param("id") UUID id, @Param("name") String name, @Param("slug") String slug);
}
