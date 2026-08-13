package com.vanter.ember.catalog.repository;

import com.vanter.ember.catalog.model.MenuItem;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MenuItemRepository extends JpaRepository<MenuItem, Long> {

    List<MenuItem> findByCategoryId(Long categoryId);

    Page<MenuItem> findByCategoryId(Long categoryId, Pageable pageable);

    List<MenuItem> findByAvailableTrue();

    Integer countByCategoryId(Long categoryId);
}
