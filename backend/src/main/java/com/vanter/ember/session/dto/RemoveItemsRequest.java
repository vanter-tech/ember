package com.vanter.ember.session.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record RemoveItemsRequest(@NotEmpty List<@NotBlank String> itemIds) {}
