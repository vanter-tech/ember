package com.vanter.ember.catalog.model.dto;

import com.vanter.ember.catalog.model.Category;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CategoryResponse {

    private Long id;
    private String name;
    private String description;
    private String imgUrl;

    public static CategoryResponse from(Category category) {
        return CategoryResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .description(category.getDescription())
                .imgUrl(category.getImgUrl())
                .build();
    }
}
