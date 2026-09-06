package com.vanter.ember.identity.dto;

import com.vanter.ember.identity.model.BannerKey;

/** The caller's own profile. {@code bannerKey} is null when no preset has been chosen. */
public record UserProfileResponse(String name, String email, BannerKey bannerKey) {}
