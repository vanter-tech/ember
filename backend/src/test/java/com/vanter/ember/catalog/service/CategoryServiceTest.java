package com.vanter.ember.catalog.service;

import com.vanter.ember.catalog.model.Category;
import com.vanter.ember.catalog.repository.CategoryRepository;
import com.vanter.ember.config.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock CategoryRepository categoryRepository;
    @InjectMocks CategoryService categoryService;

    @Test
    void create_savesAndReturnsCategory() {
        when(categoryRepository.existsByName("Burgers")).thenReturn(false);
        when(categoryRepository.save(any())).thenReturn(
                Category.builder().id(1L).name("Burgers").build());

        Category result = categoryService.create("Burgers");

        assertThat(result.getName()).isEqualTo("Burgers");
        verify(categoryRepository).save(any(Category.class));
    }

    @Test
    void create_throwsWhenNameAlreadyExists() {
        when(categoryRepository.existsByName("Burgers")).thenReturn(true);

        assertThatThrownBy(() -> categoryService.create("Burgers"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void findAll_returnsAllCategories() {
        when(categoryRepository.findAll()).thenReturn(
                List.of(Category.builder().id(1L).name("Burgers").build(),
                        Category.builder().id(2L).name("Drinks").build()));

        List<Category> result = categoryService.findAll();

        assertThat(result).hasSize(2);
    }

    @Test
    void findById_returnsCategory() {
        when(categoryRepository.findById(1L)).thenReturn(
                Optional.of(Category.builder().id(1L).name("Burgers").build()));

        Category result = categoryService.findById(1L);

        assertThat(result.getName()).isEqualTo("Burgers");
    }

    @Test
    void findById_throwsWhenNotFound() {
        when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.findById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void update_changesName() {
        Category existing = Category.builder().id(1L).name("Burgers").build();
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(categoryRepository.existsByName("Sandwiches")).thenReturn(false);
        when(categoryRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Category result = categoryService.update(1L, "Sandwiches");

        assertThat(result.getName()).isEqualTo("Sandwiches");
    }

    @Test
    void delete_removesCategory() {
        when(categoryRepository.findById(1L)).thenReturn(
                Optional.of(Category.builder().id(1L).name("Burgers").build()));

        categoryService.delete(1L);

        verify(categoryRepository).deleteById(1L);
    }
}
