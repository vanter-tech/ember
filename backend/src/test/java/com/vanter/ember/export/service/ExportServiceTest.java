package com.vanter.ember.export.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vanter.ember.analytics.dto.AnalyticsProductsResponse;
import com.vanter.ember.analytics.dto.ProductPerformance;
import com.vanter.ember.analytics.service.AnalyticsService;
import com.vanter.ember.billing.model.Bill;
import com.vanter.ember.billing.model.BillStatus;
import com.vanter.ember.billing.model.Payment;
import com.vanter.ember.billing.model.PaymentMethod;
import com.vanter.ember.billing.model.PaymentStatus;
import com.vanter.ember.billing.model.SplitMethod;
import com.vanter.ember.billing.repository.BillRepository;
import com.vanter.ember.billing.repository.PaymentRepository;
import com.vanter.ember.session.model.Session;
import com.vanter.ember.session.model.SessionStatus;
import com.vanter.ember.session.repository.SessionRepository;
import com.vanter.ember.settings.model.DiningTables;
import com.vanter.ember.settings.repository.DiningTableRepository;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExportServiceTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final LocalDateTime FROM = LocalDateTime.of(2026, 8, 1, 0, 0);
    private static final LocalDateTime TO = LocalDateTime.of(2026, 8, 14, 23, 59, 59);

    @Mock BillRepository billRepository;
    @Mock PaymentRepository paymentRepository;
    @Mock SessionRepository sessionRepository;
    @Mock DiningTableRepository diningTableRepository;
    @Mock AnalyticsService analyticsService;

    @InjectMocks ExportService exportService;

    private static Map<String, String> unzip(byte[] zipBytes) throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                entries.put(entry.getName(), new String(zis.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return entries;
    }

    private static AnalyticsProductsResponse emptyProducts() {
        return new AnalyticsProductsResponse(FROM, TO, BigDecimal.ZERO, 0L, 0, List.of(), List.of());
    }

    private void stubEmptyBillsAndProducts() {
        when(billRepository.findByTenantIdAndCreatedAtBetweenAndStatusIn(eq(TENANT_ID), any(), any(), any()))
                .thenReturn(List.of());
        when(paymentRepository.findByBillIdIn(any())).thenReturn(List.of());
        when(sessionRepository.findByTenantIdAndIdIn(eq(TENANT_ID), any())).thenReturn(List.of());
        when(analyticsService.getProducts(eq(TENANT_ID), any(), any(), eq(null))).thenReturn(emptyProducts());
    }

    @Test
    void buildTenantExportZip_containsBothCsvFilesEvenWhenEmpty() throws IOException {
        stubEmptyBillsAndProducts();

        byte[] zip = exportService.buildTenantExportZip(TENANT_ID, FROM, TO);

        Map<String, String> entries = unzip(zip);
        assertThat(entries).containsKeys("ventas.csv", "productos.csv");
        assertThat(entries.get("ventas.csv"))
                .isEqualTo("bill_id,mesa,fecha,total,estado,metodos_pago,participantes\r\n");
        assertThat(entries.get("productos.csv"))
                .isEqualTo("nombre,categoria,unidades_vendidas,ingresos,porcentaje_ingresos\r\n");
    }

    @Test
    void buildTenantExportZip_ventasCsv_oneRowPerBillWithTableAndDistinctConfirmedPaymentMethods()
            throws IOException {
        Bill bill = Bill.builder()
                .id(1L).sessionId("sess-1").total(new BigDecimal("50.00"))
                .splitMethod(SplitMethod.EQUAL_PARTS).status(BillStatus.PAID)
                .createdAt(LocalDateTime.of(2026, 8, 5, 20, 0)).build();
        when(billRepository.findByTenantIdAndCreatedAtBetweenAndStatusIn(eq(TENANT_ID), eq(FROM), eq(TO), any()))
                .thenReturn(List.of(bill));

        Payment confirmedCash = Payment.builder()
                .bill(bill).participantName("Ana").amount(new BigDecimal("25.00"))
                .method(PaymentMethod.PHYSICAL).status(PaymentStatus.CONFIRMED)
                .createdAt(LocalDateTime.of(2026, 8, 5, 20, 5)).build();
        Payment confirmedDigital = Payment.builder()
                .bill(bill).participantName("Beto").amount(new BigDecimal("25.00"))
                .method(PaymentMethod.DIGITAL).status(PaymentStatus.CONFIRMED)
                .createdAt(LocalDateTime.of(2026, 8, 5, 20, 6)).build();
        Payment pendingDigital = Payment.builder()
                .bill(bill).participantName("Cara").amount(new BigDecimal("10.00"))
                .method(PaymentMethod.DIGITAL).status(PaymentStatus.PENDING)
                .createdAt(LocalDateTime.of(2026, 8, 5, 20, 7)).build();
        when(paymentRepository.findByBillIdIn(List.of(1L)))
                .thenReturn(List.of(confirmedCash, confirmedDigital, pendingDigital));

        UUID tableId = UUID.randomUUID();
        Session session = Session.builder()
                .id("sess-1").tenantId(TENANT_ID).tableId(tableId).status(SessionStatus.CLOSED)
                .maxParticipants(4).createdAt(LocalDateTime.of(2026, 8, 5, 19, 30)).build();
        when(sessionRepository.findByTenantIdAndIdIn(TENANT_ID, List.of("sess-1"))).thenReturn(List.of(session));

        DiningTables table = DiningTables.builder().id(tableId).restaurantId(TENANT_ID).tableNumber(7).build();
        when(diningTableRepository.findByRestaurantIdAndIdIn(TENANT_ID, List.of(tableId)))
                .thenReturn(List.of(table));

        when(analyticsService.getProducts(TENANT_ID, FROM, TO, null)).thenReturn(emptyProducts());

        byte[] zip = exportService.buildTenantExportZip(TENANT_ID, FROM, TO);

        String[] lines = unzip(zip).get("ventas.csv").split("\r\n");
        assertThat(lines[0]).isEqualTo("bill_id,mesa,fecha,total,estado,metodos_pago,participantes");
        // Only the two CONFIRMED payments count for methods/participants; PENDING is excluded.
        assertThat(lines[1]).isEqualTo("1,7,2026-08-05T20:00,50.00,PAID,DIGITAL/PHYSICAL,2");
    }

    @Test
    void buildTenantExportZip_ventasCsv_billWithNoConfirmedPaymentHasEmptyMethodsAndZeroParticipants()
            throws IOException {
        Bill voidedBill = Bill.builder()
                .id(2L).sessionId("sess-2").total(new BigDecimal("15.00"))
                .splitMethod(SplitMethod.EQUAL_PARTS).status(BillStatus.VOIDED)
                .createdAt(LocalDateTime.of(2026, 8, 6, 13, 0)).build();
        when(billRepository.findByTenantIdAndCreatedAtBetweenAndStatusIn(eq(TENANT_ID), eq(FROM), eq(TO), any()))
                .thenReturn(List.of(voidedBill));
        when(paymentRepository.findByBillIdIn(List.of(2L))).thenReturn(List.of());
        when(sessionRepository.findByTenantIdAndIdIn(TENANT_ID, List.of("sess-2"))).thenReturn(List.of());
        when(analyticsService.getProducts(TENANT_ID, FROM, TO, null)).thenReturn(emptyProducts());

        byte[] zip = exportService.buildTenantExportZip(TENANT_ID, FROM, TO);

        String[] lines = unzip(zip).get("ventas.csv").split("\r\n");
        assertThat(lines[1]).isEqualTo("2,,2026-08-06T13:00,15.00,VOIDED,,0");
    }

    @Test
    void buildTenantExportZip_productosCsv_oneRowPerProductPerformance() throws IOException {
        stubEmptyBillsAndProducts();
        when(analyticsService.getProducts(TENANT_ID, FROM, TO, null)).thenReturn(new AnalyticsProductsResponse(
                FROM, TO, new BigDecimal("100.00"), 5L, 1,
                List.of(new ProductPerformance(
                        4L, "Lomo saltado", 2L, "Fondos", 5L,
                        new BigDecimal("100.00"), new BigDecimal("100.00"), new BigDecimal("100.00"))),
                List.of()));

        byte[] zip = exportService.buildTenantExportZip(TENANT_ID, FROM, TO);

        String[] lines = unzip(zip).get("productos.csv").split("\r\n");
        assertThat(lines[1]).isEqualTo("Lomo saltado,Fondos,5,100.00,100.00");
    }

    @Test
    void buildTenantExportZip_missingBoundsDefaultToTheWholeHistoryUpToNow() {
        stubEmptyBillsAndProducts();
        LocalDateTime beforeCall = LocalDateTime.now();

        exportService.buildTenantExportZip(TENANT_ID, null, null);

        ArgumentCaptor<LocalDateTime> from = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> to = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(billRepository).findByTenantIdAndCreatedAtBetweenAndStatusIn(
                eq(TENANT_ID), from.capture(), to.capture(), any());

        assertThat(from.getValue()).isEqualTo(ExportService.EPOCH_FLOOR);
        assertThat(to.getValue()).isAfterOrEqualTo(beforeCall);
    }

    @Test
    void buildTenantExportZip_invertedWindowThrows() {
        assertThatThrownBy(() -> exportService.buildTenantExportZip(TENANT_ID, TO, FROM))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
