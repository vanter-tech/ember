package com.vanter.ember.catalog.service;

import com.vanter.ember.catalog.model.MenuItemModifierGroup;
import com.vanter.ember.catalog.model.ModifierGroup;
import com.vanter.ember.catalog.model.ModifierOption;
import com.vanter.ember.catalog.model.SelectionType;
import com.vanter.ember.catalog.model.dto.ModifierGroupAssignment;
import com.vanter.ember.catalog.model.dto.ModifierGroupRequest;
import com.vanter.ember.catalog.model.dto.ModifierGroupResponse;
import com.vanter.ember.catalog.model.dto.ModifierOptionRequest;
import com.vanter.ember.catalog.model.dto.ModifierOptionResponse;
import com.vanter.ember.catalog.repository.MenuItemModifierGroupRepository;
import com.vanter.ember.catalog.repository.ModifierGroupRepository;
import com.vanter.ember.catalog.repository.ModifierOptionRepository;
import com.vanter.ember.config.ResourceNotFoundException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ModifierGroupService {

    private final ModifierGroupRepository modifierGroupRepository;
    private final ModifierOptionRepository modifierOptionRepository;
    private final MenuItemModifierGroupRepository menuItemModifierGroupRepository;

    public List<ModifierGroupResponse> findAll() {
        return modifierGroupRepository.findAll().stream()
                .map(group -> toResponse(group, modifierOptionRepository.findByGroupIdOrderByDisplayOrder(group.getId())))
                .toList();
    }

    public List<ModifierGroupResponse> findActiveGroupsForMenuItem(Long menuItemId) {
        return menuItemModifierGroupRepository.findByMenuItemIdOrderByDisplayOrder(menuItemId).stream()
                .map(assignment -> modifierGroupRepository.findById(assignment.getGroupId()))
                .flatMap(java.util.Optional::stream)
                .filter(ModifierGroup::isActive)
                .map(group -> {
                    List<ModifierOptionResponse> activeOptions = modifierOptionRepository
                            .findByGroupIdOrderByDisplayOrder(group.getId()).stream()
                            .filter(ModifierOption::isActive)
                            .map(ModifierOptionResponse::from)
                            .toList();
                    return ModifierGroupResponse.from(group, activeOptions);
                })
                .toList();
    }

    public ModifierGroupResponse create(ModifierGroupRequest request) {
        ModifierGroup group = ModifierGroup.builder()
                .name(request.getName())
                .selectionType(request.getSelectionType())
                .active(true)
                .build();
        applySelectionRules(group, request.getSelectionType(), request.getMinSelections(), request.getMaxSelections());
        ModifierGroup saved = modifierGroupRepository.save(group);

        List<ModifierOptionResponse> options = createOptions(saved, request.getOptions());
        return ModifierGroupResponse.from(saved, options);
    }

    public ModifierGroupResponse update(Long id, ModifierGroupRequest request) {
        ModifierGroup group = findGroupById(id);
        group.setName(request.getName());
        group.setSelectionType(request.getSelectionType());
        applySelectionRules(group, request.getSelectionType(), request.getMinSelections(), request.getMaxSelections());
        ModifierGroup saved = modifierGroupRepository.save(group);
        return toResponse(saved, modifierOptionRepository.findByGroupIdOrderByDisplayOrder(saved.getId()));
    }

    public ModifierGroupResponse setActive(Long id, boolean active) {
        ModifierGroup group = findGroupById(id);
        group.setActive(active);
        ModifierGroup saved = modifierGroupRepository.save(group);
        return toResponse(saved, modifierOptionRepository.findByGroupIdOrderByDisplayOrder(saved.getId()));
    }

    public ModifierGroupResponse addOption(Long groupId, ModifierOptionRequest request) {
        ModifierGroup group = findGroupById(groupId);
        int nextOrder = modifierOptionRepository.findByGroupIdOrderByDisplayOrder(groupId).size();
        modifierOptionRepository.save(ModifierOption.builder()
                .group(group)
                .name(request.getName())
                .priceDelta(request.getPriceDelta())
                .active(true)
                .displayOrder(nextOrder)
                .build());
        return toResponse(group, modifierOptionRepository.findByGroupIdOrderByDisplayOrder(groupId));
    }

    public ModifierGroupResponse updateOption(Long groupId, Long optionId, ModifierOptionRequest request) {
        ModifierGroup group = findGroupById(groupId);
        ModifierOption option = findOptionById(optionId);
        option.setName(request.getName());
        option.setPriceDelta(request.getPriceDelta());
        modifierOptionRepository.save(option);
        return toResponse(group, modifierOptionRepository.findByGroupIdOrderByDisplayOrder(groupId));
    }

    public ModifierGroupResponse deactivateOption(Long groupId, Long optionId) {
        ModifierGroup group = findGroupById(groupId);
        ModifierOption option = findOptionById(optionId);
        option.setActive(false);
        modifierOptionRepository.save(option);
        return toResponse(group, modifierOptionRepository.findByGroupIdOrderByDisplayOrder(groupId));
    }

    public void replaceMenuItemAssignments(Long menuItemId, List<ModifierGroupAssignment> assignments) {
        for (ModifierGroupAssignment assignment : assignments) {
            if (!modifierGroupRepository.existsById(assignment.groupId())) {
                throw new ResourceNotFoundException("Modifier group not found: " + assignment.groupId());
            }
        }
        menuItemModifierGroupRepository.deleteByMenuItemId(menuItemId);
        List<MenuItemModifierGroup> rows = assignments.stream()
                .map(a -> MenuItemModifierGroup.builder()
                        .menuItemId(menuItemId)
                        .groupId(a.groupId())
                        .displayOrder(a.displayOrder())
                        .build())
                .toList();
        menuItemModifierGroupRepository.saveAll(rows);
    }

    private List<ModifierOptionResponse> createOptions(ModifierGroup group, List<ModifierOptionRequest> requests) {
        List<ModifierOption> options = new java.util.ArrayList<>();
        for (int i = 0; i < requests.size(); i++) {
            ModifierOptionRequest r = requests.get(i);
            options.add(ModifierOption.builder()
                    .group(group)
                    .name(r.getName())
                    .priceDelta(r.getPriceDelta())
                    .active(true)
                    .displayOrder(i)
                    .build());
        }
        return modifierOptionRepository.saveAll(options).stream().map(ModifierOptionResponse::from).toList();
    }

    private void applySelectionRules(ModifierGroup group, SelectionType type, Integer requestMin, Integer requestMax) {
        switch (type) {
            case SINGLE_REQUIRED -> {
                if ((requestMin != null && requestMin != 1) || (requestMax != null && requestMax != 1)) {
                    throw new IllegalArgumentException(
                            "SINGLE_REQUIRED groups must use minSelections=1 and maxSelections=1");
                }
                group.setMinSelections(1);
                group.setMaxSelections(1);
            }
            case MULTI_OPTIONAL -> {
                if (requestMin != null && requestMin != 0) {
                    throw new IllegalArgumentException("MULTI_OPTIONAL groups must use minSelections=0");
                }
                if (requestMax != null) {
                    throw new IllegalArgumentException("MULTI_OPTIONAL groups do not support maxSelections");
                }
                group.setMinSelections(0);
                group.setMaxSelections(null);
            }
            case MULTI_LIMITED -> {
                if (requestMin == null || requestMax == null) {
                    throw new IllegalArgumentException(
                            "MULTI_LIMITED groups require both minSelections and maxSelections");
                }
                if (requestMin < 0 || requestMax < requestMin) {
                    throw new IllegalArgumentException("minSelections must be >= 0 and <= maxSelections");
                }
                group.setMinSelections(requestMin);
                group.setMaxSelections(requestMax);
            }
        }
    }

    private ModifierGroupResponse toResponse(ModifierGroup group, List<ModifierOption> options) {
        List<ModifierOptionResponse> responses =
                options == null ? List.of() : options.stream().map(ModifierOptionResponse::from).toList();
        return ModifierGroupResponse.from(group, responses);
    }

    private ModifierGroup findGroupById(Long id) {
        return modifierGroupRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Modifier group not found: " + id));
    }

    private ModifierOption findOptionById(Long id) {
        return modifierOptionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Modifier option not found: " + id));
    }
}
