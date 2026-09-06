package com.vanter.ember.identity.dto;

import com.vanter.ember.identity.model.BannerKey;
import jakarta.validation.constraints.NotNull;

/** PATCH body for {@code /users/me}. An unknown {@code bannerKey} fails deserialization → 400. */
public record UpdateProfileRequest(@NotNull BannerKey bannerKey) {}
