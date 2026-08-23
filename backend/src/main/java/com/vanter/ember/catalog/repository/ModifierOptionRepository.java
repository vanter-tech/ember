package com.vanter.ember.catalog.repository;

import com.vanter.ember.catalog.model.ModifierOption;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ModifierOptionRepository extends JpaRepository<ModifierOption, Long> {

    List<ModifierOption> findByGroupIdOrderByDisplayOrder(Long groupId);
}
