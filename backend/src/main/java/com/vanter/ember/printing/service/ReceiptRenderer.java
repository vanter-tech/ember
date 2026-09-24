package com.vanter.ember.printing.service;

import com.vanter.ember.billing.model.Bill;
import com.vanter.ember.billing.repository.BillRepository;
import com.vanter.ember.restaurant.model.Restaurant;
import com.vanter.ember.restaurant.repository.RestaurantRepository;
import com.vanter.ember.session.model.OrderItem;
import com.vanter.ember.session.model.OrderItemStatus;
import com.vanter.ember.session.model.SelectedModifier;
import com.vanter.ember.session.model.Session;
import com.vanter.ember.session.repository.SessionRepository;
import com.vanter.ember.settings.model.DiningTables;
import com.vanter.ember.settings.model.SettingsPayload;
import com.vanter.ember.settings.repository.DiningTableRepository;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Builds the plain-text {@code BILL_RECEIPT} payload: table, date, every billed item with its
 * modifiers, subtotal / tax / total and the configured header and footer, laid out for the
 * paper width in Settings. Used by both the automatic {@code PaymentCompleted} path and the
 * on-demand reprint endpoint, so the two copies are identical (spec §3.3).
 *
 * <p>Items are the ones {@code BillingService} bills (delivered or ready) and the total is the
 * bill's own, so the receipt always agrees with what was charged; tax is what is left between
 * them. Printing must never fail because a piece of context is gone: a missing bill or session
 * degrades to a shorter receipt instead of an error.
 */
@Component
@RequiredArgsConstructor
public class ReceiptRenderer {

    private static final int WIDTH_58MM = 32;
    private static final int WIDTH_80MM = 42;

    private final BillRepository billRepository;
    private final SessionRepository sessionRepository;
    private final DiningTableRepository diningTableRepository;
    private final RestaurantRepository restaurantRepository;

    public String render(Long billId, SettingsPayload settings) {
        SettingsPayload.TicketSettings ticket = settings.getTicket();
        ReceiptLayout.Data.Builder data = ReceiptLayout.Data.builder()
                .billId(billId)
                .width(ticket.getPaperWidth() == SettingsPayload.PaperWidth.MM_58 ? WIDTH_58MM : WIDTH_80MM)
                .header(ticket.getHeaderMessage())
                .footer(ticket.getFooterMessage())
                .currency(settings.getBilling().getCurrencySymbol());
        ReceiptBusinessInfo.lines(settings).forEach(data::infoLine);

        Optional<Bill> found = billRepository.findById(billId);
        if (found.isPresent()) {
            Bill bill = found.get();
            data.when(bill.getCreatedAt()).total(bill.getTotal());
            if (ticket.getHeaderMessage() == null || ticket.getHeaderMessage().isBlank()) {
                restaurantRepository.findById(bill.getTenantId())
                        .map(Restaurant::getName)
                        .ifPresent(data::header);
            }
            sessionRepository.findById(bill.getSessionId()).ifPresent(session -> addSession(data, session, bill, settings));
        }
        return ReceiptLayout.render(data.build());
    }

    private void addSession(ReceiptLayout.Data.Builder data, Session session, Bill bill, SettingsPayload settings) {
        if (session.getTableId() != null) {
            diningTableRepository.findById(session.getTableId())
                    .map(DiningTables::getTableNumber)
                    .ifPresent(data::tableNumber);
        }

        List<OrderItem> billed = session.getItems().stream()
                .filter(i -> i.getStatus() == OrderItemStatus.DELIVERED || i.getStatus() == OrderItemStatus.READY)
                .toList();
        if (billed.isEmpty()) {
            return;
        }

        Map<String, GroupedItem> grouped = new LinkedHashMap<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        for (OrderItem item : billed) {
            BigDecimal price = item.getPrice() == null ? BigDecimal.ZERO : item.getPrice();
            subtotal = subtotal.add(price);
            List<String> modifiers = item.getModifiers() == null ? List.of() : item.getModifiers().stream()
                    .map(SelectedModifier::getOptionName).toList();
            grouped.computeIfAbsent(item.getName() + "|" + price + "|" + modifiers,
                    k -> new GroupedItem(item.getName(), modifiers, price)).units++;
        }
        grouped.values().forEach(g -> data.line(
                new ReceiptLayout.Line(g.units, g.name, g.modifiers, g.unitPrice.multiply(BigDecimal.valueOf(g.units)))));

        BigDecimal tax = bill.getTotal().subtract(subtotal);
        if (settings.getTicket().isShowTaxBreakdown() && tax.signum() > 0) {
            data.subtotal(subtotal).tax(tax).taxLabel(taxLabel(settings.getBilling().getTaxRate()));
        }
    }

    private static String taxLabel(Double rate) {
        if (rate == null || rate <= 0) {
            return "Impuesto";
        }
        return "Impuesto (" + BigDecimal.valueOf(rate).stripTrailingZeros().toPlainString() + "%)";
    }

    private static final class GroupedItem {
        final String name;
        final List<String> modifiers;
        final BigDecimal unitPrice;
        int units;

        GroupedItem(String name, List<String> modifiers, BigDecimal unitPrice) {
            this.name = name;
            this.modifiers = modifiers;
            this.unitPrice = unitPrice;
        }
    }
}
