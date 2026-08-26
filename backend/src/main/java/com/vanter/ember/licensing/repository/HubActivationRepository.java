package com.vanter.ember.licensing.repository;

import com.vanter.ember.licensing.model.HubActivation;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HubActivationRepository extends JpaRepository<HubActivation, UUID> {
    Optional<HubActivation> findByRestaurantId(UUID restaurantId);
}
