package com.vanter.ember.catalog.repository;

import com.vanter.ember.catalog.model.RestaurantTable;
import com.vanter.ember.catalog.model.TableStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class RestaurantTableRepositoryTest {

    @Autowired RestaurantTableRepository restaurantTableRepository;

    @Test
    void save_persistsTable() {
        RestaurantTable table = RestaurantTable.builder()
                .number(1).capacity(4).status(TableStatus.AVAILABLE).build();

        RestaurantTable saved = restaurantTableRepository.save(table);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getNumber()).isEqualTo(1);
        assertThat(saved.getStatus()).isEqualTo(TableStatus.AVAILABLE);
    }

    @Test
    void findByStatus_returnsOnlyMatchingTables() {
        restaurantTableRepository.save(
                RestaurantTable.builder().number(1).capacity(4).status(TableStatus.AVAILABLE).build());
        restaurantTableRepository.save(
                RestaurantTable.builder().number(2).capacity(2).status(TableStatus.OCCUPIED).build());

        List<RestaurantTable> available = restaurantTableRepository.findByStatus(TableStatus.AVAILABLE);

        assertThat(available).hasSize(1);
        assertThat(available.get(0).getNumber()).isEqualTo(1);
    }
}
