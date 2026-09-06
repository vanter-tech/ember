package com.vanter.ember.licensing.repository;

import com.vanter.ember.licensing.model.HubActivation;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface HubActivationRepository extends JpaRepository<HubActivation, UUID> {
    Optional<HubActivation> findByRestaurantId(UUID restaurantId);

    List<HubActivation> findByRestaurantIdIn(Collection<UUID> restaurantIds);

    /**
     * Best-effort liveness stamp written on every verified heartbeat. A targeted UPDATE (not an
     * entity save) so it neither reloads the row nor bumps a version. Returns rows affected
     * (0 if the restaurant has no activation row — a caller can ignore that).
     * {@code clearAutomatically} so any {@code HubActivation} already managed in the same
     * persistence context is not left stale after this bulk update.
     */
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("update HubActivation h set h.lastHeartbeatAt = :at, h.lastHeartbeatIp = :ip "
            + "where h.restaurantId = :restaurantId")
    int recordHeartbeat(@Param("restaurantId") UUID restaurantId,
                        @Param("at") Instant at,
                        @Param("ip") String ip);
}
