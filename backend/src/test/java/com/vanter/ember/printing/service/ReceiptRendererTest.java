package com.vanter.ember.printing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReceiptRendererTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID TABLE = UUID.randomUUID();
    private static final String SESSION_ID = "session-1";
    private static final LocalDateTime CREATED = LocalDateTime.of(2026, 9, 20, 17, 9);

    private BillRepository bills;
    private SessionRepository sessions;
    private DiningTableRepository tables;
    private RestaurantRepository restaurants;
    private ReceiptRenderer renderer;
    private SettingsPayload settings;

    @BeforeEach
    void setUp() {
        bills = mock(BillRepository.class);
        sessions = mock(SessionRepository.class);
        tables = mock(DiningTableRepository.class);
        restaurants = mock(RestaurantRepository.class);
        renderer = new ReceiptRenderer(bills, sessions, tables, restaurants);

        settings = new SettingsPayload();
        settings.getTicket().setHeaderMessage("EMBER");
        settings.getTicket().setFooterMessage("Gracias por visitarnos");
        settings.getTicket().setPaperWidth(SettingsPayload.PaperWidth.MM_80);
        settings.getBilling().setCurrencySymbol("C$");
        settings.getBilling().setTaxRate(15.0);
    }

    private static OrderItem item(String name, String price, OrderItemStatus status, String... modifiers) {
        return OrderItem.builder()
                .id(UUID.randomUUID().toString()).name(name).price(new BigDecimal(price)).status(status)
                .modifiers(java.util.Arrays.stream(modifiers)
                        .map(m -> SelectedModifier.builder().optionName(m).build()).toList())
                .build();
    }

    private void givenBill(String total, OrderItem... items) {
        Bill bill = Bill.builder().id(12L).tenantId(TENANT).sessionId(SESSION_ID)
                .total(new BigDecimal(total)).createdAt(CREATED).build();
        when(bills.findById(12L)).thenReturn(Optional.of(bill));
        Session session = Session.builder().id(SESSION_ID).tableId(TABLE).items(List.of(items)).build();
        when(sessions.findById(SESSION_ID)).thenReturn(Optional.of(session));
        when(tables.findById(TABLE)).thenReturn(Optional.of(DiningTables.builder().tableNumber(5).build()));
    }

    @Test
    void render_printsTableItemsSubtotalTaxAndTotal_fromTheRealBill() {
        givenBill("57.50",
                item("Hamburguesa", "25.00", OrderItemStatus.DELIVERED, "Extra queso"),
                item("Hamburguesa", "25.00", OrderItemStatus.DELIVERED, "Extra queso"),
                item("Coca Cola", "0.00", OrderItemStatus.READY));
        // subtotal 50.00 + 15% = 57.50

        String out = renderer.render(12L, settings);

        assertThat(out).contains("EMBER").contains("Mesa 5").contains("Cuenta #12").contains("20/09/26 17:09");
        assertThat(out).contains("2 Hamburguesa").contains("C$50.00").contains("  + Extra queso");
        assertThat(out).contains("Subtotal").contains("C$50.00");
        assertThat(out).contains("Impuesto (15%)").contains("C$7.50");
        assertThat(out).contains("TOTAL").contains("C$57.50");
        assertThat(out).contains("Gracias por visitarnos");
    }

    @Test
    void render_identicalUnitsAreGroupedIntoOneLine_withTheirLineTotal() {
        givenBill("60.00",
                item("Cerveza", "20.00", OrderItemStatus.DELIVERED),
                item("Cerveza", "20.00", OrderItemStatus.DELIVERED),
                item("Cerveza", "20.00", OrderItemStatus.DELIVERED));
        settings.getBilling().setTaxRate(0.0);

        String out = renderer.render(12L, settings);

        assertThat(out).contains("3 Cerveza").contains("C$60.00");
        assertThat(out).doesNotContain("1 Cerveza");
    }

    @Test
    void render_onlyBillsWhatTheBillingCounts_notDraftsOrPending() {
        givenBill("10.00",
                item("Pizza", "10.00", OrderItemStatus.DELIVERED),
                item("Postre", "5.00", OrderItemStatus.PENDING),
                item("Cafe", "2.00", OrderItemStatus.DRAFT));
        settings.getBilling().setTaxRate(0.0);

        String out = renderer.render(12L, settings);

        assertThat(out).contains("Pizza").doesNotContain("Postre").doesNotContain("Cafe");
    }

    @Test
    void render_printsTheBusinessHoursUnderTheHeader() {
        givenBill("10.00", item("Pizza", "10.00", OrderItemStatus.DELIVERED));
        settings.getBilling().setTaxRate(0.0);
        settings.getBranding().setOpeningTime("12:00");
        settings.getBranding().setClosingTime("23:00");

        String out = renderer.render(12L, settings);

        assertThat(out).contains("Horario: 12:00 - 23:00");
        assertThat(out.indexOf("EMBER")).isLessThan(out.indexOf("Horario:"));
        assertThat(out.indexOf("Horario:")).isLessThan(out.indexOf("Mesa 5"));
    }

    @Test
    void render_omitsTheHoursWhenTheyAreSwitchedOff() {
        givenBill("10.00", item("Pizza", "10.00", OrderItemStatus.DELIVERED));
        settings.getBilling().setTaxRate(0.0);
        settings.getBranding().setOpeningTime("12:00");
        settings.getBranding().setClosingTime("23:00");
        settings.getTicket().setShowBusinessHours(false);

        assertThat(renderer.render(12L, settings)).doesNotContain("Horario");
    }

    @Test
    void render_hidesTheTaxBreakdown_whenSettingsSayNo() {
        givenBill("57.50", item("Hamburguesa", "50.00", OrderItemStatus.DELIVERED));
        settings.getTicket().setShowTaxBreakdown(false);

        String out = renderer.render(12L, settings);

        assertThat(out).doesNotContain("Subtotal").doesNotContain("Impuesto");
        assertThat(out).contains("TOTAL").contains("C$57.50");
    }

    @Test
    void render_58mmPaper_usesTheNarrowWidth() {
        givenBill("57.50", item("Hamburguesa", "50.00", OrderItemStatus.DELIVERED));
        settings.getTicket().setPaperWidth(SettingsPayload.PaperWidth.MM_58);

        for (String line : renderer.render(12L, settings).split("\n")) {
            assertThat(line.length()).as(line).isLessThanOrEqualTo(32);
        }
    }

    @Test
    void render_blankHeader_fallsBackToTheRestaurantName() {
        givenBill("57.50", item("Hamburguesa", "50.00", OrderItemStatus.DELIVERED));
        settings.getTicket().setHeaderMessage(" ");
        when(restaurants.findById(TENANT)).thenReturn(Optional.of(Restaurant.builder().name("La Brasa").build()));

        assertThat(renderer.render(12L, settings)).contains("La Brasa");
    }

    @Test
    void render_billOrSessionMissing_stillPrintsSomethingUseful_insteadOfFailing() {
        when(bills.findById(99L)).thenReturn(Optional.empty());

        String out = renderer.render(99L, settings);

        assertThat(out).contains("EMBER").contains("Cuenta #99").contains("Gracias por visitarnos");
    }

    @Test
    void render_sessionGone_keepsTheTotalButHasNoItems() {
        Bill bill = Bill.builder().id(12L).tenantId(TENANT).sessionId("gone")
                .total(new BigDecimal("57.50")).createdAt(CREATED).build();
        when(bills.findById(12L)).thenReturn(Optional.of(bill));
        when(sessions.findById("gone")).thenReturn(Optional.empty());

        String out = renderer.render(12L, settings);

        assertThat(out).contains("TOTAL").contains("C$57.50").doesNotContain("Mesa");
    }
}
