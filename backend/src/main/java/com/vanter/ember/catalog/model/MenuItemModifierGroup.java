package com.vanter.ember.catalog.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "menu_item_modifier_groups",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_menu_item_modifier_groups",
                        columnNames = {"menu_item_id", "group_id"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MenuItemModifierGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "menu_item_id", nullable = false)
    private Long menuItemId;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;
}
