package com.vanter.ember.catalog.service;

import com.vanter.ember.catalog.model.RestaurantTable;
import com.vanter.ember.catalog.model.TableStatus;
import com.vanter.ember.catalog.model.dto.RestaurantTableRequest;
import com.vanter.ember.catalog.model.dto.RestaurantTableResponse;
import com.vanter.ember.catalog.repository.RestaurantTableRepository;
import com.vanter.ember.config.ResourceNotFoundException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RestaurantTableService {

    private final RestaurantTableRepository tableRepository;

    public RestaurantTableResponse create(RestaurantTableRequest request) {
        RestaurantTable table = tableRepository.save(RestaurantTable.builder()
                .number(request.getNumber())
                .capacity(request.getCapacity())
                .status(TableStatus.AVAILABLE)
                .build());
        return RestaurantTableResponse.from(table);
    }

    public List<RestaurantTableResponse> findAll() {
        return tableRepository.findAll().stream()
                .map(RestaurantTableResponse::from)
                .toList();
    }

    public List<RestaurantTableResponse> findByStatus(TableStatus status) {
        return tableRepository.findByStatus(status).stream()
                .map(RestaurantTableResponse::from)
                .toList();
    }

    public RestaurantTableResponse findById(Long id) {
        return RestaurantTableResponse.from(findEntityById(id));
    }

    public RestaurantTableResponse updateCapacity(Long id, int capacity) {
        RestaurantTable table = findEntityById(id);
        table.setCapacity(capacity);
        return RestaurantTableResponse.from(tableRepository.save(table));
    }

    public RestaurantTableResponse setOccupied(Long id) {
        RestaurantTable table = findEntityById(id);
        if (table.getStatus() == TableStatus.OCCUPIED) {
            throw new IllegalStateException("Table " + table.getNumber() + " is already occupied");
        }
        table.setStatus(TableStatus.OCCUPIED);
        return RestaurantTableResponse.from(tableRepository.save(table));
    }

    public RestaurantTableResponse setAvailable(Long id) {
        RestaurantTable table = findEntityById(id);
        table.setStatus(TableStatus.AVAILABLE);
        return RestaurantTableResponse.from(tableRepository.save(table));
    }

    public void delete(Long id) {
        findEntityById(id);
        tableRepository.deleteById(id);
    }

    private RestaurantTable findEntityById(Long id) {
        return tableRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Table not found: " + id));
    }
}
