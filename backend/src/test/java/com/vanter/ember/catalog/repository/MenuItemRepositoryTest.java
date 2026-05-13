package com.vanter.ember.catalog.repository;

import com.vanter.ember.catalog.model.Category;
import com.vanter.ember.catalog.model.MenuItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class MenuItemRepositoryTest {

    @Autowired MenuItemRepository menuItemRepository;
    @Autowired CategoryRepository categoryRepository;

    private Category burgers;

    @BeforeEach
    void setUp() {
        burgers = categoryRepository.save(Category.builder().name("Burgers").build());
    }

    @Test
    void save_persistsMenuItem() {
        MenuItem item = MenuItem.builder()
                .name("Classic Burger")
                .description("Beef patty with lettuce")
                .price(new BigDecimal("9.99"))
                .category(burgers)
                .available(true)
                .build();

        MenuItem saved = menuItemRepository.save(item);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getName()).isEqualTo("Classic Burger");
        assertThat(saved.getPrice()).isEqualByComparingTo("9.99");
        assertThat(saved.getCategory().getId()).isEqualTo(burgers.getId());
    }

    @Test
    void findByCategoryId_returnsItemsForCategory() {
        Category drinks = categoryRepository.save(Category.builder().name("Drinks").build());

        menuItemRepository.save(MenuItem.builder().name("Burger").price(new BigDecimal("8.00"))
                .category(burgers).available(true).build());
        menuItemRepository.save(MenuItem.builder().name("Coke").price(new BigDecimal("2.00"))
                .category(drinks).available(true).build());

        List<MenuItem> result = menuItemRepository.findByCategoryId(burgers.getId());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Burger");
    }

    @Test
    void findByAvailableTrue_returnsOnlyAvailableItems() {
        menuItemRepository.save(MenuItem.builder().name("Burger").price(new BigDecimal("8.00"))
                .category(burgers).available(true).build());
        menuItemRepository.save(MenuItem.builder().name("Sold Out Item").price(new BigDecimal("5.00"))
                .category(burgers).available(false).build());

        List<MenuItem> result = menuItemRepository.findByAvailableTrue();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Burger");
    }
}
