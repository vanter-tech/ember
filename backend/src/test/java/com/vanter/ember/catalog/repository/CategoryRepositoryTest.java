package com.vanter.ember.catalog.repository;

import com.vanter.ember.catalog.model.Category;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class CategoryRepositoryTest {

    @Autowired CategoryRepository repository;

    @Test
    void save_persistsCategory() {
        Category category = Category.builder().name("Burgers").build();

        Category saved = repository.save(category);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getName()).isEqualTo("Burgers");
    }

    @Test
    void findByName_returnsCategory() {
        repository.save(Category.builder().name("Pizzas").build());

        Optional<Category> found = repository.findByName("Pizzas");

        assertThat(found).isPresent();
    }

    @Test
    void name_mustBeUnique() {
        repository.save(Category.builder().name("Drinks").build());

        assertThatThrownBy(() ->
                repository.saveAndFlush(Category.builder().name("Drinks").build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
