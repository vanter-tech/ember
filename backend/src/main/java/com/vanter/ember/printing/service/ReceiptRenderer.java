package com.vanter.ember.printing.service;

import com.vanter.ember.settings.model.SettingsPayload;
import org.springframework.stereotype.Component;

/**
 * Builds the plain-text {@code BILL_RECEIPT} payload. Extracted from {@code PrintingEventListener}
 * so both the automatic {@code PaymentCompleted} path and the on-demand reprint endpoint render
 * identically (spec §3.3). No behavior change from the listener's former private renderer.
 */
@Component
public class ReceiptRenderer {

    public String render(Long billId, SettingsPayload settings) {
        StringBuilder sb = new StringBuilder();
        String header = settings.getTicket().getHeaderMessage();
        if (header != null && !header.isBlank()) {
            sb.append(header).append('\n');
        }
        sb.append("Bill #").append(billId).append('\n');
        String footer = settings.getTicket().getFooterMessage();
        if (footer != null && !footer.isBlank()) {
            sb.append(footer).append('\n');
        }
        return sb.toString();
    }
}
