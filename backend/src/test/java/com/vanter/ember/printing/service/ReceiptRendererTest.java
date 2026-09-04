package com.vanter.ember.printing.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.vanter.ember.settings.model.SettingsPayload;
import org.junit.jupiter.api.Test;

class ReceiptRendererTest {

    private final ReceiptRenderer renderer = new ReceiptRenderer();

    @Test
    void render_includesHeaderBillLineAndFooter() {
        SettingsPayload settings = new SettingsPayload();
        settings.getTicket().setHeaderMessage("Gracias por su visita");
        settings.getTicket().setFooterMessage("Vuelva pronto");

        String out = renderer.render(42L, settings);

        assertThat(out).isEqualTo("Gracias por su visita\nBill #42\nVuelva pronto\n");
    }

    @Test
    void render_omitsBlankHeaderAndFooter() {
        SettingsPayload settings = new SettingsPayload();

        String out = renderer.render(7L, settings);

        assertThat(out).isEqualTo("Bill #7\n");
    }
}
