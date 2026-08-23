package com.vanter.ember.catalog.service;

import com.vanter.ember.catalog.model.MenuItem;
import com.vanter.ember.catalog.model.dto.MenuItemRequest;
import com.vanter.ember.catalog.model.dto.MenuItemResponse;
import com.vanter.ember.catalog.model.dto.ModifierGroupAssignment;
import com.vanter.ember.catalog.repository.CategoryRepository;
import com.vanter.ember.catalog.repository.MenuItemRepository;
import com.vanter.ember.config.MinioProperties;
import com.vanter.ember.config.ResourceNotFoundException;

import io.minio.messages.Item;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.parameters.P;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class MenuItemService {

    private final MenuItemRepository menuItemRepository;
    private final CategoryRepository categoryRepository;
    private final ImageUploadService imageUploadService;
    private final MinioProperties minioProperties;
    private final ModifierGroupService modifierGroupService;

    public MenuItemResponse create(MenuItemRequest request, MultipartFile image) {
        var category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Category not found: " + request.getCategoryId()));

        var item = MenuItem.builder()
                .name(request.getName())
                .description(request.getDescription())
                .price(request.getPrice())
                .category(category)
                .available(request.isAvailable())
                .build();

        if (image != null && !image.isEmpty()) {
            item.setImageUrl(imageUploadService.uploadImage(image, minioProperties.getBucket()));
        }

        MenuItem saved = menuItemRepository.save(item);
        return MenuItemResponse.from(saved, modifierGroupService.findActiveGroupsForMenuItem(saved.getId()));
    }

    public Page<MenuItemResponse> findAll(Long id, Pageable pageable) {
        Page<MenuItem> items = id != null
                ? menuItemRepository.findByCategoryId(id, pageable)
                : menuItemRepository.findAll(pageable);
        return items.map(item -> MenuItemResponse.from(item, modifierGroupService.findActiveGroupsForMenuItem(item.getId())));
    }

    public MenuItemResponse findById(Long id) {
        MenuItem item = findEntityById(id);
        return MenuItemResponse.from(item, modifierGroupService.findActiveGroupsForMenuItem(item.getId()));
    }

    public MenuItemResponse update(Long id, MenuItemRequest request) {
        var item = findEntityById(id);
        var category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Category not found: " + request.getCategoryId()));

        item.setName(request.getName());
        item.setDescription(request.getDescription());
        item.setPrice(request.getPrice());
        item.setCategory(category);

        if (request.getImageUrl() != null && !request.getImageUrl().isEmpty()) {
            String UrlVieja = item.getImageUrl();
            String NuevaUrl = imageUploadService.uploadImage(request.getImageUrl(), "ember-media");
            item.setImageUrl(NuevaUrl);
            if (UrlVieja != null && !UrlVieja.isEmpty()) {
                imageUploadService.deleteImage(UrlVieja);
            }
        }

        MenuItem saved = menuItemRepository.save(item);
        return MenuItemResponse.from(saved, modifierGroupService.findActiveGroupsForMenuItem(saved.getId()));
    }

    public MenuItemResponse toggleAvailability(Long id) {
        var item = findEntityById(id);
        item.setAvailable(!item.isAvailable());
        MenuItem saved = menuItemRepository.save(item);
        return MenuItemResponse.from(saved, modifierGroupService.findActiveGroupsForMenuItem(saved.getId()));
    }

    public void delete(Long id) {
        var item = findEntityById(id);
        if (item.getImageUrl() != null) {
            imageUploadService.deleteImage(item.getImageUrl());
        }
        menuItemRepository.deleteById(id);
    }

    public MenuItemResponse assignModifierGroups(Long id, List<ModifierGroupAssignment> assignments) {
        MenuItem item = findEntityById(id);
        modifierGroupService.replaceMenuItemAssignments(id, assignments);
        return MenuItemResponse.from(item, modifierGroupService.findActiveGroupsForMenuItem(id));
    }

    private MenuItem findEntityById(Long id) {
        return menuItemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Menu item not found: " + id));
    }
}
