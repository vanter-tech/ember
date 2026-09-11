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
import com.vanter.ember.settings.model.RestaurantSettings;
import com.vanter.ember.settings.model.SettingsPayload;
import com.vanter.ember.settings.repository.DiningTableRepository;
import com.vanter.ember.settings.service.SettingService;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
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
    @Mock SettingService settingService;

    @InjectMocks ExportService exportService;

    private static Workbook readWorkbook(byte[] bytes) throws IOException {
        return new XSSFWorkbook(new ByteArrayInputStream(bytes));
    }

    private static String text(Row row, int col) {
        Cell cell = row.getCell(col);
        return cell == null ? null : cell.getStringCellValue();
    }

    private static double numeric(Row row, int col) {
        return row.getCell(col).getNumericCellValue();
    }

    private static AnalyticsProductsResponse emptyProducts() {
        return new AnalyticsProductsResponse(FROM, TO, BigDecimal.ZERO, 0L, 0, List.of(), List.of());
    }

    private static RestaurantSettings sampleSettings() {
        SettingsPayload.BrandingSettings branding = new SettingsPayload.BrandingSettings();
        branding.setBusinessName("Ember Demo");
        branding.setLegalName("Ember Gastronomía S.A. de C.V.");
        branding.setRuc("800-123456-7");
        branding.setPhone("+52 55 1234 5678");
        branding.setAddress("123 Culinary Ave");

        SettingsPayload payload = new SettingsPayload();
        payload.setBranding(branding);

        RestaurantSettings settings = new RestaurantSettings();
        settings.setPayload(payload);
        return settings;
    }

    /** Asserts the shared business-info block (rows 0-5) any sheet starts with. */
    private static void assertHasBusinessHeaderBlock(Sheet sheet) {
        assertThat(text(sheet.getRow(0), 0)).isEqualTo("Negocio");
        assertThat(text(sheet.getRow(0), 1)).isEqualTo("Ember Demo");
        assertThat(text(sheet.getRow(1), 0)).isEqualTo("Nombre legal");
        assertThat(text(sheet.getRow(1), 1)).isEqualTo("Ember Gastronomía S.A. de C.V.");
        assertThat(text(sheet.getRow(2), 0)).isEqualTo("RUC");
        assertThat(text(sheet.getRow(2), 1)).isEqualTo("800-123456-7");
        assertThat(text(sheet.getRow(3), 0)).isEqualTo("Teléfono");
        assertThat(text(sheet.getRow(3), 1)).isEqualTo("+52 55 1234 5678");
        assertThat(text(sheet.getRow(4), 0)).isEqualTo("Dirección");
        assertThat(text(sheet.getRow(4), 1)).isEqualTo("123 Culinary Ave");
        assertThat(text(sheet.getRow(5), 0)).isEqualTo("Rango exportado");
        assertThat(text(sheet.getRow(5), 1)).isEqualTo("2026-08-01T00:00 a 2026-08-14T23:59:59");
        assertThat(sheet.getRow(6)).isNull();
    }

    private void stubEmptyBillsAndProducts() {
        when(billRepository.findByTenantIdAndCreatedAtBetweenAndStatusIn(eq(TENANT_ID), any(), any(), any()))
                .thenReturn(List.of());
        when(paymentRepository.findByBillIdIn(any())).thenReturn(List.of());
        when(sessionRepository.findByTenantIdAndIdIn(eq(TENANT_ID), any())).thenReturn(List.of());
        when(analyticsService.getProducts(eq(TENANT_ID), any(), any(), eq(null))).thenReturn(emptyProducts());
        when(settingService.getSettings(TENANT_ID)).thenReturn(sampleSettings());
    }

    @Test
    void buildTenantExportWorkbook_hasBothSheetsWithTheBusinessHeaderAndColumnTitles() throws IOException {
        stubEmptyBillsAndProducts();

        Workbook workbook = readWorkbook(exportService.buildTenantExportWorkbook(TENANT_ID, FROM, TO));

        Sheet ventas = workbook.getSheet("Ventas");
        assertThat(ventas).isNotNull();
        assertHasBusinessHeaderBlock(ventas);
        Row ventasColumnHeader = ventas.getRow(7);
        assertThat(text(ventasColumnHeader, 0)).isEqualTo("ID Cuenta");
        assertThat(text(ventasColumnHeader, 1)).isEqualTo("Mesa");
        assertThat(text(ventasColumnHeader, 2)).isEqualTo("Fecha");
        assertThat(text(ventasColumnHeader, 3)).isEqualTo("Total");
        assertThat(text(ventasColumnHeader, 4)).isEqualTo("Estado");
        assertThat(text(ventasColumnHeader, 5)).isEqualTo("Métodos de pago");
        assertThat(text(ventasColumnHeader, 6)).isEqualTo("Participantes");
        assertThat(ventas.getRow(8)).isNull();

        Sheet productos = workbook.getSheet("Productos");
        assertThat(productos).isNotNull();
        assertHasBusinessHeaderBlock(productos);
        Row productosColumnHeader = productos.getRow(7);
        assertThat(text(productosColumnHeader, 0)).isEqualTo("Producto");
        assertThat(text(productosColumnHeader, 1)).isEqualTo("Categoría");
        assertThat(text(productosColumnHeader, 2)).isEqualTo("Unidades vendidas");
        assertThat(text(productosColumnHeader, 3)).isEqualTo("Ingresos");
        assertThat(text(productosColumnHeader, 4)).isEqualTo("% Ingresos");
        assertThat(productos.getRow(8)).isNull();
    }

    @Test
    void buildTenantExportWorkbook_columnHeaderRowIsBold() throws IOException {
        stubEmptyBillsAndProducts();

        Workbook workbook = readWorkbook(exportService.buildTenantExportWorkbook(TENANT_ID, FROM, TO));

        Cell headerCell = workbook.getSheet("Ventas").getRow(7).getCell(0);
        Font font = workbook.getFontAt(headerCell.getCellStyle().getFontIndexAsInt());
        assertThat(font.getBold()).isTrue();
    }

    @Test
    void buildTenantExportWorkbook_ventasSheet_oneRowPerBillWithTableAndDistinctConfirmedPaymentMethods()
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
        when(settingService.getSettings(TENANT_ID)).thenReturn(sampleSettings());

        Workbook workbook = readWorkbook(exportService.buildTenantExportWorkbook(TENANT_ID, FROM, TO));

        Row dataRow = workbook.getSheet("Ventas").getRow(8);
        assertThat(numeric(dataRow, 0)).isEqualTo(1.0);
        assertThat(numeric(dataRow, 1)).isEqualTo(7.0);
        assertThat(dataRow.getCell(2).getLocalDateTimeCellValue()).isEqualTo(LocalDateTime.of(2026, 8, 5, 20, 0));
        assertThat(numeric(dataRow, 3)).isEqualTo(50.00);
        assertThat(text(dataRow, 4)).isEqualTo("PAID");
        // Only the two CONFIRMED payments count for methods/participants; PENDING is excluded.
        assertThat(text(dataRow, 5)).isEqualTo("DIGITAL/PHYSICAL");
        assertThat(numeric(dataRow, 6)).isEqualTo(2.0);
    }

    @Test
    void buildTenantExportWorkbook_ventasSheet_billWithNoTableAndNoConfirmedPaymentLeavesThoseCellsBlank()
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
        when(settingService.getSettings(TENANT_ID)).thenReturn(sampleSettings());

        Workbook workbook = readWorkbook(exportService.buildTenantExportWorkbook(TENANT_ID, FROM, TO));

        Row dataRow = workbook.getSheet("Ventas").getRow(8);
        assertThat(numeric(dataRow, 0)).isEqualTo(2.0);
        assertThat(dataRow.getCell(1)).isNull();
        assertThat(text(dataRow, 4)).isEqualTo("VOIDED");
        assertThat(text(dataRow, 5)).isEqualTo("");
        assertThat(numeric(dataRow, 6)).isEqualTo(0.0);
    }

    @Test
    void buildTenantExportWorkbook_productosSheet_oneRowPerProductPerformance() throws IOException {
        stubEmptyBillsAndProducts();
        when(analyticsService.getProducts(TENANT_ID, FROM, TO, null)).thenReturn(new AnalyticsProductsResponse(
                FROM, TO, new BigDecimal("100.00"), 5L, 1,
                List.of(new ProductPerformance(
                        4L, "Lomo saltado", 2L, "Fondos", 5L,
                        new BigDecimal("100.00"), new BigDecimal("100.00"), new BigDecimal("100.00"))),
                List.of()));

        Workbook workbook = readWorkbook(exportService.buildTenantExportWorkbook(TENANT_ID, FROM, TO));

        Row dataRow = workbook.getSheet("Productos").getRow(8);
        assertThat(text(dataRow, 0)).isEqualTo("Lomo saltado");
        assertThat(text(dataRow, 1)).isEqualTo("Fondos");
        assertThat(numeric(dataRow, 2)).isEqualTo(5.0);
        assertThat(numeric(dataRow, 3)).isEqualTo(100.00);
        assertThat(numeric(dataRow, 4)).isEqualTo(100.00);
    }

    @Test
    void buildTenantExportWorkbook_missingBoundsDefaultToTheWholeHistoryUpToNow() {
        stubEmptyBillsAndProducts();
        LocalDateTime beforeCall = LocalDateTime.now();

        exportService.buildTenantExportWorkbook(TENANT_ID, null, null);

        ArgumentCaptor<LocalDateTime> from = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> to = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(billRepository).findByTenantIdAndCreatedAtBetweenAndStatusIn(
                eq(TENANT_ID), from.capture(), to.capture(), any());

        assertThat(from.getValue()).isEqualTo(ExportService.EPOCH_FLOOR);
        assertThat(to.getValue()).isAfterOrEqualTo(beforeCall);
    }

    @Test
    void buildTenantExportWorkbook_invertedWindowThrows() {
        assertThatThrownBy(() -> exportService.buildTenantExportWorkbook(TENANT_ID, TO, FROM))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
