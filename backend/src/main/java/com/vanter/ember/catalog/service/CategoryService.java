package com.vanter.ember.catalog.service;

import com.vanter.ember.catalog.model.Category;
import com.vanter.ember.catalog.model.dto.CategoryRequest;
import com.vanter.ember.catalog.model.dto.CategoryResponse;
import com.vanter.ember.catalog.repository.CategoryRepository;
import com.vanter.ember.catalog.repository.MenuItemRepository;
import com.vanter.ember.config.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final MenuItemRepository menuItemRepository;
    private final ImageUploadService imageUploadService;

    public CategoryResponse create(CategoryRequest request) {
        if (categoryRepository.existsByName(request.getName())) {
            throw new IllegalArgumentException("Category name already exists: " + request.getName());
        }
        String imageUrl = imageUploadService.uploadImage(request.getImage());

        Category category = Category.builder()
                .name((request.getName()))
                .description(request.getDescription())
                .imgUrl(imageUrl)
                .build();

        Category savedCategory = categoryRepository.save(category);
        return CategoryResponse.from(savedCategory);
    }

    public Page<CategoryResponse> findAll(Pageable pageable) {
        return categoryRepository.findAll(pageable)
                .map(category -> {
                    Integer totalItems = menuItemRepository.countByCategoryId(category.getId());
                    return CategoryResponse.from(category, totalItems);
                });
    }

    public CategoryResponse findById(Long id) {
        return CategoryResponse.from(findEntityById(id));
    }

    public CategoryResponse update(CategoryRequest request, Long id) {
        Category category = findEntityById(id);
        if (!category.getName().equals(request.getName()) && categoryRepository.existsByName(request.getName())) {
            throw new IllegalArgumentException("Category name already exists: " + request.getName());
        }
        category.setName(request.getName());
        category.setDescription(request.getDescription());

        if( request.getImage() != null && !request.getImage().isEmpty() ) {
            String UrlVieja = category.getImgUrl();
            String NuevaUrl = imageUploadService.uploadImage(request.getImage());
            category.setImgUrl(NuevaUrl);
            if(UrlVieja != null && !UrlVieja.isEmpty()) {
                imageUploadService.deleteImage(UrlVieja);
            }

        }

        return CategoryResponse.from(categoryRepository.save(category));
    }

    public void delete(Long id) {
        Category response =  findEntityById(id);
        if (response.getImgUrl() != null) {
            imageUploadService.deleteImage(response.getImgUrl());
        }
        categoryRepository.deleteById(id);
    }

    public Category findEntityById(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + id));
    }
}
