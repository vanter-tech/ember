package com.vanter.ember.settings.model;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class SettingsPayload {

    private BrandingSettings branding = new BrandingSettings();
    private MenuSettings menu = new MenuSettings();
    private BillingSettings billing = new BillingSettings();
    private HardwareSettings hardware = new HardwareSettings();
    private SpaceSettings space = new SpaceSettings();

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
