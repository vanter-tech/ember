package com.vanter.ember.catalog.repository;

import com.vanter.ember.catalog.model.MenuItemModifierGroup;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MenuItemModifierGroupRepository extends JpaRepository<MenuItemModifierGroup, Long> {

    List<MenuItemModifierGroup> findByMenuItemIdOrderByDisplayOrder(Long menuItemId);

    void deleteByMenuItemId(Long menuItemId);
}
