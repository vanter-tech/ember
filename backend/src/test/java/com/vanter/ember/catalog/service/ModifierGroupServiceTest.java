package com.vanter.ember.catalog.service;

import com.vanter.ember.catalog.model.ModifierGroup;
import com.vanter.ember.catalog.model.ModifierOption;
import com.vanter.ember.catalog.model.SelectionType;
import com.vanter.ember.catalog.model.dto.ModifierGroupRequest;
import com.vanter.ember.catalog.model.dto.ModifierOptionRequest;
import com.vanter.ember.catalog.repository.MenuItemModifierGroupRepository;
import com.vanter.ember.catalog.repository.ModifierGroupRepository;
import com.vanter.ember.catalog.repository.ModifierOptionRepository;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ModifierGroupServiceTest {

    @Mock ModifierGroupRepository modifierGroupRepository;
    @Mock ModifierOptionRepository modifierOptionRepository;
    @Mock MenuItemModifierGroupRepository menuItemModifierGroupRepository;
    @InjectMocks ModifierGroupService modifierGroupService;

    private ModifierGroupRequest requestOf(SelectionType type, Integer min, Integer max) {
        ModifierGroupRequest request = new ModifierGroupRequest();
        request.setName("Término de cocción");
        request.setSelectionType(type);
        request.setMinSelections(min);
        request.setMaxSelections(max);
        ModifierOptionRequest option = new ModifierOptionRequest();
        option.setName("Término medio");
        option.setPriceDelta(BigDecimal.ZERO);
        request.setOptions(List.of(option));
        return request;
    }

    @Test
    void create_singleRequired_forcesMinMaxToOne() {
        when(modifierGroupRepository.save(any())).thenAnswer(inv -> {
            ModifierGroup g = inv.getArgument(0);
            g.setId(1L);
            return g;
        });
        when(modifierOptionRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = modifierGroupService.create(requestOf(SelectionType.SINGLE_REQUIRED, null, null));

        assertThat(result.getMinSelections()).isEqualTo(1);
        assertThat(result.getMaxSelections()).isEqualTo(1);
    }

    @Test
    void create_singleRequired_rejectsConflictingMinMax() {
        assertThatThrownBy(() -> modifierGroupService.create(requestOf(SelectionType.SINGLE_REQUIRED, 2, 2)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SINGLE_REQUIRED");
    }

    @Test
    void create_multiOptional_forcesMinZeroMaxNull() {
        when(modifierGroupRepository.save(any())).thenAnswer(inv -> {
            ModifierGroup g = inv.getArgument(0);
            g.setId(2L);
            return g;
        });
        when(modifierOptionRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = modifierGroupService.create(requestOf(SelectionType.MULTI_OPTIONAL, null, null));

        assertThat(result.getMinSelections()).isEqualTo(0);
        assertThat(result.getMaxSelections()).isNull();
    }

    @Test
    void create_multiLimited_requiresBothBounds() {
        assertThatThrownBy(() -> modifierGroupService.create(requestOf(SelectionType.MULTI_LIMITED, null, 2)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("require both");
    }

    @Test
    void create_multiLimited_rejectsMinGreaterThanMax() {
        assertThatThrownBy(() -> modifierGroupService.create(requestOf(SelectionType.MULTI_LIMITED, 3, 2)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void findActiveGroupsForMenuItem_excludesInactiveGroupsAndOptions() {
        var assignment = com.vanter.ember.catalog.model.MenuItemModifierGroup.builder()
                .id(1L).menuItemId(10L).groupId(5L).displayOrder(0).build();
        when(menuItemModifierGroupRepository.findByMenuItemIdOrderByDisplayOrder(10L))
                .thenReturn(List.of(assignment));

        ModifierGroup inactiveGroup = ModifierGroup.builder()
                .id(5L).name("Extras").selectionType(SelectionType.MULTI_OPTIONAL)
                .minSelections(0).maxSelections(null).active(false).build();
        when(modifierGroupRepository.findById(5L)).thenReturn(java.util.Optional.of(inactiveGroup));

        var result = modifierGroupService.findActiveGroupsForMenuItem(10L);

        assertThat(result).isEmpty();
    }
}
