package com.vanter.ember.settings.repository;

import com.vanter.ember.settings.model.DiningTables;
import com.vanter.ember.settings.model.RestaurantSettings;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SettingsRepository extends JpaRepository<RestaurantSettings, UUID> {
    Optional<RestaurantSettings> findByRestaurantId(UUID restaurantId);
}