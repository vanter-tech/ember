package com.vanter.ember.catalog.service;

import com.vanter.ember.catalog.model.Category;
import com.vanter.ember.catalog.repository.CategoryRepository;
import com.vanter.ember.config.ResourceNotFoundException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public Category create(String name) {
        if (categoryRepository.existsByName(name)) {
            throw new IllegalArgumentException("Category name already exists: " + name);
        }
        return categoryRepository.save(Category.builder().name(name).build());
    }

    public List<Category> findAll() {
        return categoryRepository.findAll();
    }

    public Category findById(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + id));
    }

    public Category update(Long id, String newName) {
        Category category = findById(id);
        if (!category.getName().equals(newName) && categoryRepository.existsByName(newName)) {
            throw new IllegalArgumentException("Category name already exists: " + newName);
        }
        category.setName(newName);
        return categoryRepository.save(category);
    }

    public void delete(Long id) {
        findById(id);
        categoryRepository.deleteById(id);
    }
}
