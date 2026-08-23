package com.vanter.ember.catalog.model.dto;

import com.vanter.ember.catalog.model.SelectionType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Data;

@Data
public class ModifierGroupRequest {

    @NotBlank(message = "Group name is required")
    private String name;

    @NotNull(message = "Selection type is required")
    private SelectionType selectionType;

    private Integer minSelections;
    private Integer maxSelections;

    @NotEmpty(message = "At least one option is required")
    @Valid
    private List<ModifierOptionRequest> options;
}
