package com.vanter.ember.catalog.model.dto;

import com.vanter.ember.catalog.model.ModifierOption;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ModifierOptionResponse {

    private Long id;
    private String name;
    private java.math.BigDecimal priceDelta;
    private boolean active;

    public static ModifierOptionResponse from(ModifierOption option) {
        return ModifierOptionResponse.builder()
                .id(option.getId())
                .name(option.getName())
                .priceDelta(option.getPriceDelta())
                .active(option.isActive())
                .build();
    }
}
