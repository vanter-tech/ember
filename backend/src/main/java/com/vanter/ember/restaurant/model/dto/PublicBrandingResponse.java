package com.vanter.ember.restaurant.model.dto;

import com.vanter.ember.settings.model.SettingsPayload;
import lombok.Builder;
import lombok.Data;

/**
 * Curated, pre-login-safe subset of {@link SettingsPayload.BrandingSettings}. Deliberately
 * excludes legalName/ruc/phone/address/wifiName, which stay behind the authenticated
 * {@code GET /settings} endpoint.
 */
@Data
@Builder
public class PublicBrandingResponse {

    private String slug;
    private String businessName;
    private String primaryThemeColor;
    private String openingTime;
    private String closingTime;

    public static PublicBrandingResponse from(String slug, String fallbackName, SettingsPayload.BrandingSettings branding) {
        return PublicBrandingResponse.builder()
                .slug(slug)
                .businessName(hasText(branding.getBusinessName()) ? branding.getBusinessName() : fallbackName)
                .primaryThemeColor(branding.getPrimaryThemeColor())
                .openingTime(branding.getOpeningTime())
                .closingTime(branding.getClosingTime())
                .build();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
