package com.vanter.ember.catalog.model.dto;

import com.vanter.ember.catalog.model.RestaurantTable;
import com.vanter.ember.catalog.model.TableStatus;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RestaurantTableResponse {

    private Long id;
    private int number;
    private int capacity;
    private TableStatus status;

    public static RestaurantTableResponse from(RestaurantTable table) {
        return RestaurantTableResponse.builder()
                .id(table.getId())
                .number(table.getNumber())
                .capacity(table.getCapacity())
                .status(table.getStatus())
                .build();
    }
}
