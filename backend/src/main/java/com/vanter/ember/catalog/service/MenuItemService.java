package com.vanter.ember.catalog.service;

import com.vanter.ember.catalog.model.MenuItem;
import com.vanter.ember.catalog.model.dto.MenuItemRequest;
import com.vanter.ember.catalog.repository.CategoryRepository;
import com.vanter.ember.catalog.repository.MenuItemRepository;
import com.vanter.ember.config.MinioProperties;
import com.vanter.ember.config.ResourceNotFoundException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class MenuItemService {

    private final MenuItemRepository menuItemRepository;
    private final CategoryRepository categoryRepository;
    private final ImageUploadService imageUploadService;
    private final MinioProperties minioProperties;

    public MenuItem create(MenuItemRequest request, MultipartFile image) {
        var category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Category not found: " + request.getCategoryId()));

        var item = MenuItem.builder()
                .name(request.getName())
                .description(request.getDescription())
                .price(request.getPrice())
                .category(category)
                .available(true)
                .build();

        if (image != null && !image.isEmpty()) {
            item.setImageUrl(imageUploadService.uploadImage(image, minioProperties.getBucket()));
        }

        return menuItemRepository.save(item);
    }

    public List<MenuItem> findAll() {
        return menuItemRepository.findAll();
    }

    public MenuItem findById(Long id) {
        return menuItemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Menu item not found: " + id));
    }

    public MenuItem update(Long id, MenuItemRequest request, MultipartFile image) {
        var item = findById(id);
        var category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Category not found: " + request.getCategoryId()));

        item.setName(request.getName());
        item.setDescription(request.getDescription());
        item.setPrice(request.getPrice());
        item.setCategory(category);

        if (image != null && !image.isEmpty()) {
            if (item.getImageUrl() != null) {
                imageUploadService.deleteImage(item.getImageUrl());
            }
            item.setImageUrl(imageUploadService.uploadImage(image, minioProperties.getBucket()));
        }

        return menuItemRepository.save(item);
    }

    public MenuItem toggleAvailability(Long id) {
        var item = findById(id);
        item.setAvailable(!item.isAvailable());
        return menuItemRepository.save(item);
    }

    public void delete(Long id) {
        var item = findById(id);
        if (item.getImageUrl() != null) {
            imageUploadService.deleteImage(item.getImageUrl());
        }
        menuItemRepository.deleteById(id);
    }
}
