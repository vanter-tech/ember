package com.vanter.ember.catalog.service;

import com.vanter.ember.catalog.model.RestaurantTable;
import com.vanter.ember.catalog.model.TableStatus;
import com.vanter.ember.catalog.model.dto.RestaurantTableRequest;
import com.vanter.ember.catalog.model.dto.RestaurantTableResponse;
import com.vanter.ember.catalog.repository.RestaurantTableRepository;
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
class RestaurantTableServiceTest {

    @Mock RestaurantTableRepository tableRepository;
    @InjectMocks RestaurantTableService tableService;

    private RestaurantTableRequest tableRequest() {
        RestaurantTableRequest req = new RestaurantTableRequest();
        req.setNumber(5);
        req.setCapacity(4);
        return req;
    }

    private RestaurantTable availableTable() {
        return RestaurantTable.builder()
                .id(1L).number(5).capacity(4).status(TableStatus.AVAILABLE).build();
    }

    @Test
    void create_savesTableWithAvailableStatus() {
        when(tableRepository.save(any())).thenAnswer(inv -> {
            RestaurantTable t = inv.getArgument(0);
            t.setId(1L);
            return t;
        });

        RestaurantTableResponse result = tableService.create(tableRequest());

        assertThat(result.getNumber()).isEqualTo(5);
        assertThat(result.getCapacity()).isEqualTo(4);
        assertThat(result.getStatus()).isEqualTo(TableStatus.AVAILABLE);
    }

    @Test
    void findAll_returnsAllTables() {
        when(tableRepository.findAll()).thenReturn(List.of(availableTable()));

        assertThat(tableService.findAll()).hasSize(1);
    }

    @Test
    void findByStatus_returnsFilteredTables() {
        when(tableRepository.findByStatus(TableStatus.AVAILABLE))
                .thenReturn(List.of(availableTable()));

        List<RestaurantTableResponse> result = tableService.findByStatus(TableStatus.AVAILABLE);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo(TableStatus.AVAILABLE);
    }

    @Test
    void findById_returnsTable() {
        when(tableRepository.findById(1L)).thenReturn(Optional.of(availableTable()));

        assertThat(tableService.findById(1L).getNumber()).isEqualTo(5);
    }

    @Test
    void findById_throwsWhenNotFound() {
        when(tableRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tableService.findById(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateCapacity_updatesAndSaves() {
        when(tableRepository.findById(1L)).thenReturn(Optional.of(availableTable()));
        when(tableRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RestaurantTableResponse result = tableService.updateCapacity(1L, 6);

        assertThat(result.getCapacity()).isEqualTo(6);
        verify(tableRepository).save(any());
    }

    @Test
    void setOccupied_changesStatusToOccupied() {
        when(tableRepository.findById(1L)).thenReturn(Optional.of(availableTable()));
        when(tableRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RestaurantTableResponse result = tableService.setOccupied(1L);

        assertThat(result.getStatus()).isEqualTo(TableStatus.OCCUPIED);
    }

    @Test
    void setOccupied_throwsWhenAlreadyOccupied() {
        RestaurantTable occupied = RestaurantTable.builder()
                .id(1L).number(5).capacity(4).status(TableStatus.OCCUPIED).build();
        when(tableRepository.findById(1L)).thenReturn(Optional.of(occupied));

        assertThatThrownBy(() -> tableService.setOccupied(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already occupied");
    }

    @Test
    void setAvailable_changesStatusToAvailable() {
        RestaurantTable occupied = RestaurantTable.builder()
                .id(1L).number(5).capacity(4).status(TableStatus.OCCUPIED).build();
        when(tableRepository.findById(1L)).thenReturn(Optional.of(occupied));
        when(tableRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RestaurantTableResponse result = tableService.setAvailable(1L);

        assertThat(result.getStatus()).isEqualTo(TableStatus.AVAILABLE);
    }
}
