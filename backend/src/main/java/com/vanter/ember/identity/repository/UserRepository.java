package com.vanter.ember.identity.repository;

import com.vanter.ember.identity.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, String> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);

    /** Untenanted by design, like {@link #findByEmail} — {@code User} carries no {@code @TenantId}. */
    List<User> findByRestaurantId_IdAndRole(UUID restaurantId, Role role);

    /** Non-CUSTOMER users for a tenant — the Staff Management roster. */
    List<User> findByRestaurantId_IdAndRoleNot(UUID restaurantId, Role role);
}
