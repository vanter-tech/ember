package com.vanter.ember.catalog.model.dto;

import com.vanter.ember.catalog.model.MenuItem;
import java.math.BigDecimal;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MenuItemResponse {

    private Long id;
    private String name;
    private String description;
    private BigDecimal price;
    private boolean available;
    private String imageUrl;
    private CategoryResponse category;
    private List<ModifierGroupResponse> modifierGroups;

    public static MenuItemResponse from(MenuItem item, List<ModifierGroupResponse> modifierGroups) {
        return MenuItemResponse.builder()
                .id(item.getId())
                .name(item.getName())
                .description(item.getDescription())
                .price(item.getPrice())
                .available(item.isAvailable())
                .imageUrl(item.getImageUrl())
                .category(item.getCategory() != null ? CategoryResponse.from(item.getCategory()) : null)
                .modifierGroups(modifierGroups)
                .build();
    }
}
