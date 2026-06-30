package com.vanter.ember.catalog.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class MenuItemRequest {

    @NotBlank(message = "Name is required")
    private String name;
    private String description;
    @NotNull(message = "Price is required")
    private BigDecimal price;
    private MultipartFile imageUrl;
    private boolean available;
    @NotNull(message = "Category is required")
    private Long categoryId;
}
