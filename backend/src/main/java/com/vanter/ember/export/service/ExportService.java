package com.vanter.ember.export.service;

import com.vanter.ember.analytics.dto.AnalyticsProductsResponse;
import com.vanter.ember.analytics.dto.ProductPerformance;
import com.vanter.ember.analytics.service.AnalyticsService;
import com.vanter.ember.billing.model.Bill;
import com.vanter.ember.billing.model.BillStatus;
import com.vanter.ember.billing.model.Payment;
import com.vanter.ember.billing.model.PaymentStatus;
import com.vanter.ember.billing.repository.BillRepository;
import com.vanter.ember.billing.repository.PaymentRepository;
import com.vanter.ember.session.model.Session;
import com.vanter.ember.session.repository.SessionRepository;
import com.vanter.ember.settings.model.DiningTables;
import com.vanter.ember.settings.model.SettingsPayload;
import com.vanter.ember.settings.repository.DiningTableRepository;
import com.vanter.ember.settings.service.SettingService;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds the tenant business-data export: a single {@code .xlsx} workbook with a "Ventas" sheet
 * (one row per {@code PAID}/{@code VOIDED} bill) and a "Productos" sheet (delegates entirely to
 * {@link AnalyticsService#getProducts}, the same computation the admin dashboard's product-
 * performance view already uses, just with no top-N {@code limit}). A real spreadsheet — not a
 * CSV — so numbers stay numbers (summable/sortable in Excel), dates stay dates, and the column
 * header row can actually be bold and colored.
 */
@Service
@RequiredArgsConstructor
public class ExportService {

    /** Same sentinel {@link AnalyticsService} uses for "the tenant's whole history". */
    static final LocalDateTime EPOCH_FLOOR = LocalDateTime.of(1970, 1, 1, 0, 0);

    private static final byte[] BRAND_RED = {(byte) 0x8c, (byte) 0x17, (byte) 0x17};

    private final BillRepository billRepository;
    private final PaymentRepository paymentRepository;
    private final SessionRepository sessionRepository;
    private final DiningTableRepository diningTableRepository;
    private final AnalyticsService analyticsService;
    private final SettingService settingService;

    @Transactional(readOnly = true)
    public byte[] buildTenantExportWorkbook(UUID tenantId, LocalDateTime from, LocalDateTime to) {
        LocalDateTime windowStart = from == null ? EPOCH_FLOOR : from;
        LocalDateTime windowEnd = to == null ? LocalDateTime.now() : to;
        if (windowStart.isAfter(windowEnd)) {
            throw new IllegalArgumentException("Export range 'from' must not be after 'to'");
        }

        SettingsPayload.BrandingSettings branding = settingService.getSettings(tenantId).getPayload().getBranding();

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            CellStyle labelStyle = boldStyle(workbook, null);
            CellStyle columnHeaderStyle = boldStyle(workbook, BRAND_RED);
            CellStyle dateStyle = dateStyle(workbook);
            CellStyle moneyStyle = numberStyle(workbook, "#,##0.00");
            CellStyle countStyle = numberStyle(workbook, "#,##0");

            writeVentasSheet(workbook, tenantId, windowStart, windowEnd, branding,
                    labelStyle, columnHeaderStyle, dateStyle, moneyStyle, countStyle);
            writeProductosSheet(workbook, tenantId, windowStart, windowEnd, branding,
                    labelStyle, columnHeaderStyle, moneyStyle, countStyle);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void writeVentasSheet(
            XSSFWorkbook workbook,
            UUID tenantId,
            LocalDateTime from,
            LocalDateTime to,
            SettingsPayload.BrandingSettings branding,
            CellStyle labelStyle,
            CellStyle columnHeaderStyle,
            CellStyle dateStyle,
            CellStyle moneyStyle,
            CellStyle countStyle) {
        Sheet sheet = workbook.createSheet("Ventas");
        int rowIndex = writeBusinessHeaderBlock(sheet, branding, from, to, labelStyle);

        writeColumnHeaderRow(sheet, rowIndex, columnHeaderStyle,
                "ID Cuenta", "Mesa", "Fecha", "Total", "Estado", "Métodos de pago", "Participantes");
        rowIndex++;

        List<Bill> bills = billRepository.findByTenantIdAndCreatedAtBetweenAndStatusIn(
                tenantId, from, to, List.of(BillStatus.PAID, BillStatus.VOIDED));

        List<Long> billIds = bills.stream().map(Bill::getId).toList();
        Map<Long, List<Payment>> paymentsByBillId = paymentRepository.findByBillIdIn(billIds).stream()
                .collect(Collectors.groupingBy(p -> p.getBill().getId()));

        List<String> sessionIds = bills.stream().map(Bill::getSessionId).distinct().toList();
        Map<String, Session> sessionsById = sessionRepository.findByTenantIdAndIdIn(tenantId, sessionIds).stream()
                .collect(Collectors.toMap(Session::getId, s -> s));

        List<UUID> tableIds = sessionsById.values().stream()
                .map(Session::getTableId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<UUID, Integer> tableNumbersById =
                diningTableRepository.findByRestaurantIdAndIdIn(tenantId, tableIds).stream()
                        .collect(Collectors.toMap(DiningTables::getId, DiningTables::getTableNumber));

        for (Bill bill : bills) {
            Session session = sessionsById.get(bill.getSessionId());
            UUID tableId = session == null ? null : session.getTableId();
            Integer tableNumber = tableId == null ? null : tableNumbersById.get(tableId);

            List<Payment> confirmedPayments = paymentsByBillId.getOrDefault(bill.getId(), List.of()).stream()
                    .filter(p -> p.getStatus() == PaymentStatus.CONFIRMED)
                    .toList();
            String methods = confirmedPayments.stream()
                    .map(p -> p.getMethod().name())
                    .distinct()
                    .sorted()
                    .collect(Collectors.joining("/"));
            long participantCount = confirmedPayments.stream()
                    .map(Payment::getParticipantName)
                    .distinct()
                    .count();

            Row row = sheet.createRow(rowIndex++);
            numericCell(row, 0, bill.getId(), countStyle);
            if (tableNumber != null) {
                numericCell(row, 1, tableNumber, countStyle);
            }
            Cell dateCell = row.createCell(2);
            dateCell.setCellValue(bill.getCreatedAt());
            dateCell.setCellStyle(dateStyle);
            numericCell(row, 3, bill.getTotal().doubleValue(), moneyStyle);
            row.createCell(4).setCellValue(bill.getStatus().name());
            row.createCell(5).setCellValue(methods);
            numericCell(row, 6, participantCount, countStyle);
        }

        for (int col = 0; col <= 6; col++) {
            sheet.autoSizeColumn(col);
        }
    }

    private void writeProductosSheet(
            XSSFWorkbook workbook,
            UUID tenantId,
            LocalDateTime from,
            LocalDateTime to,
            SettingsPayload.BrandingSettings branding,
            CellStyle labelStyle,
            CellStyle columnHeaderStyle,
            CellStyle moneyStyle,
            CellStyle countStyle) {
        Sheet sheet = workbook.createSheet("Productos");
        int rowIndex = writeBusinessHeaderBlock(sheet, branding, from, to, labelStyle);

        writeColumnHeaderRow(sheet, rowIndex, columnHeaderStyle,
                "Producto", "Categoría", "Unidades vendidas", "Ingresos", "% Ingresos");
        rowIndex++;

        AnalyticsProductsResponse products = analyticsService.getProducts(tenantId, from, to, null);
        for (ProductPerformance product : products.products()) {
            Row row = sheet.createRow(rowIndex++);
            row.createCell(0).setCellValue(product.name());
            row.createCell(1).setCellValue(product.categoryName() == null ? "" : product.categoryName());
            numericCell(row, 2, product.quantitySold(), countStyle);
            numericCell(row, 3, product.revenue().doubleValue(), moneyStyle);
            numericCell(row, 4, product.revenueShare().doubleValue(), moneyStyle);
        }

        for (int col = 0; col <= 4; col++) {
            sheet.autoSizeColumn(col);
        }
    }

    /**
     * A small label/value block identifying the business behind the report, at the top of every
     * sheet so it's self-describing on its own — pulled from the same Branding settings the admin
     * already fills in under Settings > Marca y negocio. Returns the next free row index (a blank
     * row after the block).
     */
    private int writeBusinessHeaderBlock(
            Sheet sheet, SettingsPayload.BrandingSettings branding, LocalDateTime from, LocalDateTime to,
            CellStyle labelStyle) {
        writeLabelRow(sheet, 0, "Negocio", blankToEmpty(branding.getBusinessName()), labelStyle);
        writeLabelRow(sheet, 1, "Nombre legal", blankToEmpty(branding.getLegalName()), labelStyle);
        writeLabelRow(sheet, 2, "RUC", blankToEmpty(branding.getRuc()), labelStyle);
        writeLabelRow(sheet, 3, "Teléfono", blankToEmpty(branding.getPhone()), labelStyle);
        writeLabelRow(sheet, 4, "Dirección", blankToEmpty(branding.getAddress()), labelStyle);
        writeLabelRow(sheet, 5, "Rango exportado", from + " a " + to, labelStyle);
        return 7;
    }

    private void writeLabelRow(Sheet sheet, int rowIndex, String label, String value, CellStyle labelStyle) {
        Row row = sheet.createRow(rowIndex);
        Cell labelCell = row.createCell(0);
        labelCell.setCellValue(label);
        labelCell.setCellStyle(labelStyle);
        row.createCell(1).setCellValue(value);
    }

    private void writeColumnHeaderRow(Sheet sheet, int rowIndex, CellStyle style, String... columns) {
        Row row = sheet.createRow(rowIndex);
        for (int col = 0; col < columns.length; col++) {
            Cell cell = row.createCell(col);
            cell.setCellValue(columns[col]);
            cell.setCellStyle(style);
        }
    }

    private void numericCell(Row row, int col, double value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private static String blankToEmpty(String value) {
        return value == null ? "" : value;
    }

    /** Bold label style; {@code fillRgb == null} leaves the background transparent (plain bold text). */
    private static CellStyle boldStyle(XSSFWorkbook workbook, byte[] fillRgb) {
        XSSFCellStyle style = workbook.createCellStyle();
        XSSFFont font = workbook.createFont();
        font.setBold(true);
        if (fillRgb != null) {
            font.setColor(new XSSFColor(new byte[] {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF}, null));
            style.setFillForegroundColor(new XSSFColor(fillRgb, null));
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        }
        style.setFont(font);
        return style;
    }

    private static CellStyle dateStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat("yyyy-mm-dd hh:mm"));
        return style;
    }

    private static CellStyle numberStyle(XSSFWorkbook workbook, String format) {
        CellStyle style = workbook.createCellStyle();
        style.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat(format));
        return style;
    }
}
