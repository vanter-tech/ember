package com.vanter.ember.catalog.service;

import com.vanter.ember.catalog.model.MenuItem;
import com.vanter.ember.catalog.model.dto.MenuDTO;
import com.vanter.ember.catalog.model.dto.MenuItemResponse;
import com.vanter.ember.catalog.repository.CategoryRepository;
import com.vanter.ember.catalog.repository.MenuItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MenuService {

    private final CategoryRepository categoryRepository;
    private final MenuItemRepository menuItemRepository;
    private final ModifierGroupService modifierGroupService;

    public List<MenuDTO> getMenu() {
        var categories = categoryRepository.findAll();
        var menuItems = menuItemRepository.findAll();

        Map<Object, List<MenuItem>> itemsByCategory = menuItems.stream()
                .collect(Collectors.groupingBy(item -> item.getCategory().getId()));

        return categories.stream().map(category -> {

            List<MenuItem> rawItems = itemsByCategory.getOrDefault(category.getId(), Collections.emptyList());
            List<MenuItemResponse> itemResponses = rawItems.stream()
                    .map(item -> MenuItemResponse.from(item, modifierGroupService.findActiveGroupsForMenuItem(item.getId())))
                    .toList();

            return MenuDTO.builder()
                    .id(category.getId())
                    .name(category.getName())
                    .description(category.getDescription())
                    .imgUrl(category.getImgUrl())
                    .items(itemResponses)
                    .build();
        }).toList();
    }


}
