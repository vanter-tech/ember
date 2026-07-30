package com.vanter.ember.catalog.model.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class MenuDTO {

    private Long id;
    private String name;
    private String description;
    private String imgUrl;
    private List<MenuItemResponse> items;

}
