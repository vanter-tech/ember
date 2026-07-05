package com.vanter.ember.settings.repository;

import com.vanter.ember.settings.model.DiningTables;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface DiningTableRepository extends JpaRepository<DiningTables, UUID> {
    long countByRestaurantIdAndIsActiveTrue(UUID restaurantId);

    @Query("SELECT MAX(t.tableNumber) FROM DiningTables t WHERE t.restaurantId = :restaurantId" )
    Integer findMaxTableNumberByRestaurantId(@Param("restaurantId") UUID restaurantId);

    List<DiningTables> findByRestaurantIdAndIsActiveTrueOrderByTableNumberAsc(UUID restaurantId);

    List<DiningTables> findByRestaurantIdAndIsActiveTrueOrderByTableNumberDesc(UUID restaurantId, Pageable pageable);


}
