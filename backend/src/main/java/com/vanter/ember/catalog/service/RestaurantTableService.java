package com.vanter.ember.catalog.service;

import com.vanter.ember.catalog.model.RestaurantTable;
import com.vanter.ember.catalog.model.TableStatus;
import com.vanter.ember.catalog.model.dto.RestaurantTableRequest;
import com.vanter.ember.catalog.repository.RestaurantTableRepository;
import com.vanter.ember.config.ResourceNotFoundException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RestaurantTableService {

    private final RestaurantTableRepository tableRepository;

    public RestaurantTable create(RestaurantTableRequest request) {
        return tableRepository.save(RestaurantTable.builder()
                .number(request.getNumber())
                .capacity(request.getCapacity())
                .status(TableStatus.AVAILABLE)
                .build());
    }

    public List<RestaurantTable> findAll() {
        return tableRepository.findAll();
    }

    public List<RestaurantTable> findByStatus(TableStatus status) {
        return tableRepository.findByStatus(status);
    }

    public RestaurantTable findById(Long id) {
        return tableRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Table not found: " + id));
    }

    public RestaurantTable updateCapacity(Long id, int capacity) {
        RestaurantTable table = findById(id);
        table.setCapacity(capacity);
        return tableRepository.save(table);
    }

    public RestaurantTable setOccupied(Long id) {
        RestaurantTable table = findById(id);
        if (table.getStatus() == TableStatus.OCCUPIED) {
            throw new IllegalStateException("Table " + table.getNumber() + " is already occupied");
        }
        table.setStatus(TableStatus.OCCUPIED);
        return tableRepository.save(table);
    }

    public RestaurantTable setAvailable(Long id) {
        RestaurantTable table = findById(id);
        table.setStatus(TableStatus.AVAILABLE);
        return tableRepository.save(table);
    }

    public void delete(Long id) {
        findById(id);
        tableRepository.deleteById(id);
    }
}
