package com.vanter.ember.settings.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.DayOfWeek;
import org.junit.jupiter.api.Test;

class SettingsPayloadTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void defaults_neverExposeARawSecretField() {
        SettingsPayload payload = new SettingsPayload();

        assertThat(payload.getPaymentGateway()).isNotNull();
        assertThat(payload.getPaymentGateway().getSecretRef()).isNull();
        assertThat(payload.getBusinessHours().getSchedule()).isEmpty();
        assertThat(payload.getBilling().getTaxRules()).isEmpty();
    }

    @Test
    void roundTrips_paymentGatewayBusinessHoursAndTaxRules_throughJson() throws Exception {
        SettingsPayload payload = new SettingsPayload();

        payload.getPaymentGateway().setEnabled(true);
        payload.getPaymentGateway().setProvider("STRIPE");
        payload.getPaymentGateway().setPublicKey("pk_live_example");
        payload.getPaymentGateway().setSecretRef("GATEWAY_SECRET_STRIPE");

        SettingsPayload.BusinessHoursSettings.DaySchedule monday =
                new SettingsPayload.BusinessHoursSettings.DaySchedule();
        monday.setDay(DayOfWeek.MONDAY);
        monday.setClosed(false);
        monday.setOpenTime("09:00");
        monday.setCloseTime("22:00");
        payload.getBusinessHours().getSchedule().add(monday);

        SettingsPayload.TaxRule ivaRule = new SettingsPayload.TaxRule();
        ivaRule.setName("IVA");
        ivaRule.setRate(18.0);
        ivaRule.setIncludedInPrice(true);
        payload.getBilling().getTaxRules().add(ivaRule);

        String json = objectMapper.writeValueAsString(payload);
        SettingsPayload roundTripped = objectMapper.readValue(json, SettingsPayload.class);

        assertThat(roundTripped.getPaymentGateway().isEnabled()).isTrue();
        assertThat(roundTripped.getPaymentGateway().getProvider()).isEqualTo("STRIPE");
        assertThat(roundTripped.getPaymentGateway().getSecretRef()).isEqualTo("GATEWAY_SECRET_STRIPE");

        assertThat(roundTripped.getBusinessHours().getSchedule()).hasSize(1);
        assertThat(roundTripped.getBusinessHours().getSchedule().get(0).getDay())
                .isEqualTo(DayOfWeek.MONDAY);

        assertThat(roundTripped.getBilling().getTaxRules()).hasSize(1);
        assertThat(roundTripped.getBilling().getTaxRules().get(0).getRate()).isEqualTo(18.0);
    }
}
