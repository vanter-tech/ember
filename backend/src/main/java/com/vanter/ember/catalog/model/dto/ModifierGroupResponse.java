package com.vanter.ember.catalog.model.dto;

import com.vanter.ember.catalog.model.ModifierGroup;
import com.vanter.ember.catalog.model.SelectionType;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ModifierGroupResponse {

    private Long id;
    private String name;
    private SelectionType selectionType;
    private int minSelections;
    private Integer maxSelections;
    private boolean active;
    private List<ModifierOptionResponse> options;

    public static ModifierGroupResponse from(ModifierGroup group, List<ModifierOptionResponse> options) {
        return ModifierGroupResponse.builder()
                .id(group.getId())
                .name(group.getName())
                .selectionType(group.getSelectionType())
                .minSelections(group.getMinSelections())
                .maxSelections(group.getMaxSelections())
                .active(group.isActive())
                .options(options)
                .build();
    }
}
