package com.vanter.ember.settings.model;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class SettingsPayload {

    private BrandingSettings branding = new BrandingSettings();
    private MenuSettings menu = new MenuSettings();
    private BillingSettings billing = new BillingSettings();
    private HardwareSettings hardware = new HardwareSettings();
    private SpaceSettings space = new SpaceSettings();
    private PaymentGatewaySettings paymentGateway = new PaymentGatewaySettings();
    private BusinessHoursSettings businessHours = new BusinessHoursSettings();
    private LoyaltySettings loyalty = new LoyaltySettings();

    @Data
    public static class BrandingSettings{
        private String businessName;
        private String legalName;
        private String ruc;
        private String phone;
        private String address;
        private String openingTime;
        private String closingTime;
        private String wifiName;
        private String primaryThemeColor;
    }

    @Data
    public static class MenuSettings{
        private boolean showOutOfStockItems;
        private boolean enableItemSearch;
    }

    @Data
    public static class BillingSettings{
        private String currencySymbol;
        private Double taxRate;
        private boolean isTaxIncludeInMenuPrice;
        private List<Integer> suggestedTipPercentage;
        private List<TaxRule> taxRules = new ArrayList<>();
    }

    @Data
    public static class TaxRule {
        private String name;

        @Min(value = 0, message = "Tax rate cannot be negative")
        @Max(value = 100, message = "Tax rate cannot exceed 100%")
        private Double rate;

        private boolean includedInPrice;
    }

    @Data
    public static class PaymentGatewaySettings {
        private boolean enabled;
        private String provider;
        private String publicKey;

        // Secret-reference pattern: this holds an opaque alias (e.g. an env var or
        // secrets-manager key name) that the backend resolves at call time. There is
        // deliberately no field here that can hold a raw API secret/private key.
        private String secretRef;
    }

    @Data
    public static class BusinessHoursSettings {
        private List<DaySchedule> schedule = new ArrayList<>();

        @Data
        public static class DaySchedule {
            private DayOfWeek day;
            private boolean closed;
            private String openTime;
            private String closeTime;
        }
    }

    @Data
    public static class LoyaltySettings {
        private boolean enabled;
        private AccrualMode accrualMode = AccrualMode.BY_VISIT;

        @Min(value = 0, message = "Points per visit cannot be negative")
        private int pointsPerVisit = 10;

        @Min(value = 0, message = "Points per currency unit cannot be negative")
        private double pointsPerCurrencyUnit = 1.0;

        @Min(value = 0, message = "Threshold cannot be negative")
        private int plataThreshold = 100;

        @Min(value = 0, message = "Threshold cannot be negative")
        private int oroThreshold = 500;

        @Min(value = 0, message = "Threshold cannot be negative")
        private int platinoThreshold = 1500;
    }

    public enum AccrualMode {
        BY_VISIT,
        BY_AMOUNT_SPENT
    }

    @Data
    public static class HardwareSettings{
        private boolean autoPrintTickets;
        private boolean printCustomerReceipt;
    }

    @Data
    public static class SpaceSettings{
        @Min(value = 1, message = "This restaurant must have at least 1 table")
        @Max(value = 200, message = "Due political security only 200 table are allowed")
        private int TotalTables = 10;
    }


}
