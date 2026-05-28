package com.vanter.ember.catalog.service;

import com.vanter.ember.catalog.model.Category;
import com.vanter.ember.catalog.model.dto.CategoryResponse;
import com.vanter.ember.catalog.repository.CategoryRepository;
import com.vanter.ember.config.ResourceNotFoundException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public CategoryResponse create(String name) {
        if (categoryRepository.existsByName(name)) {
            throw new IllegalArgumentException("Category name already exists: " + name);
        }
        return CategoryResponse.from(categoryRepository.save(Category.builder().name(name).build()));
    }

    public List<CategoryResponse> findAll() {
        return categoryRepository.findAll().stream()
                .map(CategoryResponse::from)
                .toList();
    }

    public CategoryResponse findById(Long id) {
        return CategoryResponse.from(findEntityById(id));
    }

    public CategoryResponse update(Long id, String newName) {
        Category category = findEntityById(id);
        if (!category.getName().equals(newName) && categoryRepository.existsByName(newName)) {
            throw new IllegalArgumentException("Category name already exists: " + newName);
        }
        category.setName(newName);
        return CategoryResponse.from(categoryRepository.save(category));
    }

    public void delete(Long id) {
        findEntityById(id);
        categoryRepository.deleteById(id);
    }

    public Category findEntityById(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + id));
    }
}
