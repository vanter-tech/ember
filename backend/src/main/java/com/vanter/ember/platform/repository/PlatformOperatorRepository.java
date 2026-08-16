package com.vanter.ember.platform.repository;

import com.vanter.ember.platform.model.PlatformOperator;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformOperatorRepository extends JpaRepository<PlatformOperator, UUID> {
    Optional<PlatformOperator> findByEmail(String email);
}
