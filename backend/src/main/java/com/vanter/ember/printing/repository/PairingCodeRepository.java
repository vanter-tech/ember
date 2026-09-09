package com.vanter.ember.printing.repository;

import com.vanter.ember.printing.model.PairingCode;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PairingCodeRepository extends JpaRepository<PairingCode, String> {

    Optional<PairingCode> findByCode(String code);
}
