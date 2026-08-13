package com.vanter.ember.restaurant.service;

import com.vanter.ember.restaurant.model.Restaurant;
import com.vanter.ember.restaurant.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RestaurantService {

    private final RestaurantRepository restaurantRepository;

    /**
     * Joins an existing restaurant by slug, or creates a new one when no slug is given.
     */
    public Restaurant createOrJoin(String restaurantName, String restaurantSlug, String fallbackNameSeed) {
        if (StringUtils.hasText(restaurantSlug)) {
            return restaurantRepository.findBySlug(slugify(restaurantSlug))
                    .orElseThrow(() -> new IllegalArgumentException(
                            "No restaurant found for slug: " + restaurantSlug));
        }

        String name = StringUtils.hasText(restaurantName)
                ? restaurantName
                : fallbackNameSeed + "'s Restaurant";

        Restaurant restaurant = Restaurant.builder()
                .name(name)
                .slug(uniqueSlug(name))
                .build();

        return restaurantRepository.save(restaurant);
    }

    private String uniqueSlug(String name) {
        String base = slugify(name);
        String candidate = base;
        while (restaurantRepository.existsBySlug(candidate)) {
            candidate = base + "-" + UUID.randomUUID().toString().substring(0, 6);
        }
        return candidate;
    }

    private String slugify(String value) {
        String slug = value.trim().toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
        return StringUtils.hasText(slug) ? slug : "restaurant";
    }
}
